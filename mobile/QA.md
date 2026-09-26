# Android alpha QA — 2026-09-26

## Deliverable

`artifacts/tend-0.1.0-alpha01.apk` is the signed, optimized Android release.
Application ID: `io.omabit.tend`; version code: 1; minimum API: 26; target API: 36.
APK size is approximately 1.8 MiB. ARM64, ARM32, x86, and x86_64 are packaged.

SHA-256:

```text
8ccecb96c59f27ce4f14904b40a2c05a6b38cc0afb3eb3fb62ee09144a9de5dc
```

Signing certificate SHA-256 (not a private key):

```text
ecbf2290150711ec6e3d138392fb1115b4dd619b4a857de056c965c2502b4dc5
```

Verified with `apksigner verify --verbose --print-certs` (v2 signing) and
`zipalign -c -P 16 -v 4`. The final release installed and cold-launched successfully
on the Pixel 8 API 36 emulator; `am start -W -S` reported 306 ms on this machine.
That single emulator measurement is not a physical-device performance benchmark.

## Verification performed

- 25 core tests and 9 HTTP/Eyre transport tests passed. Coverage includes 10,000
  reminders, parent-first nesting, ancestor context, cycles/orphans, stale list
  and preference revisions, dates, DST and recurrence rules, URL validation,
  code/cookie handling, incompatible protocols, bounded responses, ack/nack
  distinction, and unsafe-link rejection.
- One opt-in JVM integration test passed against the existing running `~wex`
  and `~rus` fake ships: login, subscription, list/section/reminder creation,
  assignment, scheduling, metadata, nesting, recurring completion, invitation
  acceptance, and peer-to-owner canonical confirmation. Its isolated QA list
  was deleted; pre-existing lists were not edited.
- An additional **installed Android app** live-ship test passed against `~wex`:
  real login UI, Android networking and storage, canonical creation/completion,
  and cleanup. No real code is embedded in the APK, source, or screenshots.
- Android lint passed with 0 errors and 12 warnings (pinned-version update
  suggestions and a SharedPreferences KTX suggestion). Release vital lint passed.
- The final signed release's login screen was visually inspected. End-to-end
  native write tests use the development variant because local fake ships use
  HTTP; production HTTPS and the physical GrapheneOS device still need the
  user-assisted acceptance check below.

The repeatable native fixture test exercises actual Compose controls and the
packaged app, not a mocked repository: URL/+code login, nested items, reminder
draft, assignee dropdown, calendar, recurrence, snapshot arrival and activity
recreation while editing, save, recurring completion, notes editing, isolated
account switching, and offline read-only presentation.

| Emulator layout | Text scale | Full native flow |
| --- | --- | --- |
| Pixel 8, 1080 × 2400 px / approximately 411 × 914 dp | 100% | Passed |
| Narrow, 840 × 1491 px / 320 × 568 dp | 100% | Passed |
| Landscape, 2400 × 1080 px / approximately 914 × 411 dp | 100% | Passed |
| Pixel 8, 1080 × 2400 px | 150% | Passed |

All runs used 420 dpi and Android 16/API 36. Short layouts deliberately scroll
the editor/calendar body while keeping confirmation actions inside the viewport.
An additional cold-process test stopped the fixture, force-stopped the client,
and relaunched it: encrypted saved identity and Room content restored offline,
with mutation controls disabled.

## Screenshots

These images use disposable fixture content, not personal ship content. Image
artifacts are retained locally with the APK, not committed to source control.

- [Signed release login](artifacts/qa-pixel8/00-release-login.png)
- [All reminders and nested children](artifacts/qa-pixel8/02-all-nesting.png)
- [New reminder with assignee, date, and recurrence](artifacts/qa-pixel8/04-new-reminder.png)
- [Calendar on a narrow screen](artifacts/qa-320x568/03a-calendar.png)
- [Landscape calendar](artifacts/qa-landscape/03a-calendar.png)
- [Calendar at 150% text size](artifacts/qa-font150/03a-calendar.png)
- [Independent ship accounts](artifacts/qa-pixel8/06-separate-accounts.png)
- [Offline cache after cold process restart](artifacts/screenshots/offline-cold-start.png)

Each `artifacts/qa-*` folder also contains the complete eight-screen test flow.
The narrow screenshots precede the final grammatical “Every day” label polish;
the later Pixel 8, landscape, and large-text captures include it.

## Android 17 limitation

Both installed Android 17 emulator images (API 37.0 and API 37.2 QPR2) repeatedly
crashed in the **guest system's SurfaceFlinger**, before a reliable app test:

```text
RegionSampling / mapper.ranchu.so:
Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma
```

Alternate graphics modes and GLDMA settings did not resolve it. This is not
evidence of a Tend crash, but it also does not establish Android 17 compatibility.
The unreliable images are not part of the retained daily development setup.

## Remaining acceptance checks / known alpha boundaries

1. Install the signed APK on the actual GrapheneOS Pixel 8, allow Network access,
   and sign into an existing `%tend` protocol-2 ship over trusted HTTPS. No Google
   Play Services are required. Verify on both Wi-Fi/tailnet and normal reconnect.
2. Check physical keyboard/IME behavior, TalkBack, touch feel, and battery use.
   Emulator layout checks are not an accessibility or battery certification.
3. No notifications, background refresh, offline mutations, cross-list transfer,
   bulk editing, drag ordering, advanced recurrence-rule authoring, or iOS host
   are delivered in this alpha. Existing advanced recurrence rules are preserved.
4. Theme tokens match the proposed Omarchy vocabulary, but automatic `%settings`
   theme synchronization waits for that extension's agreed wire contract.
5. A remote write whose canonical streamed result is missed during disconnect
   can remain uncertain when the replica has no durable receipt. The pending
   journal is preserved and the operation is not automatically re-submitted.

## Repository boundary

All implementation and documentation changes are under `mobile/`, on
`codex/tend-mobile`, based on frozen Tend commit `5e431a5`. No Gall source,
browser, plugin, or existing root tests changed. No ship deployment/restart was
performed. Other worktrees and the primary checkout's uncommitted files remain
untouched. Build/test commands and signing-key backup instructions are in
[README.md](README.md).
