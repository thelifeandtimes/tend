# Tend contributor instructions

Read `README.md`, `docs/REPOSITORY.md`, and `docs/WORKSTREAMS.md` before
repository-wide changes. Read the nearest component documentation and any
more-specific `AGENTS.md` before changing that component.

## Boundaries

This repository owns the complete Tend application: the `%tend` desk and web
client, Omarchy plugin/transport, `tend` CLI, native mobile client, tests, release
tooling, documentation, and agent skill. The Omabit ship manager is a separate
project; do not add an implicit runtime or build dependency on it.

Keep `%tend`, its publisher, wire/mark names, plugin/mobile ID `io.omabit.tend`,
invitation URI scheme, and existing settings/credential paths stable. Changing
persistent identities requires a separate, explicitly approved migration.

Never commit piers, keys, codes, cookies, runtime state, or signing identities.
Repository testing does not authorize live-ship mutations, service changes,
plugin deployment, or shell restarts. Prefer offline tests and isolated installs.
The native client has additional instructions in `mobile/AGENTS.md`.

## Concurrent work

Use a separate Git worktree and `codex/` branch per active coding task. The
canonical sourcebox checkout is `~/repos/tend`; historical development worktrees
may remain at older paths until their sessions and artifacts are retired.
Preserve unrelated working changes. Do not rewrite shared history or force-push.

## Validation

Start with the narrowest applicable tests, then:

```sh
make check-tend
make dist-tend
make check-tend-release
# Requires the Android/JDK toolchain described in mobile/README.md:
make check-mobile
```

`make check` aliases the desktop/web/CLI checks. `make check-all` additionally
checks/builds mobile. Real-ship migration/collaboration checks remain opt-in.
