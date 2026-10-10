# Tend repository

Canonical remote: `https://github.com/thelifeandtimes/tend.git`.
Integration branch: `master`. Sourcebox checkout: `~/repos/tend`.

## Source map

- `desk/`: `%tend` Gall agent, marks, libraries, migrations, and browser UI.
- `omarchy-plugin/`: panel, menubar widget, service, and Eyre transport.
- `bin/tend`: standalone command-line client; uses the bundled/installed transport.
- `mobile/`: Kotlin Multiplatform/Compose Android client and native fixtures/tests.
- `tests/`: desktop, browser, CLI, protocol, scale, and packaging regression tests.
- `scripts/`, `packaging/tend/`: validation and deterministic release/install tooling.
- `skills/tend/`: optional agent instructions for the CLI.
- `docs/`, `PRODUCT_ARCHITECTURE_PLAN.md`: component contracts and product decisions.

The former host-manager import and Pals/messenger planning stubs are retired from
the current tree. They remain recoverable in history before the repository
separation (for example commit `1e28444`). Tend has no build dependency on them.
The replacement Omabit runner is maintained separately; this repository neither
installs nor owns its global `omabit` command.

## Worktrees

```sh
git clone https://github.com/thelifeandtimes/tend.git ~/repos/tend
cd ~/repos/tend
git worktree add ../tend-web -b codex/tend-web master
git worktree add ../tend-mobile -b codex/tend-mobile master
```

Choose an unused branch name when one already exists. Keep one active coding
task per checkout. Verify `git remote get-url --push origin` before publishing;
do not rely on GitHub's old-name redirect once the Omabit namespace is reused.

## Packaging and private state

`make dist-tend` builds `dist/tend-VERSION.tar.gz` from the Git-tracked product
source, including native sources and tests. Stage newly added source files before
building a development archive. Build/cache directories, APKs, screenshots,
credentials, and untracked source are not silently swept into releases.
The archive has per-file checksums and an installer at its root. Packaging itself
requires a Git checkout; the extracted sources can run checks and native builds.
Android APKs are built/signed separately as documented in `mobile/README.md`.

The installer owns only `tend` and the existing `io.omabit.tend` plugin. It does
not remove or overwrite an existing `omabit` executable or another plugin.
Legacy connection/cookie paths and `OMABIT_TEND_*` environment overrides remain
compatible. Do not migrate user data as a side effect of renaming source.

Sourcebox migration evidence and preserved local work are recorded in
`TEND_REPOSITORY_MIGRATION.md`.
