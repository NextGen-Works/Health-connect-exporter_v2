<div align="center">
  <img src="https://img.shields.io/badge/Flutter-3.44+-02569B?style=for-the-badge&logo=flutter&logoColor=white" alt="Flutter">
  <img src="https://img.shields.io/badge/Dart-3.12+-0175C2?style=for-the-badge&logo=dart&logoColor=white" alt="Dart">
  <img src="https://img.shields.io/badge/minSdk-26-6DB33F?style=for-the-badge&logo=android&logoColor=white" alt="minSdk 26">
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="License">
  <br>
  <img src="https://img.shields.io/badge/Health_Connect-✓-00C853?style=flat-square" alt="Health Connect">
  <img src="https://img.shields.io/badge/Foreground_Service-✓-00C853?style=flat-square" alt="Foreground Service">
  <img src="https://img.shields.io/badge/No_Firebase_Required-✓-00C853?style=flat-square" alt="No Firebase">
</div>

<br>

<p align="center">
  <h1 align="center">HCGateway <em>(Flutter)</em></h1>
  <p align="center">
    A universal REST API bridge for Android Health Connect — built with Flutter.
    <br />
    Read 30+ health data types from Health Connect and sync them to your own backend.
    <br />
    <a href="https://github.com/ShuchirJ/HCGateway"><strong>Original Project »</strong></a>
  </p>
</p>

<br>

---

## ✨ Features

<table>
  <tr>
    <td width="50%">
      <h3>📊 Health Connect Integration</h3>
      <ul>
        <li>Reads <strong>30+ health data types</strong> from Android Health Connect</li>
        <li>Steps, heart rate, sleep, blood pressure, glucose, weight, and more</li>
        <li>Two-way sync capable (device ⟷ server)</li>
      </ul>
    </td>
    <td width="50%">
      <h3>🔄 REST API Bridge</h3>
      <ul>
        <li>Configurable API base URL</li>
        <li>Auto-login & token refresh</li>
        <li>Full 30-day or incremental sync modes</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>⚙️ Background Sync</h3>
      <ul>
        <li>Foreground service with persistent notification</li>
        <li>Configurable sync interval (default: every 2 hours)</li>
        <li>Custom date range selection for manual sync</li>
      </ul>
    </td>
    <td width="50%">
      <h3>🔒 Privacy-First</h3>
      <ul>
        <li><strong>No Firebase required</strong> — no external dependencies</li>
        <li>No analytics, no crash reporting</li>
        <li>Your data, your server</li>
      </ul>
    </td>
  </tr>
</table>

---

## 🚀 Quick Start

### Prerequisites
- Android device running **Android 8.0 (API 26) or newer**
- [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) app installed from Play Store
- A running HCGateway server (or use the hosted instance at `https://api.hcgateway.shuchir.dev`)

### Installation
1. Download the latest APK from the [Releases](../../releases) page
2. Install on your device (you may need to enable "Install from unknown sources")
3. Open the app, enter a **username** and **password**
4. Grant **all Health Connect permissions** when prompted
5. Tap **Sync Now**

> New to HCGateway? An account will be created automatically on first login.

---

## 📱 Supported Health Data Types

<details>
<summary><b>Click to expand full list (30+ types)</b></summary>
<br>

| Category | Types |
|---|---|
| **Activity** | Active Calories Burned, Steps, Floors Climbed, Distance, Elevation Gained, Power, Speed, Wheelchair Pushes |
| **Body Metrics** | Weight, Height, Body Fat, Body Temperature, Basal Body Temperature, Bone Mass, Lean Body Mass, BMI |
| **Cardiovascular** | Heart Rate, Resting Heart Rate, Blood Pressure (systolic/diastolic), VO2 Max, Oxygen Saturation |
| **Glucose** | Blood Glucose |
| **Metabolic** | Basal Metabolic Rate, Total Calories Burned |
| **Hydration & Nutrition** | Hydration (Water), Nutrition |
| **Sleep** | Sleep Session (all stages: deep, light, REM, awake) |
| **Respiratory** | Respiratory Rate |
| **Reproductive** | Menstruation Flow, Menstruation Period, Ovulation Test, Cervical Mucus |
| **Exercise** | Exercise Session (Workout) |

</details>

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   HCGateway Flutter App                      │
│                                                             │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ Login Screen │  │  Home Screen │  │ Background Service│  │
│  │  (auth)      │  │  (settings)  │  │  (foreground sync)│  │
│  └──────┬───────┘  └──────┬───────┘  └─────────┬─────────┘  │
│         │                 │                    │            │
│         ▼                 ▼                    ▼            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   Sync Service                       │   │
│  │  Reads Health Connect → Formats → POSTs to API      │   │
│  └────────────────────────┬────────────────────────────┘   │
│                          │                                 │
└──────────────────────────┼─────────────────────────────────┘
                           │
          ┌────────────────▼────────────────┐
          │          HCGateway API           │
          │     (Python / Flask / MongoDB)   │
          │  https://api.hcgateway.shuchir.dev│
          └─────────────────────────────────┘
```

---

## ⚙️ Configuration

All settings are configurable directly from the app:

| Setting | Description | Default |
|---|---|---|
| **API Base URL** | Your HCGateway server address | `https://api.hcgateway.shuchir.dev` |
| **Sync Interval** | How often to auto-sync (hours) | `2` |
| **Full Sync** | Sync all 30 days vs incremental | `true` |
| **Auto Sync** | Enable background periodic sync | `false` |
| **Custom Range** | Sync a specific date range | — |

---

## 🛠️ Building from Source

```bash
# Clone
git clone https://github.com/yourusername/hcgateway-flutter.git
cd hcgateway-flutter

# Get dependencies
flutter pub get

# Analyze (should be clean)
flutter analyze

# Build APK (universal)
flutter build apk --release

# Build split APKs (smaller per-device)
flutter build apk --release --split-per-abi
```

Outputs in `build/app/outputs/flutter-apk/`

---

## 🧩 Tech Stack

| Component | Technology |
|---|---|
| **Framework** | Flutter 3.44+ / Dart 3.12+ |
| **Health API** | [`health`](https://pub.dev/packages/health) v13 (Health Connect) |
| **HTTP Client** | [`dio`](https://pub.dev/packages/dio) v5 |
| **Local Storage** | [`shared_preferences`](https://pub.dev/packages/shared_preferences) |
| **Background Service** | [`flutter_background_service`](https://pub.dev/packages/flutter_background_service) v5 + [`flutter_local_notifications`](https://pub.dev/packages/flutter_local_notifications) |
| **State Management** | StatefulWidget + ChangeNotifier pattern |
| **Minimum API** | Android 8.0 (API 26) |
| **Target API** | Android 14+ (API 34+) |

---

## 🤝 Contributing

Contributions are welcome! Feel free to open issues or submit PRs.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the **GPL-3.0 License**. See [`LICENSE`](LICENSE) for more information.

---

## 🙏 Acknowledgments

- [ShuchirJ](https://github.com/ShuchirJ) — Original HCGateway project & API
- [health](https://pub.dev/packages/health) Flutter package — Health Connect integration
- All contributors to the HCGateway ecosystem

---

<div align="center">
  <sub>Built with ❤️ for the health data community</sub>
</div>
