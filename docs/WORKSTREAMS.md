# Tend components

Tend is independently buildable and usable without the Omabit ship manager.
All clients use Urbit identity and Gall-authoritative reminder state.

## Gall and browser

`desk/` contains the canonical `%tend` state, collaboration protocol, migrations,
and embedded web UI. Preserve protocol compatibility and existing publisher
`~dister-dozzod-sarlev`. Repository separation does not change agent state or OTA.
See `TEND_PROTOCOL.md`, `TEND_COMPATIBILITY.md`, and `TEND_THEMES.md`.

## Desktop and CLI

`omarchy-plugin/` supplies the native panel, menubar widget, and transport.
`bin/tend` uses that transport from source or the installed plugin. The CLI is
`tend COMMAND`, not a product subcommand of the host manager. Plugin IDs, IPC,
credential locations, and invitation URIs remain stable. See `TEND_INSTALL.md`.

## Native mobile

`mobile/` contains the Android-first Kotlin Multiplatform/Compose client. Sessions,
cached state, and appearance are isolated per ship. Offline reading is supported;
general offline writes and notifications remain deferred. Shared iOS targets are
scaffolding, not a usable iOS app. See `mobile/README.md`, `mobile/DECISIONS.md`, and
`mobile/QA.md` from the repository root.

## External integrations

Theme sync reads the existing `%settings` contract; it does not depend on a local
theme plugin at runtime. Future host-manager discovery/onboarding must use a
documented interface, not files internal to another repository. Pals, messenger,
and ship orchestration are not Tend components.
