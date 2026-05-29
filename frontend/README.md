# Zenith Frontend

This is the frontend application for Zenith, a visual workflow automation engine. It is built using **Flutter**, delivering a highly fluid, responsive, and beautiful cross-platform experience.

## Features

* **Visual Builder:** A fluid Dual-Flow (Canvas vs. Wizard) interface for building Applets.
* **Premium Animations:** Bursting with micro-interactions and smooth AnimatedSwitcher transitions.
* **State Management:** Powered by Riverpod for robust and scalable state handling.
* **Routing:** `go_router` for seamless and deep-linkable navigation.

## Prerequisites

* [Flutter SDK](https://docs.flutter.dev/get-started/install) (Ensure you run `flutter doctor` to verify your setup)
* A device or emulator for testing (Android, iOS, Web, or Desktop)

## Getting Started

1. **Install dependencies:**
   ```bash
   flutter pub get
   ```

2. **Generate code (for Riverpod, Freezed, etc.):**
   ```bash
   dart run build_runner build -d
   ```

3. **Run the app:**
   ```bash
   flutter run
   ```

## Project Structure

* `lib/`: Main application source code.
  * `core/`: Constants, themes, network configuration, and utilities.
  * `features/`: Feature-based architecture containing UI and logic for different parts of the app.
* `android/`, `ios/`, `web/`, `windows/`: Platform-specific configuration files.

## License

This project is licensed under the [Creative Commons Attribution-NonCommercial 4.0 International Public License](../LICENSE) (CC BY-NC 4.0).
