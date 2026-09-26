# Tend mobile

Android-first native Tend client. Kotlin Multiplatform/Compose owns protocol,
state and screens; Android provides Room storage and Keystore-backed sessions.
Gall, the web application and the Omarchy plugin are frozen and unchanged.

Initial target: GrapheneOS Pixel 8. Android 8+ is installable; Android 17 is the
primary verification target. No Google Play, analytics or notification service.

Build with JDK 21 and Android SDK 36:

```sh
./gradlew :shared:jvmTest :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Debug ID: `io.omabit.tend.dev`. Production ID: `io.omabit.tend`.
Use a trusted HTTPS ship URL, with `%tend` protocol 2 already installed, and the
ship's `+code`. The code is exchanged, never persisted. Debug builds additionally
allow loopback/emulator-host HTTP for disposable integration tests only.

Notifications and general offline writes are deliberately out of scope.
Cached data is never authoritative. Existing accounts remain independently
scoped; the currently selected ship is always shown in navigation.
