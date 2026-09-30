# Tend mobile

Android-first native Tend client. Kotlin Multiplatform/Compose owns protocol,
state and screens; Android provides Room storage and Keystore-backed sessions.
Gall logic and the Omarchy plugin are frozen and unchanged. The theme workstream
also updates the browser frontend to consume the same published settings.

Initial target: GrapheneOS Pixel 8. Minimum Android 8; compile/target SDK 36.
No Google Play Services dependency, analytics, or notification service.

## Install the alpha

1. Copy `artifacts/tend-0.1.0-alpha02.apk` to your phone, or download it from the
   build handoff. Do not install the `androidTest` package.
2. Open it in Files. Temporarily allow that source to install apps if Android
   asks, install Tend, then turn that permission back off.
3. Keep GrapheneOS's **Network** permission enabled for Tend. Join the same
   tailnet if your ship is reachable only through Tailscale.
4. Enter the ship's **HTTPS base URL**, without `/apps/tend`, and its `+code`.
   `%tend` protocol 2 must already be installed. No ship update is needed.
5. Open the navigation menu for lists and smart views. Use **New reminder** to
   choose the list, assignee, date, and repeat rule. Tap a reminder to edit its
   notes, tags, flag, priority, section, or parent. Open **Accounts** to add or
   switch ships; every account has its own session and cache.

The release ID is `io.omabit.tend`; development builds use `io.omabit.tend.dev`
and can coexist with it. Subsequent alpha updates must use the same signing key.
Neither uninstalling nor forgetting an account deletes anything from your ship.
Uninstalling does remove this device's sessions, cache, and unsent drafts.

## What works

- URL/+code sign-in; encrypted, independently switchable ship sessions.
- All six smart views, search, shared lists, invitations, host status, and nesting.
- List creation/appearance/deletion; sections; reminder creation/editing/deletion;
  assignment, notes, links, tags, priorities, flags, and subtask/section placement.
- Date selection and hourly/daily/weekly/monthly/yearly recurrence. Existing
  advanced end-date/ordinal rules are preserved. Completion crossfades recurring
  occurrences and animates list removal/reflow.
- Offline reading, reconnect/backoff, revision-conflict protection, persistent
  in-flight operation tracking, and drafts that survive incoming updates/rotation.
- Automatic ship colors/light-dark mode from `%settings`, full Omarchy profile
  with Talon bridge fallback, cached per ship. **Accounts → Appearance** offers
  a local opt-out. Theme changes preserve open drafts. Fonts/layout stay native.

This is an **alpha**, not full desktop feature parity. Notifications/background
refresh, offline mutations, cross-list transfer, bulk editing, drag ordering,
advanced recurrence-rule authoring are deferred. The clients only read appearance
settings; they never publish a competing theme. See [theme contract and tests](../docs/TEND_THEMES.md).

## Development loop

Use JDK 21, Android SDK platform 36, build-tools 36.0.0, and the checked-in Gradle
wrapper. Set `ANDROID_HOME` (or an untracked `local.properties`). On the initial
development machine the tools are kept under
`~/.local/share/tend-mobile/toolchain/` and Gradle's cache under
`~/.local/share/tend-mobile/gradle/`.

```sh
export JAVA_HOME="$HOME/.local/share/tend-mobile/toolchain/jdk"
export ANDROID_HOME="$HOME/.local/share/tend-mobile/toolchain/android-sdk"
export GRADLE_USER_HOME="$HOME/.local/share/tend-mobile/gradle"
export PATH="$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
./gradlew :shared:jvmTest :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

For a physical Pixel: enable Developer options by tapping **Build number** seven
times, enable **USB debugging**, connect a data-capable cable, unlock the phone,
and approve the computer's RSA fingerprint. `adb devices` must say `device`, not
`unauthorized`. Turn USB debugging off again when not developing. Android Studio
is optional; the command-line SDK, emulator, Gradle, and ADB are sufficient.

The emulator is a disposable virtual phone; ADB installs/runs the app and collects
logs; the SDK supplies Android APIs/build tools. An API 36 Pixel 8 AVD is the
currently reliable automated test target. See [QA.md](QA.md) for the Android 17
emulator limitation and outstanding physical-device verification.

### Repeatable native tests

Run the fixture in a separate terminal, then the tests with one emulator running:

```sh
python3 tools/fixture_ship.py
./gradlew :androidApp:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

