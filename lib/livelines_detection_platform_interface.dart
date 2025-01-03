import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'livelines_detection_method_channel.dart';

abstract class LivelinesDetectionPlatform extends PlatformInterface {
  LivelinesDetectionPlatform() : super(token: _token);

  static final Object _token = Object();

  static LivelinesDetectionPlatform _instance = MethodChannelLivelinesDetection();
  static LivelinesDetectionPlatform get instance => _instance;
  static set instance(LivelinesDetectionPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('getPlatformVersion() has not been implemented.');
  }

  Future<void> launchCamera() {
    throw UnimplementedError('launchCamera() has not been implemented.');
  }
}
