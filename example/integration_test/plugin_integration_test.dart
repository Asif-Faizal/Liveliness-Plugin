import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:livelines_detection/livelines_detection.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('getPlatformVersion test', (WidgetTester tester) async {
    final LivelinesDetection plugin = LivelinesDetection();
    final String? version = await plugin.getPlatformVersion();
    expect(version?.isNotEmpty, true);
  });
}
