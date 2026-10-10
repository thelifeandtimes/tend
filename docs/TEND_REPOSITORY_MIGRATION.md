# Tend repository separation — 2026-10-09

## Result and boundaries

The original GitHub repository was renamed in place to
`https://github.com/thelifeandtimes/tend` (repository node ID
`R_kgDOUrzoJg`), preserving history and GitHub metadata. The integration branch
is `master`; migration work is on `codex/tend-repository-migration`.
The canonical sourcebox checkout is `/home/thelifeandtimes/repos/tend`.

The replacement GitHub `omabit` repository was **not** created. The sourceport
agent can claim that namespace for the separate ship-running project. Do not
rely on the old-name redirect or push the runner into Tend's renamed repository.
Both independent sourcebox clones, including all linked worktrees, now use:

```text
origin  https://github.com/thelifeandtimes/tend.git (fetch)
origin  https://github.com/thelifeandtimes/tend.git (push)
```

No sourceport checkout, pier, running ship, runner service, installed plugin,
login credential, signing identity, or user data was changed. Gall, browser,
plugin, and mobile implementation files are unchanged from the latest theme
checkpoint `1e28444`; this migration changes repository/tooling boundaries.

## Complete local source included

GitHub was at `5e431a5`. The migration retains its ancestry and adds the existing
local mobile commits (`cbdfbcf`, `596d3b2`, `a447dbf`) and theme commits
(`82103cc`, `2323e6f`, `97495a5`, `1e28444`) before the migration commits:

- `685b3b6`: standalone CLI and complete source packaging.
- `3fd5188`: Tend-only repository documentation and retirement of unrelated imports.

The unused `apps/urbit-host`, Pals, and messenger placeholders were removed only
from the new current tree after checking dependencies. Their source remains in
history and the old development checkouts. The old import is not the newer
sourceport runner and must not be substituted for it.

The release now includes the desk/dependencies, webapp, plugin/transport, CLI,
native mobile source, fixtures, tests, docs, skills, and build/install tooling.
It includes Git-tracked source only; APKs, screenshots, Gradle outputs, local SDK
configuration, and signing material are not published in the source tarball.

## CLI and deployment compatibility

- Run `tend COMMAND` or `tend --json COMMAND`; source executable is `bin/tend`.
- Archive/install root is `tend-VERSION`; installation owns `~/.local/bin/tend`.
- The installer does not remove, replace, or back up someone else's `omabit`
  command. Fresh and forced installs were tested alongside a separate command
  and plugin fixture.
- `%tend`, publisher `~dister-dozzod-sarlev`, wire/mark names, `io.omabit.tend`,
  mobile IDs/signing identity, IPC, `omabit://tend` invitations, existing settings
  and credential paths, and `OMABIT_TEND_*` overrides remain stable.
- QML invokes its transport directly; it has no old CLI invocation to rewrite.
- The bundled Tend skill now uses the standalone CLI; only command examples and
  naming were changed, preserving its existing mutation/credential boundaries.

The existing sourcebox `~/.local/bin/omabit` is an old installed Tend CLI. It was
left untouched because this is a source migration, not a live deployment. Before
installing the new Omabit runner, retire/back up that legacy executable deliberately
and update any personal scripts/keybindings that call `omabit tend`.
If compatibility is desired, the **Omabit** CLI should implement an optional
`omabit tend …` forwarding adapter to `tend …`; Tend must not install a competing
global `omabit` wrapper. `--json` needs forwarding as a global Tend option.

## Preserved sourcebox state and remaining old paths

The idle clone formerly at `~/repos/omabit` was renamed to `~/repos/tend`.
No active process, tmux pane, service configuration, or local executable referring
to that old clone path was found. Source-relative scripts continue to work.

These historical paths remain deliberately, rather than breaking active editor
state or discarding private artifacts:

- `/home/thelifeandtimes/Documents/ChatGPT/omabit`: old primary checkout and this
  task's saved project path; its unrelated modified README and untracked
  `docs/EXECUTION_PLAN.md` remain unchanged.
- Sibling `omabit-tend-mobile` and `omabit-tend-theme` worktrees: clean source
  checkpoints, ignored build outputs, signed alpha APKs, and screenshots retained.
- `scratch-omabit`: existing compatibility symlink, left intact.
- An already-missing `/tmp/omabit-tend-full-loop` worktree registration remains
  prunable; its committed branch is preserved. No destructive prune was needed.

Use `~/repos/tend` for future work and register/open it as the Tend project in
your editor. These retained sourcebox paths do not keep ownership of the GitHub
Omabit namespace. Historical checkouts/docs and old ignored release artifacts may
still contain the old name; active remotes and the new product's clone/build links
do not. The stable IDs/URI/config paths above intentionally retain `omabit`.

A private recovery checkpoint is under
`~/.local/share/tend-migration/2026-10-09/`: an all-ref Git bundle (verified),
the primary README, untracked execution plan, and binary-capable working diff.
Original ignored files and signing material remain in their original locations;
they were not swept into Git. The old canonical clone's desktop archives are
preserved there as `old-dist/`, separate from newly built Tend packages.
The latest alpha artifacts are also copied locally into the new checkout's
ignored `mobile/artifacts/`, with originals retained.

## Validation

- `make check-tend`: passed; plugin validation/QML lint, 125 Python tests
  (including 39 CLI tests), and 45 JavaScript tests.
- `make dist-tend`: complete deterministic source archive and portable checksum.
- `make check-tend-release`: passed; two archives byte-identical, checksum
  verification, required mobile/desktop sources, no generated/private mobile
  files, all 170 Python/JavaScript tests from the unpacked archive, isolated
  install/update/refusal/backup/desk-overlay checks, and Omabit coexistence.
- Skill validator: passed using system Python/PyYAML.
- Mobile `:shared:jvmTest :androidApp:lintDebug :androidApp:assembleDebug`:
  passed from the migration checkout; 42 Kotlin tests, one opt-in live-ship test
  skipped, zero lint errors (12 existing warnings), debug APK built.
- No live-ship tests or deployment were run. No new Android/iOS physical-device
  compatibility claim is made. Previous mobile acceptance limits still apply.

No force-push, history rewrite, or GitHub release publication is part of this
migration. New source packages are local build artifacts, not published releases.
