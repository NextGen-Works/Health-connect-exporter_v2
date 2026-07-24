import 'package:health/health.dart';

class HealthService {
  static final HealthService _instance = HealthService._internal();
  factory HealthService() => _instance;
  HealthService._internal();

  final Health _health = Health();

  static final List<HealthDataType> androidDataTypes = [
    HealthDataType.ACTIVE_ENERGY_BURNED,
    HealthDataType.BASAL_ENERGY_BURNED,
    HealthDataType.BLOOD_GLUCOSE,
    HealthDataType.BLOOD_OXYGEN,
    HealthDataType.BLOOD_PRESSURE_DIASTOLIC,
    HealthDataType.BLOOD_PRESSURE_SYSTOLIC,
    HealthDataType.BODY_FAT_PERCENTAGE,
    HealthDataType.LEAN_BODY_MASS,
    HealthDataType.BODY_MASS_INDEX,
    HealthDataType.BODY_TEMPERATURE,
    HealthDataType.BODY_WATER_MASS,
    HealthDataType.HEART_RATE,
    HealthDataType.HEART_RATE_VARIABILITY_RMSSD,
    HealthDataType.HEIGHT,
    HealthDataType.STEPS,
    HealthDataType.WEIGHT,
    HealthDataType.DISTANCE_DELTA,
    HealthDataType.SPEED,
    HealthDataType.SLEEP_ASLEEP,
    HealthDataType.SLEEP_AWAKE_IN_BED,
    HealthDataType.SLEEP_AWAKE,
    HealthDataType.SLEEP_DEEP,
    HealthDataType.SLEEP_LIGHT,
    HealthDataType.SLEEP_OUT_OF_BED,
    HealthDataType.SLEEP_REM,
    HealthDataType.SLEEP_SESSION,
    HealthDataType.SLEEP_UNKNOWN,
    HealthDataType.WATER,
    HealthDataType.WORKOUT,
    HealthDataType.WORKOUT_ROUTE,
    HealthDataType.RESTING_HEART_RATE,
    HealthDataType.FLIGHTS_CLIMBED,
    HealthDataType.RESPIRATORY_RATE,
    HealthDataType.NUTRITION,
    HealthDataType.TOTAL_CALORIES_BURNED,
    HealthDataType.MENSTRUATION_FLOW,
    HealthDataType.ACTIVITY_INTENSITY,
    HealthDataType.SKIN_TEMPERATURE,
  ];

  static final Map<HealthDataType, String> typeToApiName = {
    HealthDataType.ACTIVE_ENERGY_BURNED: 'ActiveCaloriesBurned',
    HealthDataType.BASAL_ENERGY_BURNED: 'BasalMetabolicRate',
    HealthDataType.BLOOD_GLUCOSE: 'BloodGlucose',
    HealthDataType.BLOOD_OXYGEN: 'OxygenSaturation',
    HealthDataType.BLOOD_PRESSURE_DIASTOLIC: 'BloodPressure',
    HealthDataType.BLOOD_PRESSURE_SYSTOLIC: 'BloodPressure',
    HealthDataType.BODY_FAT_PERCENTAGE: 'BodyFat',
    HealthDataType.LEAN_BODY_MASS: 'LeanBodyMass',
    HealthDataType.BODY_MASS_INDEX: 'BodyFat',
    HealthDataType.BODY_TEMPERATURE: 'BodyTemperature',
    HealthDataType.BODY_WATER_MASS: 'BoneMass',
    HealthDataType.HEART_RATE: 'HeartRate',
    HealthDataType.HEIGHT: 'Height',
    HealthDataType.STEPS: 'Steps',
    HealthDataType.WEIGHT: 'Weight',
    HealthDataType.DISTANCE_DELTA: 'Distance',
    HealthDataType.SPEED: 'Speed',
    HealthDataType.SLEEP_SESSION: 'SleepSession',
    HealthDataType.WATER: 'Hydration',
    HealthDataType.WORKOUT: 'ExerciseSession',
    HealthDataType.RESTING_HEART_RATE: 'RestingHeartRate',
    HealthDataType.FLIGHTS_CLIMBED: 'FloorsClimbed',
    HealthDataType.RESPIRATORY_RATE: 'RespiratoryRate',
    HealthDataType.NUTRITION: 'Nutrition',
    HealthDataType.TOTAL_CALORIES_BURNED: 'TotalCaloriesBurned',
    HealthDataType.MENSTRUATION_FLOW: 'MenstruationFlow',
  };

  Future<void> configure() async {
    await _health.configure();
  }

  Future<bool> requestPermissions() async {
    return await _health.requestAuthorization(androidDataTypes);
  }

  Future<Map<String, List<Map<String, dynamic>>>> readAndFormatAllData(
    DateTime startTime,
    DateTime endTime,
  ) async {
    final result = <String, List<Map<String, dynamic>>>{};

    for (final type in androidDataTypes) {
      final apiName = typeToApiName[type];
      if (apiName == null) continue;

      try {
        final points = await _health.getHealthDataFromTypes(
          startTime: startTime,
          endTime: endTime,
          types: [type],
        );

        if (points.isEmpty) continue;

        final records = points.map((p) => _healthDataPointToMap(p, apiName)).toList();
        result.putIfAbsent(apiName, () => []).addAll(records);
      } catch (e) {
        // skip types that fail
      }
    }

    return result;
  }

  Map<String, dynamic> _healthDataPointToMap(HealthDataPoint point, String apiName) {
    final json = point.toJson();

    return {
      'metadata': {
        'id': point.uuid,
        'dataOrigin': json['source_id'] ?? '',
      },
      'startTime': point.dateFrom.toUtc().toIso8601String(),
      'endTime': point.dateTo.toUtc().toIso8601String(),
      if (point.value is NumericHealthValue)
        'value': (point.value as NumericHealthValue).numericValue,
    };
  }
}
