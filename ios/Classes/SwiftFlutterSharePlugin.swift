import Flutter
import UIKit
import Photos

enum ShareType {
    case text
    case image
    case file
}

public class SwiftFlutterSharePlugin: NSObject, FlutterPlugin, FlutterStreamHandler {
    static let kMessagesChannel = "plugins.flutter.io/share";
    static let kEventsChannelMedia = "receive_sharing_intent/events-media";
    static let kEventsChannelLink = "receive_sharing_intent/events-text";

    private var initialMedia: [SharedMediaFile]? = nil
    private var latestMedia: [SharedMediaFile]? = nil
    
    private var initialText: String? = nil
    private var latestText: String? = nil
    
    private var eventSinkMedia: FlutterEventSink? = nil;
    private var eventSinkText: FlutterEventSink? = nil;

    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = SwiftFlutterSharePlugin()
        
        let channel = FlutterMethodChannel(name: kMessagesChannel, binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(instance, channel: channel)
        
        let chargingChannelMedia = FlutterEventChannel(name: kEventsChannelMedia, binaryMessenger: registrar.messenger())
        chargingChannelMedia.setStreamHandler(instance)
        
        let chargingChannelLink = FlutterEventChannel(name: kEventsChannelLink, binaryMessenger: registrar.messenger())
        chargingChannelLink.setStreamHandler(instance)
        
        registrar.addApplicationDelegate(instance)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        if call.method == "getInitialMedia" {
            result(toJson(data: self.initialMedia));
        } else if call.method == "getInitialText" {
            result(self.initialText);
        } else if call.method == "reset" {
            self.initialMedia = nil
            self.latestMedia = nil
            self.initialText = nil
            self.latestText = nil
            result(nil);
        } else if call.method == "share" {
            print("[share (\(String(describing: call.arguments)))]")

            guard let args = call.arguments else {
                result(nil);
                return
            }

            if let myArgs = args as? [String: Any],
                let type = myArgs["type"] as? String {
                
                switch (type) {
                case "text/plain":
                    if let text = myArgs["text"] as? String {
                        self.shareText(text)
                    } else {
                        print("[share (\(type))] Text is not specified")
                    }
                    break
                case "image/*":
                    if let path = myArgs["path"] as? String {
                        self.shareImage(path)
                    } else {
                        print("[share (\(type))] Path is not specified")
                    }
                    break
                case "*/*":
                    if let path = myArgs["path"] as? String {
                        self.shareFile(path)
                    } else {
                        print("[share (\(type))] Path is not specified")
                    }
                    break
                default:
                    print("[share] Unknown type \"\(type)\"")
                }
            } else {
                print("[share] Type is not specified")
            }
            result(nil)
        } else {
            result(FlutterMethodNotImplemented)
        }
    }
    
    func shareText(_ text: String) -> Void {
        print("[shareText]: \(text)")
        
        if let navigationController = UIApplication.shared.keyWindow?.rootViewController  {
            var items: [String] = []
            
            items.append(text)
            
            let activityViewController = UIActivityViewController(activityItems: items, applicationActivities: nil)
            
            navigationController.present(activityViewController, animated: true, completion: nil)
        } else {
            print("[shareFile]: No navigation controller")
        }
    }
    
    func shareFile(_ path: String) -> Void {
        print("[shareFile]: \(path)")
        
        if let navigationController = UIApplication.shared.keyWindow?.rootViewController  {
            var items: [Any] = []
            
            let fileURL = NSURL(fileURLWithPath: path)
            items.append(fileURL as Any)
            
            let activityViewController = UIActivityViewController(activityItems: items, applicationActivities: nil)
            
            navigationController.present(activityViewController, animated: true, completion: nil)
        } else {
            print("[shareFile]: No navigation controller")
        }
    }
    
    func shareImage(_ path: String) -> Void {
        print("[shareImage]: \(path)")

        if let navigationController = UIApplication.shared.keyWindow?.rootViewController  {
            var items: [Any] = []
            
            if let image = UIImage(contentsOfFile: path) {
                items.append(image as Any)
            }

            let activityViewController = UIActivityViewController(activityItems: items, applicationActivities: nil)

            navigationController.present(activityViewController, animated: true, completion: nil)
        } else {
            print("[shareImage]: No navigation controller")
        }
    }
    
