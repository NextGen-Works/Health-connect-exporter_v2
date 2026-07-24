import 'package:flutter/material.dart';
import 'services/storage_service.dart';
import 'services/api_service.dart';
import 'services/health_service.dart';
import 'services/background_service.dart';
import 'screens/login_screen.dart';
import 'screens/home_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await StorageService().init();
  await HealthService().configure();
  await BackgroundService().init();

  ApiService().init();

  final token = StorageService().getString('login');
  if (token != null) {
    ApiService().clearAuthToken();
  }

  runApp(const HCGatewayApp());
}

class HCGatewayApp extends StatelessWidget {
  const HCGatewayApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HCGateway',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: Colors.teal,
        useMaterial3: true,
      ),
      home: StorageService().getString('login') != null
          ? const HomeScreen()
          : const LoginScreen(),
    );
  }
}
