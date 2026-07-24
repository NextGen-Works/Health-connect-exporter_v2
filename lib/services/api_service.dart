import 'package:dio/dio.dart';
import 'storage_service.dart';

class ApiService {
  static final ApiService _instance = ApiService._internal();
  factory ApiService() => _instance;
  ApiService._internal();

  late Dio _dio;
  String _baseUrl = 'https://api.hcgateway.shuchir.dev';

  static const List<String> recordTypes = [
    'ActiveCaloriesBurned',
    'BasalBodyTemperature',
    'BloodGlucose',
    'BloodPressure',
    'BasalMetabolicRate',
    'BodyFat',
    'BodyTemperature',
    'BoneMass',
    'CervicalMucus',
    'Distance',
    'ElevationGained',
    'FloorsClimbed',
    'HeartRate',
    'Height',
    'Hydration',
    'LeanBodyMass',
    'MenstruationFlow',
    'MenstruationPeriod',
    'Nutrition',
    'OvulationTest',
    'OxygenSaturation',
    'Power',
    'RespiratoryRate',
    'RestingHeartRate',
    'SleepSession',
    'Speed',
    'Steps',
    'StepsCadence',
    'TotalCaloriesBurned',
    'Vo2Max',
    'Weight',
    'WheelchairPushes',
  ];

  static const List<String> detailedReadTypes = ['SleepSession', 'Speed', 'HeartRate'];

  void init() {
    _baseUrl = StorageService().getString('apiBase') ?? _baseUrl;
    _dio = Dio(BaseOptions(
      baseUrl: _baseUrl,
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 60),
      headers: {'Content-Type': 'application/json'},
    ));
  }

  String get baseUrl => _baseUrl;
  set baseUrl(String url) {
    _baseUrl = url;
    _dio.options.baseUrl = url;
    StorageService().setString('apiBase', url);
  }

  void clearAuthToken() {
    _dio.options.headers.remove('Authorization');
  }

  Future<Map<String, dynamic>> login(String username, String password, {String? fcmToken}) async {
    final body = <String, dynamic>{'username': username, 'password': password};
    if (fcmToken != null) body['fcmToken'] = fcmToken;
    final response = await _dio.post('/api/v2/login', data: body);
    return response.data;
  }

  Future<void> syncRecords(String recordType, List<Map<String, dynamic>> records) async {
    if (records.isEmpty) return;
    await _dio.post(
      '/api/v2/sync/$recordType',
      data: {'data': records},
    );
  }

  Future<String> refreshToken(String refreshToken) async {
    final response = await _dio.post('/api/v2/refresh', data: {'refresh': refreshToken});
    return response.data['token'];
  }
}
