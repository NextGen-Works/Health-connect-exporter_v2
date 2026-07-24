import 'dart:async';
import 'package:flutter_background_service/flutter_background_service.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'sync_service.dart';

class BackgroundService {
  static final BackgroundService _instance = BackgroundService._internal();
  factory BackgroundService() => _instance;
  BackgroundService._internal();

  final FlutterLocalNotificationsPlugin _notifications = FlutterLocalNotificationsPlugin();
  Timer? _syncTimer;
  int _intervalHours = 2;

  Future<void> init() async {
    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosSettings = DarwinInitializationSettings();
    await _notifications.initialize(
      const InitializationSettings(android: androidSettings, iOS: iosSettings),
    );

    final service = FlutterBackgroundService();

    await service.configure(
      androidConfiguration: AndroidConfiguration(
        onStart: onStart,
        autoStart: false,
        isForegroundMode: true,
        notificationChannelId: 'hcgateway_sync',
        initialNotificationTitle: 'HCGateway Sync',
        initialNotificationContent: 'Starting...',
        foregroundServiceNotificationId: 1244,
      ),
      iosConfiguration: IosConfiguration(
        autoStart: false,
        onForeground: onStart,
      ),
    );
  }

  static void onStart(ServiceInstance service) {
    if (service is AndroidServiceInstance) {
      service.setAsForegroundService();
    }
  }

  Future<void> start({int intervalHours = 2}) async {
    _intervalHours = intervalHours;
    final service = FlutterBackgroundService();
    await service.startService();

    _syncTimer?.cancel();
    _syncTimer = Timer.periodic(
      Duration(hours: _intervalHours),
      (_) => SyncService().sync(),
    );
  }

  Future<void> stop() async {
    _syncTimer?.cancel();
    final service = FlutterBackgroundService();
    service.invoke('stopService');
  }

  void updateInterval(int hours) {
    _intervalHours = hours;
    if (_syncTimer != null && _syncTimer!.isActive) {
      _syncTimer!.cancel();
      _syncTimer = Timer.periodic(
        Duration(hours: _intervalHours),
        (_) => SyncService().sync(),
      );
    }
  }
}