Fixture URL in the emulator: `http://10.0.2.2:18787`; codes `zod` and `nec`.
The fixture is loopback-only, ephemeral, and resets for each native test. It is
not a Gall emulator and does not replace the opt-in real-ship integration test.
Debug builds permit HTTP only for localhost, 127.0.0.1, and the emulator host.
Release builds require trusted HTTPS and never bypass certificate validation.

Start the retained virtual Pixel in another terminal (add `-no-window` for
headless testing):

```sh
export ANDROID_AVD_HOME="$HOME/.config/.android/avd"
"$ANDROID_HOME/emulator/emulator" -avd Tend_Pixel8_API36 \
  -no-audio -no-snapshot -gpu swiftshader
```

`NativeLiveFlowTest` is a separate opt-in smoke test for an authorized local
fake ship. Pass `android.testInstrumentationRunnerArguments.class` as
`io.omabit.tend.NativeLiveFlowTest`, `tendLiveUrl` as its emulator-reachable
HTTP URL, and `tendLiveLoopback` as its loopback command endpoint, using Gradle
`-Pandroid.testInstrumentationRunnerArguments.<name>=<value>` arguments.
It reads `+code` privately, signs in through the UI, creates/completes a reminder
in its own QA list, deletes that list, and forgets its emulator-only session.
Do not use this test against a personal or production ship. The default native
suite skips it unless both URLs are provided.

The opt-in JVM integration test accepts `TEND_LIVE_URL` plus
`TEND_LIVE_CODE_JSON` (a private file containing a JSON string with the code).
Optionally set `TEND_PEER_URL` and `TEND_PEER_CODE_JSON` for two-ship tests.
It creates one clearly named QA list, tests canonical operations, and removes its
own list afterward. Never point it at an account without authorization to create
that test data. Credentials are not included in the repository or APK.

### Signed release

The existing alpha signing identity lives outside Git under
`~/.local/share/tend-mobile/signing/`. Back up that directory privately; losing
the key means installed apps cannot receive an in-place update under this ID.
`tools/create-signing-key.sh` is only for a **new** signing identity, not updates.

```sh
export TEND_KEYSTORE="$HOME/.local/share/tend-mobile/signing/tend-alpha.p12"
export TEND_KEY_PASSWORD_FILE="$HOME/.local/share/tend-mobile/signing/password.txt"
./gradlew :shared:jvmTest :androidApp:lintDebug :androidApp:assembleRelease
```

Without signing variables Gradle produces an unsigned release. Never share the
keystore, password file, test credentials, or development cache with the APK.

## Architecture and trust

Following [Talon](https://github.com/nisfeb/talon)'s broad patterns, shared Kotlin
owns protocol models, Ktor/Eyre networking, StateFlow state, and Compose screens.
Android supplies OkHttp, Room, and Android Keystore; UI does not own credentials
or mutate local cache as if it were authoritative.

Gall remains authoritative. HTTP acceptance and a local Eyre poke ack are not
reported as host success. A matching canonical fact or durable Gall receipt
confirms a write. Local-session expiry, unavailable list hosts, incompatible
protocol versions, conflicts, and uncertain writes are distinct states.

Remote replica agents do not retain every host response in the local receipt
scry. A matched live host-result fact confirms such operations. If that fact was
missed while disconnected and no receipt is available, the client keeps the
operation uncertain and does not automatically resubmit it. This conservative
edge case needs a future reconciliation UX/protocol decision, not an invented ack.

Notifications and general offline writes are deliberately out of scope.
Cached data is never authoritative. Existing accounts remain independently
scoped; the currently selected ship is always shown in navigation.
Session cookies are AES-GCM encrypted with a non-exportable Keystore key; `+code`
is exchanged but never stored. Cached list content is in app-private Room storage
(protected by Android/GrapheneOS device encryption, not a separate database key).
Cloud backup and device-transfer backup are disabled. No Gall notification ack is
sent, so using mobile cannot consume another client's notification delivery.

See [DECISIONS.md](DECISIONS.md) for the saved product direction and next steps.
