# Zenith - Open Source Automation Engine

![Project Status](https://img.shields.io/badge/Status-Completed%20(v1.0.0)-blue)
![License](https://img.shields.io/badge/License-MIT-green)

Zenith is a premium, highly fluid, and 100% open-source alternative to IFTTT and Zapier. It empowers users to build automated workflows (Applets) connecting various third-party services and webhooks, featuring a beautifully designed Flutter frontend and a rock-solid, scalable backend.

## 🚀 Features
- **Visual Builder**: A fluid, Dual-Flow (Canvas vs. Wizard) Flutter interface bursting with premium animations and micro-interactions.
- **Background Execution**: A robust queue-based NestJS Microservice worker system to reliably execute actions without blocking.
- **Self-Hosted & Open Source**: Full control over your data and APIs. No enterprise paywalls.
- **Custom Integrations**: Easily add your own webhooks, API endpoints, or OAuth services (with AES-256-GCM token encryption natively integrated).

## 🛠 Tech Stack
- **Frontend**: [Flutter](https://flutter.dev/) (Riverpod for state, go_router for navigation, fluid animations via AnimatedSwitcher)
- **Backend API**: Node.js (NestJS)
- **Database**: PostgreSQL (TypeORM, JSONB dynamic payloads)
- **Task Queue / Engine**: Redis + BullMQ (NestJS Microservice)
- **Security**: `@boringnode/encryption` (chacha20poly1305)

## 📚 Documentation
Detailed documentation for the architecture, APIs, and project phases can be found in the `docs/` directory. If you are new to the project, please read these first:
- [Phased Development Plan](./docs/phased_development_plan.md)
- [Product Requirements Document (PRD)](./docs/prd.md)
- [Technical Architecture](./docs/technical_architecture.md)
- [Database Schema](./docs/database_schema.md)
- [API Specification](./docs/api_design.md)
- [UI / UX Guidelines](./docs/ui_ux_guidelines.md)
- [Troubleshooting Guide](./docs/troubleshooting.md)

---

## 💻 Getting Started (Local Development)

### 1. Infrastructure
Start the PostgreSQL database and Redis queue:
```bash
cd infrastructure
podman-compose up -d
```
> If you are using Docker instead of Podman, use `docker-compose up -d`.

### 2. Backend (NestJS)
Install dependencies and start the development server:
```bash
cd backend
npm install
npm run start:dev
```

### 3. Backend Worker (NestJS Microservice)
Start the background BullMQ consumer in a separate terminal:
```bash
cd backend
npm run start:worker
```

### 4. Frontend (Flutter)
Install Flutter dependencies:
```bash
cd frontend
flutter pub get
dart run build_runner build -d
```

---

## 📲 Running & Building for Devices

### Prerequisites
Ensure Flutter is installed and set up by running:
```bash
flutter doctor
```
Fix any issues reported before proceeding.

---

### ▶️ Run in Development (Hot Reload)

| Platform | Command |
|---|---|
| Auto-detect | `flutter run` |
| Android Emulator / Device | `flutter run -d android` |
| Windows Desktop | `flutter run -d windows` |
| Web (Chrome) | `flutter run -d chrome` |
| iOS Simulator (macOS only) | `flutter run -d ios` |

> **Android Note:** The emulator uses `10.0.2.2` to reach your host machine's `localhost`. The `ApiConstants` helper in `lib/core/constants/api_constants.dart` handles this automatically.

> **Windows Note:** Developer Mode must be enabled in Windows Settings to allow Flutter to create symlinks required for building with plugins.

---

### 📦 Build for Production / Release

#### Android APK (for direct installation)
```bash
cd frontend
flutter build apk --release
```
Output: `build/app/outputs/flutter-apk/app-release.apk`

#### Android App Bundle (for Google Play Store)
```bash
cd frontend
flutter build appbundle --release
```
Output: `build/app/outputs/bundle/release/app-release.aab`

#### Windows Desktop (EXE)
> Requires **Developer Mode** to be enabled in Windows Settings.
```bash
cd frontend
flutter build windows --release
```
Output: `build/windows/x64/runner/Release/`

#### Web (Static Files)
```bash
cd frontend
flutter build web --release
```
Output: `build/web/` — deploy this folder to any static hosting provider (e.g., Firebase Hosting, Vercel, Netlify).

#### iOS (macOS only)
```bash
cd frontend
flutter build ios --release
```
Then open `ios/Runner.xcworkspace` in Xcode and archive for distribution via App Store Connect.

---

### 🧹 Clean Build Cache
If you encounter stale errors after code changes, always run a clean build:
```powershell
# Run each on a separate line in PowerShell
flutter clean
flutter pub get
flutter run
```

---

## 🤝 Contributing
We welcome contributions! Please review the [Phased Development Plan](./docs/phased_development_plan.md) to see what Phase the project is currently in before opening a Pull Request.
