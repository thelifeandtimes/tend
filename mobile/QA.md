# Tend theme sync / Android alpha02 — 2026-09-29

## Deliverable

`artifacts/tend-0.1.0-alpha02.apk` is the signed, optimized Android update.
Application ID `io.omabit.tend`, version code 2, minimum API 26, target API 36.
It uses alpha01's signing identity, so installing over that build preserves
accounts and cached data. No new permissions or Google Play Services dependency.

APK SHA-256:

```text
91a6cd61020f0598ad1bf7d811303a92f4db5bace051b555e5721378f60df910
```

Signing certificate SHA-256:

```text
ecbf2290150711ec6e3d138392fb1115b4dd619b4a857de056c965c2502b4dc5
```

The release passes `apksigner verify` (v2) and 16 KiB `zipalign` verification.
It installed over the previous release and cold-launched on the API-36 emulator
in 291 ms (`am start -W -S`; a single emulator observation, not a device benchmark).
Physical GrapheneOS/Android 17 and production HTTPS acceptance remain separate
from the emulator/fake-ship tests below.

## Verified behavior

- Full Omarchy profile, light/dark palettes, legacy Talon bridge and its optional
  Auto fields, full-profile priority, stable bridge ID independent of `activeId`.
- Live `%settings` updates while typing preserve the browser's focused draft and
  Android's unsent draft. Android draft also survives activity recreation.
- Local opt-out persists independently per account. Switching ships restores
  the right palette and preference, including from cached/offline state.
- Invalid/unknown profiles retain the last valid theme; deleting both supported
  entries restores Tend default. Settings failure does not disable reminder sync.
- Android activity and already-open sheet system-bar icons adapt to light themes.
  Material 3's sheet window initialized those flags only at creation, so a small
  Android-only window hook now updates them without recreating the editor.
- Web changes apply CSS variables without rerendering reminder content. Theme
  switches suppress color transitions briefly to avoid mixed light/dark frames.

## Test evidence

- **42 Kotlin tests passed:** 25 existing core, 11 HTTP/Eyre transport, 6 palette
  and cache tests. The opt-in legacy JVM write test was skipped for this theme
  pass; it is not counted among the 42.
- **14 web tests passed:** existing hierarchy/recurrence plus five new parser,
  fallback, size-bound and semantic-color tests.
- **Live browser test passed** against fake-wex:8082 and fake-rus:8081. The
  frontend document was routed to the worktree during development; all settings,
  authentication and Tend requests reached the real running ships. Direct
  settings pokes observed Gall acknowledgements, and original entries were
  restored. No reminder writes occurred in this test.
- Light and dark screenshots at widths **320, 640, 768, 1024, 1280, 1440, 1536**,
  each at heights **568 and 960**, with no horizontal viewport overflow. Settings,
  reminder detail, calendar, and bridge-only peer captures are also included.
- **Installed Android live-theme test passed** at Pixel 8's 1080×2400 and narrow
  840×1491 (320×568 dp), API 36. It uses real fake-ship settings and restores them.
- **Existing native reminder flow passed** against the local fixture: login,
  nesting, assignment, calendar/recurrence, editing, rotation, multi-ship switch,
  and offline reading. This is an actual installed-app test, not a repository mock.
- Android debug lint and release vital lint passed. Existing deprecation and
  pinned-version warnings do not prevent the build.

## Screenshots

The captured palettes are conspicuous QA examples, not new defaults. The real
fake ships' list content is visible; screenshots are served only on the tailnet
handoff and retained locally, not committed to Git.

- [Web light settings](artifacts/themes/web/appearance-settings-light.png)
- [Web dark desktop](artifacts/themes/web/dark-1440x960.png)
- [Web narrow dark](artifacts/themes/web/dark-320x568.png)
- [Web light calendar](artifacts/themes/web/calendar-light.png)
- [Android dark](artifacts/themes/android-pixel8/01-dark-home.png)
- [Android live light draft](artifacts/themes/android-pixel8/02-light-draft.png)
- [Android light calendar](artifacts/themes/android-pixel8/03-light-calendar.png)
- [Android local opt-out](artifacts/themes/android-pixel8/04-local-opt-out.png)
- [Android bridge-only peer](artifacts/themes/android-pixel8/05-peer-bridge.png)
- [Android cached theme offline](artifacts/themes/android-pixel8/06-cached-light-offline.png)

All layout captures are under `artifacts/themes/`. Repeatable commands and the
exact published settings contract are in [TEND_THEMES.md](../docs/TEND_THEMES.md).

## Boundaries and remaining acceptance

No Gall `.hoon` source, protocol, theme plugin, or Omarchy plugin was edited or
tested as part of this change. Web source changes are confined to
`desk/app/tend.html`; mobile work is in `mobile/`. Other worktrees remain untouched.
The fake-rus mounted frontend was backed up and committed as a single HTML change
through Dojo, recompiling the unchanged embedded agent. No live-network ship was
deployed. Test-created settings entries were removed; pre-existing values were
restored. Empty settings buckets can remain after entry deletion.

The loopback evaluator rejected an initial attempt to run the commit generator
as an expression. Its documented cancel operation cleared that request, and its
read API was reverified; no agent restart or logic change was needed.

Install on the actual GrapheneOS Pixel 8 and check trusted HTTPS, keyboard/touch
feel, and theme changes from your desktop. API-36 emulator success does not prove
Android-17 compatibility; the previous Android-17 emulator graphics failure is
recorded in [alpha01 QA](QA-ALPHA01.md). There is still no iOS host, background
notification delivery, offline mutation queue, or full desktop feature parity.
