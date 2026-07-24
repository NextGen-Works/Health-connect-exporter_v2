import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../services/api_service.dart';
import '../services/storage_service.dart';
import '../services/sync_service.dart';
import '../services/background_service.dart';
import 'login_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _api = ApiService();
  final _sync = SyncService();
  final _background = BackgroundService();
  final _storage = StorageService();

  final _apiUrlController = TextEditingController();
  final _intervalController = TextEditingController();

  bool _fullSync = true;
  bool _autoSync = true;
  int _intervalHours = 2;
  DateTime? _customStart;
  DateTime? _customEnd;

  @override
  void initState() {
    super.initState();
    _apiUrlController.text = _api.baseUrl;
    _fullSync = _storage.getBool('fullSync') ?? true;
    _autoSync = _storage.getBool('autoSync') ?? false;
    _intervalHours = _storage.getInt('intervalHours') ?? 2;
    _intervalController.text = _intervalHours.toString();

    _sync.addListener(_onSyncUpdate);
  }

  @override
  void dispose() {
    _apiUrlController.dispose();
    _intervalController.dispose();
    _sync.removeListener(_onSyncUpdate);
    super.dispose();
  }

  void _onSyncUpdate() {
    if (mounted) setState(() {});
  }

  Future<void> _toggleAutoSync(bool value) async {
    setState(() => _autoSync = value);
    await _storage.setBool('autoSync', value);
    if (value) {
      await _background.start(intervalHours: _intervalHours);
    } else {
      await _background.stop();
    }
  }

  Future<void> _logout() async {
    await _background.stop();
    await _storage.remove('login');
    await _storage.remove('refreshToken');
    await _storage.remove('username');
    ApiService().clearAuthToken();

    if (!mounted) return;
    Navigator.pushReplacement(
      context,
      MaterialPageRoute(builder: (_) => const LoginScreen()),
    );
  }

  Future<void> _pickCustomRange() async {
    final range = await showDateRangePicker(
      context: context,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now(),
      initialDateRange: _customStart != null && _customEnd != null
          ? DateTimeRange(start: _customStart!, end: _customEnd!)
          : null,
    );
    if (range != null) {
      setState(() {
        _customStart = range.start;
        _customEnd = range.end;
      });
    }
  }

  void _startSync() {
    if (_customStart != null && _customEnd != null) {
      _sync.sync(customStart: _customStart, customEnd: _customEnd);
    } else {
      _sync.sync();
    }
  }

  String get _lastSyncText {
    final last = _storage.getString('lastSync');
    if (last == null) return 'Never';
    try {
      final dt = DateTime.parse(last);
      return DateFormat('MMM dd, yyyy HH:mm').format(dt.toLocal());
    } catch (_) {
      return last;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('HCGateway'),
        actions: [
          IconButton(icon: const Icon(Icons.logout), onPressed: _logout),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Last Sync: $_lastSyncText',
                style: const TextStyle(fontSize: 16)),
            const SizedBox(height: 24),

            const Text('API Base URL:', style: TextStyle(fontSize: 15)),
            const SizedBox(height: 4),
            TextField(
              controller: _apiUrlController,
              decoration: const InputDecoration(border: OutlineInputBorder()),
              onChanged: (url) => _api.baseUrl = url,
            ),
            const SizedBox(height: 16),

            SwitchListTile(
              title: const Text('Auto Sync'),
              subtitle: const Text('Sync periodically in the background'),
              value: _autoSync,
              onChanged: _toggleAutoSync,
            ),

            if (_autoSync) ...[
              const SizedBox(height: 8),
              TextField(
                controller: _intervalController,
                decoration: const InputDecoration(
                  labelText: 'Sync Interval (hours)',
                  border: OutlineInputBorder(),
                ),
                keyboardType: TextInputType.number,
                onChanged: (val) {
                  final h = int.tryParse(val);
                  if (h != null && h > 0) {
                    _intervalHours = h;
                    _storage.setInt('intervalHours', h);
                    _background.updateInterval(h);
                  }
                },
              ),
            ],

            const SizedBox(height: 16),
            SwitchListTile(
              title: const Text('Full 30-day sync'),
              subtitle: const Text(
                  'Sync all available data, not just since last sync'),
              value: _fullSync,
              onChanged: (val) {
                setState(() => _fullSync = val);
                _storage.setBool('fullSync', val);
              },
            ),

            const SizedBox(height: 16),
            Row(
              children: [
                const Text('Custom date range: '),
                const SizedBox(width: 8),
                TextButton(
                  onPressed: _pickCustomRange,
                  child: Text(
                    _customStart != null && _customEnd != null
                        ? '${DateFormat.MMMd().format(_customStart!)} - ${DateFormat.MMMd().format(_customEnd!)}'
                        : 'Select dates',
                  ),
                ),
              ],
            ),

            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton.icon(
                onPressed: _sync.isSyncing ? null : _startSync,
                icon: _sync.isSyncing
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.sync),
                label: Text(
                  _sync.isSyncing
                      ? 'Syncing ${_sync.syncedRecords}/${_sync.totalRecords}...'
                      : 'Sync Now',
                ),
              ),
            ),

            if (_sync.currentStatus.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(_sync.currentStatus,
                  style: const TextStyle(color: Colors.grey)),
            ],

            if (_sync.isSyncing && _sync.totalRecords > 0) ...[
              const SizedBox(height: 8),
              LinearProgressIndicator(
                value: _sync.totalRecords > 0
                    ? _sync.syncedRecords / _sync.totalRecords
                    : null,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
