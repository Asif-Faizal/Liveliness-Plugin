import 'livelines_detection_platform_interface.dart';

class LivelinesDetection {
  Future<String?> getPlatformVersion() {
    return LivelinesDetectionPlatform.instance.getPlatformVersion();
  }

  Future<void> launchCamera() {
    return LivelinesDetectionPlatform.instance.launchCamera();
  }
}