    public func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [AnyHashable : Any] = [:]) -> Bool {
        if let url = launchOptions[UIApplicationLaunchOptionsKey.url] as? URL {
            return handleUrl(url: url, setInitialData: true)
        } else if let activityDictionary = launchOptions[UIApplicationLaunchOptionsKey.userActivityDictionary] as? [AnyHashable: Any] { //Universal link
            for key in activityDictionary.keys {
                if let userActivity = activityDictionary[key] as? NSUserActivity {
                    if let url = userActivity.webpageURL {
                        return handleUrl(url: url, setInitialData: true)
                    }
                }
            }
        }
        return false
    }
    
    public func application(_ application: UIApplication, open url: URL, options: [UIApplicationOpenURLOptionsKey : Any] = [:]) -> Bool {
        return handleUrl(url: url, setInitialData: false)
    }
    
    public func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([Any]) -> Void) -> Bool {
        return handleUrl(url: userActivity.webpageURL, setInitialData: true)
    }
    
    private func handleUrl(url: URL?, setInitialData: Bool) -> Bool {
        if let url = url {
            let appDomain = Bundle.main.bundleIdentifier!
            let userDefaults = UserDefaults(suiteName: "group.\(appDomain)")
            if url.fragment == "media" {
                if let key = url.host?.components(separatedBy: "=").last,
                    let json = userDefaults?.object(forKey: key) as? Data {
                    let sharedArray = decode(data: json)
                    let sharedMediaFiles: [SharedMediaFile] = sharedArray.compactMap{
                        guard let path = getAbsolutePath(for: $0.path) else {
                            return nil
                        }
                        
                        if ($0.type == .video && $0.thumbnail != nil) {
                            guard let thumbnail = getAbsolutePath(for: $0.thumbnail!) else {
                                // If its video and it does not have a thumbnail return nil
                                return nil
                            }
                            return SharedMediaFile.init(path: path, thumbnail: thumbnail, duration: $0.duration, type: $0.type)
                        } else if ($0.type == .video && $0.thumbnail == nil) {
                            // If its video and it does not have a thumbnail return nil
                            return nil
                        }
                        
                        return SharedMediaFile.init(path: path, thumbnail: nil, duration: $0.duration, type: $0.type)
                    }
                    latestMedia = sharedMediaFiles
                    if(setInitialData) {
                        initialMedia = latestMedia
                    }
                    eventSinkMedia?(toJson(data: latestMedia))
                }
            } else if url.fragment == "text" {
                if let key = url.host?.components(separatedBy: "=").last,
                    let sharedArray = userDefaults?.object(forKey: key) as? [String] {
                    latestText =  sharedArray.joined(separator: ",")
                    if(setInitialData) {
                        initialText = latestText
                    }
                    eventSinkText?(latestText)
                }
            } else {
                latestText = url.absoluteString
                if(setInitialData) {
                    initialText = latestText
                }
                eventSinkText?(latestText)
            }
            return true
        }
        
        latestMedia = nil
        latestText = nil
        return false
    }
    
    
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        if (arguments as! String? == "media") {
            eventSinkMedia = events;
        } else if (arguments as! String? == "text") {
            eventSinkText = events;
        } else {
            return FlutterError.init(code: "NO_SUCH_ARGUMENT", message: "No such argument\(String(describing: arguments))", details: nil);
        }
        return nil;
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        if (arguments as! String? == "media") {
            eventSinkMedia = nil;
        } else if (arguments as! String? == "text") {
            eventSinkText = nil;
        } else {
            return FlutterError.init(code: "NO_SUCH_ARGUMENT", message: "No such argument as \(String(describing: arguments))", details: nil);
        }
        return nil;
    }
    
    private func getAbsolutePath(for identifier: String) -> String? {
        if (identifier.starts(with: "file://") || identifier.starts(with: "/var/mobile/Media") || identifier.starts(with: "/private/var/mobile")) {
            return identifier.replacingOccurrences(of: "file://", with: "")
        }
        let phAsset = PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: .none).firstObject
        if(phAsset == nil) {
            return nil
        }
        var url: String?
        
        let options = PHImageRequestOptions()
        options.isSynchronous = true
        options.isNetworkAccessAllowed = true
        PHImageManager.default().requestImageData(for: phAsset!, options: options) { (data, fileName, orientation, info) in
            url = (info?["PHImageFileURLKey"] as? NSURL)?.absoluteString?.replacingOccurrences(of: "file://", with: "")
        }
        return url
    }
    
    private func decode(data: Data) -> [SharedMediaFile] {
        let encodedData = try? JSONDecoder().decode([SharedMediaFile].self, from: data)
        return encodedData!
    }
    
    private func toJson(data: [SharedMediaFile]?) -> String? {
        if data == nil {
            return nil
        }
        let encodedData = try? JSONEncoder().encode(data)
        let json = String(data: encodedData!, encoding: .utf8)!
        return json
    }
    
    class SharedMediaFile: Codable {
        var path: String;
        var thumbnail: String?; // video thumbnail
        var duration: Double?; // video duration in milliseconds
        var type: SharedMediaType;
        
        
        init(path: String, thumbnail: String?, duration: Double?, type: SharedMediaType) {
            self.path = path
            self.thumbnail = thumbnail
            self.duration = duration
            self.type = type
        }
    }
    
    enum SharedMediaType: Int, Codable {
        case image
        case video
        case file
    }
}
