import 'package:flutter_test/flutter_test.dart';
import 'package:livelines_detection/livelines_detection.dart';
import 'package:livelines_detection/livelines_detection_platform_interface.dart';
import 'package:livelines_detection/livelines_detection_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockLivelinesDetectionPlatform
    with MockPlatformInterfaceMixin
    implements LivelinesDetectionPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final LivelinesDetectionPlatform initialPlatform = LivelinesDetectionPlatform.instance;

  test('$MethodChannelLivelinesDetection is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelLivelinesDetection>());
  });

  test('getPlatformVersion', () async {
    LivelinesDetection livelinesDetectionPlugin = LivelinesDetection();
    MockLivelinesDetectionPlatform fakePlatform = MockLivelinesDetectionPlatform();
    LivelinesDetectionPlatform.instance = fakePlatform;

    expect(await livelinesDetectionPlugin.getPlatformVersion(), '42');
  });
}
