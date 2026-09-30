# Tend ship themes

Tend's browser and Android clients follow the authenticated ship's Omarchy color
palette and light/dark mode. Typography, spacing, icons, and list-specific colors
are unchanged. Gall and the desktop plugins do not need a code change.

- Browser: **Settings → Appearance → Colors and mode**.
- Android: **Accounts → Appearance → Follow ship theme**.

Following is on by default. Choose **Tend default** (turn the Android switch off)
to use the original dark Tend palette. This preference is local to that account
and device/browser; it does not modify the theme on the ship or any other client.
Android keeps the last valid palette with its offline account cache. The browser
caches palettes in localStorage and uses them once the ship identity is known;
this does not make the browser app an offline PWA.

## Published interface

The reader implements the existing
[omarchy-urbit-theme contract](https://github.com/thelifeandtimes/omarchy-urbit-theme),
not a new Tend-specific settings schema:

1. `%settings`, desk `omarchy-urbit-theme`, bucket `appearance`, entry `current`.
   This is a JSON-encoded version-1 profile. Only `colors` and its `mode` are
   consumed. Font, shell, file, animation, and window settings are ignored.
2. If the full profile is absent, read desk `talon`, bucket `ui-prefs`, entry
   `themes`. Select the theme with stable ID `omarchy-urbit-theme`, regardless
   of Talon's `activeId`. This supports ships receiving only the Talon bridge.
3. If neither is present, use Tend's original palette. Invalid/unknown profiles
   and temporary request failures retain the last valid palette instead.

Both clients subscribe to `/desk/omarchy-urbit-theme` and `/desk/talon`. A
successful subscription acknowledgement triggers the initial scry; subsequent
facts invalidate that snapshot and trigger serialized, coalesced scries. They
resubscribe and re-scry after reconnect, with periodic reconciliation as a
fallback. `updateId` is opaque identity, never an ordered revision or timestamp.

Appearance is separate from Tend's reminder subscription/state. CSS variables
change in place in the web client; Compose's theme changes without replacing its
editor state. Settings failures cannot take the reminder connection offline.
Android stops watches in the background and catches up on foregrounding.

Only known six-digit hex colors are applied. Missing optional surface colors are
derived from the supplied background/foreground (including light palettes), and
foregrounds on accent/error/selection controls are selected for contrast. Theme
entries are limited to 256 KiB and desk responses to 1 MiB. No profile-supplied
CSS, files, fonts, URLs, or executable content is interpreted.

## Frontend-only deployment

The web application remains a single `desk/app/tend.html`, embedded by the
existing Tend agent. Copy only that file into the publisher's mounted
`tend/app/tend.html`, retaining a backup, then `|commit %tend`. This recompiles the
unchanged agent with the new embedded frontend; it is not a Gall logic change.
Do not replace the desk wholesale or copy mobile files into it. Use the normal
desk distribution workflow for subscribers.

Install the signed Android alpha02 over alpha01; it uses the same application ID
and signing identity and preserves saved accounts/cache. No new permission or
Google service is needed. See [mobile installation](../mobile/README.md).

## Repeatable verification

Read-only/unit checks:

```sh
node --test tests/test_tend_web*.js
cd mobile
./gradlew :shared:jvmTest :androidApp:lintDebug
```

The opt-in tests below directly change only two theme entries on the authorized
fake ships and restore their prior values in `finally`. They never change a
plugin or Gall source, and do not edit existing reminders. Do not run the two
tests concurrently, because they intentionally exercise the same settings.

Browser, from repository root, with Playwright installed:

```sh
node tests/tend_theme_live.mjs
```

Optional `PLAYWRIGHT_MODULE` and `CHROMIUM_EXECUTABLE` select an existing local
runtime/browser. The test authenticates `~wex` at 8082/loopback 12324 and `~rus`
at 8081/loopback 12323, routes only the frontend document to the worktree version,
and leaves all Eyre/settings/Tend requests against the actual ships. It never
logs or stores their codes/cookies. Screenshots go under
`mobile/artifacts/themes/web`.

Android, with one disposable API-36 emulator, from `mobile/`:

```sh
./gradlew :androidApp:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.omabit.tend.NativeThemeTest \
  -Pandroid.testInstrumentationRunnerArguments.tendThemeFakes=true \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

This test uses the emulator's `10.0.2.2` host bridge and clears only the disposable
development app's saved test accounts. It covers full profiles, bridge-only
ships, live updates while typing, rotation, system-bar appearance, account-local
opt-out, cached/offline palettes, invalid profiles, and deletion fallback.

The test palettes are deliberately distinct light/dark QA examples; they are
not new product defaults. Physical GrapheneOS/Android 17 validation remains a
separate acceptance check. iOS host work and plugin testing remain out of scope.
