import 'package:flutter/services.dart';
import 'package:flutter_share/flutter_share.dart';
import 'package:permission_handler/permission_handler.dart';

typedef Future<dynamic> SharingReceiveHandler(Share share);

class FlutterShareReceiver {
  factory FlutterShareReceiver() => _instance;

  FlutterShareReceiver.private(MethodChannel channel) : _channel = channel;

  static final FlutterShareReceiver _instance =
      FlutterShareReceiver.private(const MethodChannel('plugins.flutter.io/share'));

  final MethodChannel _channel;

  late SharingReceiveHandler _onReceive;
  late Function _onPermissionError;

  Future<void> configure({
    required SharingReceiveHandler onReceive,
    required Function onPermissionError,
  }) async {
    _onReceive = onReceive;
    _onPermissionError = onPermissionError;

    _channel.setMethodCallHandler(_handleMethod);
    _channel.invokeMethod<void>('configure');
  }

  Future<bool> _checkPermission() async {
    try {
      final hasPermission = await Permission.storage.request().isGranted;
      if (!hasPermission) return false;

      return true;
    } catch (e) {
      return false;
    }
  }

  Future<dynamic> _handleMethod(MethodCall call) async {
    switch (call.method) {
      case "onReceive":
        bool permission = await _checkPermission();
        if (permission) {
          Share share = Share.fromReceived(call.arguments.cast<String, String>());
          return _onReceive(share);
        } else {
          _onPermissionError();
        }
        break;
      default:
        throw UnsupportedError("Unrecognized input data");
    }
  }
}
