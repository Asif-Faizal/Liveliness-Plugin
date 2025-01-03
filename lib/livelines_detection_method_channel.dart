import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'livelines_detection_platform_interface.dart';

class MethodChannelLivelinesDetection extends LivelinesDetectionPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('livelines_detection');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }

  @override
  Future<void> launchCamera() async {
    try {
      await methodChannel.invokeMethod('launchCamera');
    } on PlatformException catch (e) {
      debugPrint("Failed to launch camera: ${e.message}");
      throw e;
    }
  }
}
