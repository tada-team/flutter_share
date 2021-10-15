import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_share/flutter_share.dart';
import 'package:meta/meta.dart';
import 'package:permission_handler/permission_handler.dart';

typedef Future<dynamic> SharingReceiveHandler(Share share);

class FlutterShareReceiver {
  factory FlutterShareReceiver() => _instance;

  FlutterShareReceiver.private(MethodChannel channel) : _channel = channel;

  static final FlutterShareReceiver _instance = FlutterShareReceiver.private(
      const MethodChannel('plugins.flutter.io/share'));

  final MethodChannel _channel;

  SharingReceiveHandler _onReceive;
  Function _onPermissionError;

  Future<void> configure({
    @required SharingReceiveHandler onReceive,
    @required Function onPermissionError,
  }) async {
    _onReceive = onReceive;
    _onPermissionError = onPermissionError;

    _channel.setMethodCallHandler(_handleMethod);
    _channel.invokeMethod<void>('configure');
  }

  Future<bool> _checkPermission() async {
    try {
//      PermissionStatus permission = await PermissionHandler()
//          .checkPermissionStatus(PermissionGroup.storage);
//      var hasPermission = permission == PermissionStatus.granted;
//      if (!hasPermission) {
//        Map<PermissionGroup, PermissionStatus> permissions =
//            await PermissionHandler()
//                .requestPermissions([PermissionGroup.storage]);
//        hasPermission =
//            permissions[PermissionGroup.storage] == PermissionStatus.granted;
//      }
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
          Share share =
              Share.fromReceived(call.arguments.cast<String, String>());
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
