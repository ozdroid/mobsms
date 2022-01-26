import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:mobsms/mobsms.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion =
          await Mobsms.platformVersion ?? 'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  _ckickEvent() async {
    // Mobsms.submitPrivacyGrantResult(true);

    // Mobsms.commitCode("18022*******", "86", "882691", (dynamic ret, Map? err) {
    //   if (err != null) {
    //     debugPrint("Error:" + err.toString());
    //   } else {
    //     String rst = ret.toString();
    //     debugPrint("ret:" + rst);
    //   }
    // });
    // Mobsms.getTextCode("1802218*****", "86", "", (dynamic result, Map? error) {
    //   if (error != null) {
    //     debugPrint("Error:" + error.toString());
    //   } else {
    //     debugPrint("result:" + result.toString());
    //   }
    // });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Text('Running on: $_platformVersion\n'),
        ),
        floatingActionButton: FloatingActionButton(onPressed: () {
          _ckickEvent();
        }),
      ),
    );
  }
}
