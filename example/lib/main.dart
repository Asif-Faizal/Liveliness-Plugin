import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:livelines_detection/livelines_detection.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _livelinesDetectionPlugin = LivelinesDetection();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  Future<void> initPlatformState() async {
    String platformVersion;
    try {
      platformVersion = await _livelinesDetectionPlugin.getPlatformVersion() ??
          'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Column(
            children: [
              Text('Running on: $_platformVersion\n'),
              ElevatedButton(
                onPressed: () async {
                  try {
                    await _livelinesDetectionPlugin.launchCamera();
                  } catch (e) {
                    print("Error launching camera: $e");
                  }
                },
                child: Text('Camera'),
              )
            ],
          ),
        ),
      ),
    );
  }
}

class CameraScreen extends StatelessWidget {
  final LivelinesDetection livelinesDetection = LivelinesDetection();

  CameraScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text("Camera Detection"),
      ),
      body: Center(
        child: ElevatedButton(
          onPressed: () async {
            try {
              await livelinesDetection.launchCamera();
            } catch (e) {
              print("Error launching camera: $e");
            }
          },
          child: Text("Launch Camera"),
        ),
      ),
    );
  }
}
