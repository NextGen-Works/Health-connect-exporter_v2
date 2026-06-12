# Health Connect Exporter v2

A production-grade, local-first Android backup utility designed as a robust replace-in-place client for [hcgateway](https://github.com/your-org/hcgateway) self-hosted endpoints.

## Features

- **Local-first architecture** — Data is persisted to SQLite before any network transmission, ensuring nothing is lost in dead zones
- **Idempotent sync** — Deterministic deduplication keys (`[type]_[healthConnectId]_[timestamp]`) guarantee single-delivery
- **Delta sync engine** — Cursor watermarks only advance on server acknowledgement, with configurable rolling lookback
- **Automatic retry** — Exponential backoff on network failures with status reversion for failed batches
- **Manual repair sync** — Re-sync arbitrary lookback windows (1–30 days) from the Diagnostics screen
- **8 health data types** — Steps, Heart Rate, Sleep, Distance, Active Calories, Weight, Blood Pressure, Workouts
- **Material 3 UI** — Onboarding, Dashboard, Settings, and Diagnostics screens with dark theme

## Architecture

```
ui/            Compose screens + ViewModels (business logic decoupled)
domain/core/   Sealed Result<T>, AppException tree, Logger
healthconnect/ Health Connect client wrapper with permission handling
storage/       Room entities, DAOs, database (destructive migration)
network/       Retrofit API interface, DTOs, partial acknowledgement models
sync/          WorkManager CoroutineWorker with exponential retry
di/            Hilt dependency injection module
```

## Database Schema

| Entity | Purpose |
|--------|---------|
| `RawHealthRecord` | Archives exact Health Connect payload to prevent model skew |
| `NormalizedHealthRecord` | Outbound records with dedup key and status tracking (`PENDING` / `IN_FLIGHT` / `ACKNOWLEDGED` / `FAILED`) |
| `SyncCursor` | High-watermark per record type with customizable `rollingLookbackMs` (default 24h) |
| `AppEventLog` | Offline audit trail for troubleshooting connectivity failures |
| `PermissionState` | Tracks granted Health Connect permissions |

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 35
- A running [hcgateway](https://github.com/your-org/hcgateway) instance
- Health Connect app installed on device

### Build

```bash
./gradlew assembleDebug
```

### Setup

1. Install and open the app
2. Grant Health Connect permissions during onboarding
3. Navigate to **Settings** and configure:
   - **Base URL** — Your hcgateway endpoint (e.g., `https://health.example.com`)
   - **Authorization Token** — Bearer token for authentication
   - **Device ID** — Unique identifier for this device
4. Select which health data types to export
5. Return to **Dashboard** and tap **Trigger Sync**

## Sync Engine

### Delta Sync Rules

1. Cursor watermarks **never** advance before receiving an affirmative HTTP response
2. Failed batches revert all records to `PENDING` and trigger exponential backoff
3. Partial acknowledgements update individual record statuses correctly
4. Duplicate uploads are blocked via deterministic deduplication keys

### Repair Sync

The Diagnostics screen provides a **Repair Sync** dialog that allows:

- Selecting a lookback range (1, 3, 7, 14, or 30 days)
- Filtering by specific record type or all types
- Re-submitting previously failed records to the gateway

## API Contract

### `POST /v1/ingest/batch`

```json
{
  "records": [
    {
      "id": "hc-uuid",
      "type": "StepsRecord",
      "payload": { "count": 500 },
      "capturedAt": 1700000000000,
      "deduplicationKey": "StepsRecord_hc-uuid_1700000000000"
    }
  ],
  "deviceId": "my-phone",
  "batchId": "uuid-v4"
}
```

### Response (partial acknowledgement support)

```json
{
  "success": true,
  "batchId": "uuid-v4",
  "acknowledged": [
    { "deduplicationKey": "StepsRecord_hc-uuid_1700000000000", "serverRecordId": "srv-1" }
  ],
  "rejected": [
    { "deduplicationKey": "StepsRecord_hc-uuid2_1700000001000", "reason": "Duplicate", "errorCode": "DUPLICATE" }
  ],
  "serverTimestamp": 1700000060000
}
```

## Testing

```bash
./gradlew testDebugUnitTest
```

Tests verify:
- Revoked permissions abort execution without breaking database state
- Cursors are preserved on server errors and advance only on full acknowledgments
- Duplicate uploads are blocked using deterministic keys
- Failed batches correctly revert status for retry

## Tech Stack

| Component | Library |
|-----------|---------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Database | Room (SQLite) |
| Network | Retrofit + OkHttp + Gson |
| Background | WorkManager |
| Health Data | Health Connect SDK |
| Async | Kotlin Coroutines + Flow |
| Preferences | DataStore |
| Testing | JUnit 4 + MockK + Coroutines Test |

## License

MIT License. See [LICENSE](LICENSE) for details.
