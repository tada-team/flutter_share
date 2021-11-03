import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_share/flutter_share_receiver.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _shareContent = 'Unknown';

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Text('Share content: $_shareContent'),
        ),
      ),
    );
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
      FlutterShareReceiver().configure(
        onReceive: (share) async {
          setState(() {
            _shareContent = share.toString();
          });
        },
        onPermissionError: (e) {
          setState(() {
            _shareContent = e.toString();
          });
        },
      );

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {});
  }

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }
}
