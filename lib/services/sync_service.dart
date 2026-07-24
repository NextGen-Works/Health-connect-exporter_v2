import 'api_service.dart';
import 'health_service.dart';

class SyncService {
  static final SyncService _instance = SyncService._internal();
  factory SyncService() => _instance;
  SyncService._internal();

  final _api = ApiService();
  final _health = HealthService();

  bool _isSyncing = false;
  int _totalRecords = 0;
  int _syncedRecords = 0;
  String _currentStatus = '';

  bool get isSyncing => _isSyncing;
  int get totalRecords => _totalRecords;
  int get syncedRecords => _syncedRecords;
  String get currentStatus => _currentStatus;

  final List<void Function()> _listeners = [];
  void addListener(void Function() listener) => _listeners.add(listener);
  void removeListener(void Function() listener) => _listeners.remove(listener);
  void _notify() {
    for (final l in _listeners) {
      l();
    }
  }

  Future<void> sync({DateTime? customStart, DateTime? customEnd}) async {
    if (_isSyncing) return;
    _isSyncing = true;
    _totalRecords = 0;
    _syncedRecords = 0;
    _notify();

    try {
      final endTime = customEnd ?? DateTime.now();
      final startTime = customStart ?? endTime.subtract(const Duration(days: 29));

      _currentStatus = 'Reading health data...';
      _notify();

      final allData = await _health.readAndFormatAllData(startTime, endTime);

      _totalRecords = allData.values.fold(0, (sum, list) => sum + list.length);
      _syncedRecords = 0;

      for (final entry in allData.entries) {
        final recordType = entry.key;
        final records = entry.value;

        _currentStatus = 'Syncing $recordType (${records.length} records)...';
        _notify();

        try {
          await _api.syncRecords(recordType, records);
          _syncedRecords += records.length;
        } catch (e) {
          // continue with other types on failure
        }

        _notify();
      }

      _currentStatus = 'Sync complete';
    } catch (e) {
      _currentStatus = 'Sync failed: $e';
    } finally {
      _isSyncing = false;
      _notify();
    }
  }
}
