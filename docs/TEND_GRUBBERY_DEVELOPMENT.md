# Native development loop and distribution evidence

November 17, 2026, sourcebox. Worktree bound by T3; branch `grub/tendy`. No extra
checkout, PR, merge, service replacement, shell restart or live/owned-ship change.
Grubbin is owned by the parent; this task did not edit it. Its reusable evidence
is this report and [the app-model notes](TEND_GRUBBERY.md).

## Authoritative source selection

Fresh GitHub reads returned:

- Lattice main [`5f081b4450acbbffa914c6e318e8dde784841341`](https://github.com/nisfeb/lattice/commit/5f081b4450acbbffa914c6e318e8dde784841341).
  Its actual [CI workflow](https://github.com/nisfeb/lattice/blob/5f081b4450acbbffa914c6e318e8dde784841341/.github/workflows/ci.yml)
  clones **nisfeb/grubbery, dist/single-release**, downloads Vere `vere-v4.6`
  and the v4.6 bootstrap pill. Its Grubbery cache key is a moving ref, not our lock.
- The selected Grubbery head was
  [`7f7701d546ce115eea5f37f66465b362cb121774`](https://github.com/nisfeb/grubbery/commit/7f7701d546ce115eea5f37f66465b362cb121774).
  **No drift** from the user-supplied November 17 baseline. The pinned GitHub
  source archive SHA-256 and exact CI bytes hash are in the dependency lock.
- The local `/home/thelifeandtimes/.local/src/dnd/grubbery` reference remains the
  older `ff2b66b`; it was read for orientation, never used as the selected baseline.
- Read Dungeons' `dev/dependencies.json`, research/setup docs and implemented app
  nexus. Its older qualified framework is not evidence for the newer source.
- Read current Lattice app source and
  [releasing guide](https://github.com/nisfeb/lattice/blob/5f081b4450acbbffa914c6e318e8dde784841341/docs/releasing.md)
  for single-writer/request-fiber structure, permits and app-desk distribution.
  Lattice source was studied, not copied into Tend. New rules derive from Tend's
  own source; framework I/O uses Grubbery's API.

[`dev/grubbery/dependencies.json`](../dev/grubbery/dependencies.json) pins the
source candidate plus Vere `4.6-5a7614f`, OCI manifest
`47fbcf0710ded939931ce3c6408864cee5e8848c5765da0b51a90ca0b1ab1afd`, and the
217,421,172-byte generation-qualified pill with hash
`f8b08c53dd6d0aa9c4f7654f0a7bcd11e94089222888a3c99c0a10eedcbb377b`.
These runtime/pill pins also match Omabit's actual project-runtime source pins.
**This combination is a reproducible candidate, not a qualified Tend baseline.**
Never advance it silently after successful qualification; record source drift and
qualify the new combination separately.

## Supported Omabit workflow actually attempted

Read the newer `omabit-dev-loop` project creation, collector and operator project
docs, and the corresponding `omabit-platform-overhaul` reference. No source in
either checkout was modified. No installed tool, plugin or service was replaced.

The `urbit-collector` launcher is not on PATH. The installed plugin's bootstrap
manifest identifies Omabit 0.4.0, source
`a9623329a538ebf83d63365053d2d1011ade091e`. The verified cached collector executable
has SHA-256 `781decb3285ede2cf7bec317041d64599690bf3cb416f0c7b722846c41ca217b`,
matching that manifest. Calling this exact executable avoids modifying the
launcher's binary cache merely to read help.

Actual installed `help projects`, `project-plan --help`, `project-create --help`,
`project-operate --help`, `enroll --help`, `omabit --help`, `desk-settings --help`,
`desk-plan-create --help` and source-staging/install help were inspected. One
expected mismatch is useful: the installed guided create accepts 0–16 fresh
members, while an older operator reference says 2–16. Installed help and fresh
admission determine the executable contract.

Runner Hello identified an operator-access `development-managed` runner and
advertised project reservation, dependency preparation, initial launch/readiness,
member stop/exit/retire/resume and recovery capabilities. We enrolled it in a
**task-local** collector directory, not the user's existing enrollment registry.
A direct-socket guided create was refused before effect because it requires a
named identity-pinned enrollment; the enrolled invocation was used next.

The full plan, digest, create progress and saved operation history were preserved
privately during the run. No keys, codes, cookies, piers or runtime state entered
Git. Public plan/receipt identities are summarized below.

| Selection | Recorded value |
| --- | --- |
| Project | `project-892dab43f15fc96c8c938424a211c139` |
| Label | `tend-grub-20261117` |
| Review digest | `663c146488ee3a1c36bd06449ecb24239eaf6ab754eb3d459f7448a6a4a55157` |
| Original create request | `tend-grub-20261117-create-1` |
| Registration receipt | `op-eca8edf0eb97e1db757548806ed546f4` |
| Topology | Fresh private bridge/shared namespace, three trusted fake galaxies |
| Host endpoints | Loopback-only ports 19381, 19382, 19383 |
| Disk admission | 409 GiB free before launch; three members only; piers retained after stop |

| Role | Instance | Identity | Launch receipt | Initial readiness receipt |
| --- | --- | --- | --- | --- |
| Intended distributor | `tend-grub-zod` | `~zod` | `op-b87c9c33bbd4ccb136991294be1d39c2` | `op-a87e34f16fcc81e989285a0e1adbcdf7` |
| Intended receiver A | `tend-grub-nec` | `~nec` | `op-ff9362ab8044c95c463ec2ba8606547e` | `op-d6f79cd3ef35f2147fa61dd984cd05fc` |
| Intended receiver B | `tend-grub-bud` | `~bud` | `op-de6380b969c9ce67ca5acf228c3a51ec` | `op-b36094351fa9bea9dc32f040780f74cb` |

Guided creation reached **ready**, and all three identity/readiness observations
succeeded. This demonstrates fresh project creation, not application health.
No unrelated project network was reused and no Docker commands were used.

## Exact app-development blocker

Against the new distributor, both actual public read-only commands
`omabit desk-settings ... --desk grubbery` and
`omabit desk-plan-create ... --desk grubbery` returned:

> runner lacks app-development-policy-v2; desk creation, commit and publication unavailable

The installed collector's project lifecycle capabilities are sufficient to create
the network, but that does not imply a source-staging/commit capability. The
current public workflow cannot bootstrap the pinned Grubbery source on these
members. There is no supported Grubbery instance onto which to upload Tend.

No new services/plugins were installed, no Setup policies were bypassed, no
containers were accessed with hand-written Docker, and no unrelated running ship
was borrowed to compile this source. The user explicitly authorized finishing
offline if this gap occurred. Resuming live qualification requires an Omabit
release whose public desk workflow advertises the missing capability and whose
exact supported setup/source-staging flow is available. That is an external
prerequisite, not a Tend build/runtime dependency.

The three fresh members were then stopped through public `project-operate`, using
their exact readiness receipts. The runner serializes project operations: the
first attempt to stop the next member while ~zod's stop was queued was refused
with `project_operation_pending`. No effect occurred for that refusal. The same
original request ID was submitted only after ~zod's saved stop had succeeded.
Piers and project identities were retained; there was no destructive cleanup.
A fresh public runner snapshot at **2026-11-17T22:35:45Z** reported
`desired_state: stopped` and `observed_state: stopped` for all three members
(`health: unknown`). Their public stop receipts succeeded:

| Member | Stop receipt |
| --- | --- |
| `tend-grub-zod` | `op-caa60538a0ad40afa348b28e7bb194ae` |
| `tend-grub-nec` | `op-50463ada4a35bb8d9267bfc7e4df5919` |
| `tend-grub-bud` | `op-925d38ba10365998365b39f1efdba599` |

The separate formal exit observations for ~zod
(`op-b23ecf159d6fd00b1ffedc84c0fd102f`, request
`tend-grub-20261117-exit-zod`) and ~nec
(`op-cd35df6f2297eddb68062b48e488f08e`, request
`tend-grub-20261117-exit-nec`) were recorded **interrupted**. The public status
response supplied no further reason. They were not retried, adopted, retired or
replaced, and no third exit observation was attempted. These exact receipts need
reconciliation before a future lifecycle continuation. Snapshot-observed stopped
state is not a successful formal exit qualification. The project network/anchor
and retained piers were not destroyed.

## Distribution acceptance matrix

**Every app scenario below remains NOT RUN** because framework bootstrap was
blocked. No app source was delivered even to the distributor; no receiver got
local code. There is no resolved Tend alias/instance, app build-health result,
permit approval, task operation receipt or reload sentinel on these ships.

| Scenario | Required evidence before acceptance |
| --- | --- |
| Initial install/follow on two receivers | Source provenance, real native app-desk source/alias, pending permit request, app has not started with unapproved capabilities |
| Approved startup | Exact approved roads/groups, every code build healthy, instance has no bang, authenticated route answers |
| Task round trip | Create/update/complete result from canonical writer with matching operation ID; receiver reads the resulting task |
| Published update | Only distributor gets edited local code, code/version changes, receiver follows native desk source; old sentinel remains and a new task operation succeeds |
| Reload/restart | Exact lifecycle receipts, resolved alias and route recover, both sentinel and original receipt survive |
| Missing grant/unauthorized caller | Denial from actual authority boundary, no private bytes, no list change |
| Source offline/recovery | Supported distributor stop/resume, receiver retains usable installed data; follow resumes with provenance, build health and successful operation |

Native app distribution is distinct from the `%grubbery` framework Clay desk.
After bootstrap, local Tend `code/` goes only to the distributor's native app
source. Receiver desks follow its **installed desk/code and root version** through
Grubbery; they are not local source overlays and do not follow an independently
shared development tree. The distributor's installed desk must first follow/sync
the development source and republish its release. The parent session reports that
Grubbin's independent forward-test confirmed the current `/desks/add` and
`/desks/share` contracts and this distinction. That is companion contract evidence,
not a Tend install/distribution result. `code/version.json` is an opaque app release tag and must
change with each release. Version equality alone proves none of compilation,
instance startup, permits, operation outcome or data survival. Process captures
must record `procs` total/truncated completeness; a partial page cannot prove all
processes healthy. When the distributor is offline, preserve that unavailable
source observation and separately capture receiver state/operations. Grubbin's
capture helper supports this distinction; no Tend capture was run here. Preserve
the source/version metadata and full acceptance observations, not only HTTP 200s.

The companion procedure read for this handoff is
`/tmp/grubbin-workshop/docs/RELEASE_AND_RESTART.md`, being finalized in the private
[Grubbin repository](https://github.com/thelifeandtimes/grubbin). It separates
local source preparation, distributor installed-desk republishing, receiver
following, nexus reload and whole-ship replacement. Its release helper writes a
local version/manifest only. Nothing in this Tend session upgrades services or
uses that helper as runtime authority. The source gap and interrupted lifecycle
receipts above remain prerequisites for the proposed exercise.

Same-host fake networking would establish logical ship distribution only. A later
public-network test needs separately authorized real remote ships, independent
hosting endpoints, normal Ames connectivity and real identities. Re-run the
source/permit/action/reload matrix there; do not extrapolate fakes into public
remote hosting or public-network reliability.

## Offline verification

- Seven native Python transport tests: matching receipt, rejection, incompatible
  snapshots, missing IDs, route confinement and lost replies without mutation
  replay. They mock the HTTP boundary; they do not emulate or compile Grubbery.
- Three native preview web tests pass in a Node VM: form input handling, logical
  task references and retained uncertain commands. No browser/device render claim.
- Three added Kotlin native transport tests pass as part of the shared JVM suite.
  That suite reports 46 tests, one opt-in live test skipped, zero failures/errors.
- `make check-tend` passes with fixture loopback access enabled. Its 132 Python
  tests and existing JavaScript suites exercise established product behavior;
  their pass is not native Hoon execution.
- `make check-mobile` passes: JVM tests, Android debug lint and debug APK assembly.
  No APK was installed and no emulator/physical device was used for this branch.
- `check-tend-native` checks local imports, bill source closure, lock shape and
  JavaScript syntax. Its output explicitly says Hoon compilation was not performed.
- Native Hoon assertions are authored in `code/lib/tend-native-tests.hoon`: CRUD,
  actor authority, deduplication/ID reuse, local transfer, timer re-entry, private
  additions after revocation/deletion and saved-noun recovery. **Not executed.**
- `make dist-tend` and `make check-tend-release` pass: deterministic archive,
  checksums, extracted-source tests and isolated installer checks. Including native
  source in that archive does not make the native app deployable/qualified.

## Reusable findings for Grubbin

1. A capability for fake-network lifecycle says nothing about source installation.
   Discover actual installed help, runner Hello and the app-development capability
   before budgeting a multi-ship app test.
2. Keep a plan digest, registration request and exact operation/readiness receipts.
   Queued operations must settle before the next serialized lifecycle action;
   preserve IDs after refusals or uncertainty.
3. A single saved writer noun is a useful initial transaction boundary for list
   operations. Per-list fibers introduce coordination work; they do not provide
   multi-grub transactions for free.
4. Alias discovery, permission approval, app code sync, build health, instance
   health and canonical actions are separate stages. Record each one.
5. Private visual children need independent storage and independent lifetime.
   Host-private is not private from the host. Personal-ship storage avoids a host
   storage grant; viewing/acting on its shared target still needs explicit rights.
6. An attachment is a stable reference plus read/watch/action contracts and a UI
   renderer. A directory path or generic frontend alone is not app composition.
7. A loader must cover each durable subtree/file. Request-fiber recovery is not
   operation recovery; operation receipts must survive outside ephemeral requests.
