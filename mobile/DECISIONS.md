# Native client decisions

Update, 2026-09-29: the `codex/tend-theme` worktree adds **colors and light/dark
mode only** to Android and the browser, using the published `omarchy-urbit-theme`
contract. Default to Follow ship theme; opt out per account/device. Prefer the
full profile, then the stable Talon bridge; cache last-good palettes offline.
Gall logic and both plugins remain frozen. Test directly on fake-wex:8082 and
fake-rus:8081, restoring their settings afterward. No plugin testing requested.
This supersedes the original browser freeze and deferred theme-sync decision
below. See [the implemented contract](../docs/TEND_THEMES.md).

Approved direction, 2026-09-26:

- Android first, iOS later; native Kotlin Multiplatform/Compose interpretation of
  Tend, following Talon's separation of shared client state and platform adapters.
- Primary physical target: Pixel 8, GrapheneOS, Android 17. iPhone 16 Pro/iOS 26.7
  and the M2 MacBook Air/M4 Mac mini will be used for the later Apple loop.
- Separate `codex/tend-mobile` worktree. Do not merge other Omabit worktrees as an
  incidental part of mobile development. Gall, browser, and plugin are frozen.
- Application IDs: `io.omabit.tend` / `io.omabit.tend.dev`.
- Many independently authenticated ships; easy identity switching; no merged
  account data. V1 uses ship URL and +code. Host-manager, Tailscale discovery, and
  QR login are later integrations through documented interfaces.
- Gall authoritative writes; V1 offline reading only. Do not create a silent
  mutation queue. Later offline writes need durable operation IDs, per-list
  dependency ordering, conflict resolution, safe retries, and explicit pending /
  acknowledged / rejected presentation.
- Notification delivery, relay selection, and perfected background sync are
  out of scope now. Later research should cover a reusable multi-app relay,
  Talon's relay approach, presence/attention handoff, and the limits of direct
  Gall-to-APNs/FCM delivery. Device presence is not proof that the human saw an
  event; desktop presence alone must not silently suppress phone delivery.
- Future notification privacy options: Private (“Items are being tended to”),
  Subtle (“~ship <action>”), Full (“~ship <action> <item> in <list>”). No additional
  app lock beyond device unlock is needed for this beta.
- The entire proposed Omarchy palette vocabulary is encoded as semantic color
  tokens. Keep the current Tend interpretation by default. Sync over `%settings`
  will be added once the Talon/theme-manager extension's actual wire contract is
  agreed; do not invent a competing settings schema.
- Direct APK distribution first. App Store/TestFlight preparation and Apple
  developer enrollment can wait for Android alpha feedback. Shared code already
  declares iOS framework targets on macOS, but an iOS host and platform storage /
  network adapters are still required before an iOS build is usable.

## Next user-assisted checks

1. Install the signed alpha on the physical Pixel 8 and sign into an HTTPS Tend
   ship. Physical-device GrapheneOS/Android 17 verification is not replaceable by
   the generic Android emulator. No Google Play Services should be necessary.
2. For live iteration, connect the unlocked Pixel by USB and accept the ADB RSA
   prompt. ADB authorization, Android permission prompts, and a personal ship's
   credentials remain user actions; no credentials need to be posted in chat.
3. Supply a trusted HTTPS fake-ship URL on the tailnet when ready. Release builds
   intentionally do not accept unencrypted LAN HTTP or self-signed certificates.
4. Back up the generated alpha signing key privately before distributing wider.
5. After initial phone feedback, prioritize missing desktop parity versus iOS.
   No notifications decision is needed to continue the Android design loop.
