# Tend native translation — working branch

Status, November 17, 2026: **unqualified native implementation slice**, not a
completed product replacement. `grub/tendy` contains fresh Grubbery source under
`code/`, native preview transports and executable-on-ship contract assertions.
The established Gall clients remain available while native compilation is blocked.
No migration, Gall facade, mixed-release compatibility, publisher change, desk
rename, credential-path change or dependency on Omabit was introduced.

The exact runtime/source candidate is in
[`dev/grubbery/dependencies.json`](../dev/grubbery/dependencies.json). Read the
[development and distribution evidence](TEND_GRUBBERY_DEVELOPMENT.md) before
attempting a deployment. **Offline tests do not prove the Hoon builds.**

## What the application model changes

A nexus gives a directory its application behavior. Tend's nexus loads a durable
state file, starts its writer and clock, and starts HTTP request handlers. A grub
combines stored content with a process: `state.noun` owns both Tend's state and
the process allowed to change it. Fibers are the running handlers. A handler can
wait for a result without preventing the next HTTP request from being admitted.

The framework owns storage, process recovery and permission enforcement. Tend
owns list meaning, owner/member rules, revisions, operation outcomes, schedules
and private additions. Installing source does not grant it every capability.
The shell reviews `weir.json`; the app's external operations remain unavailable
until the user approves those permits.

| Role/data | Location or contract | Authority and lifetime |
| --- | --- | --- |
| App identity | `link.json`, alias `tend` | Resolve the installed instance; never assume its storage path |
| Hosted lists, sections, task trees | `state.noun` product | Host writer validates every edit and list revision |
| Operation results | Same noun, keyed by authenticated ship + operation ID | Saved before response; exact command retained to detect ID reuse |
| Personal preferences, snoozes, pending alerts | Same noun, separate product fields | Owner-only view; never serialized in a member snapshot |
| Host-stored private additions | Same noun, separate actor-keyed map | Not children of a shared list; survive task deletion/revocation |
| Personal-ship annotation app | Opt-in `/tend/annotations` nexus | Independent owner-only instance; no host grant |
| HTTP work | Temporary `requests` subtree | Cleared on reload; committed receipts survive |
| Clock | `clock.sig` | Recomputes work from durable deadlines and notification IDs |

There is one hosted-state writer in this slice. That is a deliberate transaction
boundary, not an assumption that separate grub writes commit together. Directory
grants include descendants. Neither `state.noun` nor a parent directory receives
a foreign peek grant. Public admission allows a poke to one exact writer file
and a read of protocol discovery only. It is not permission to edit a list.

The writer derives a peer's identity from the framework's transport origin. Local
HTTP requests must come from request fibers made by the authenticated dispatcher.
JSON cannot supply a trusted actor. Unknown peers cannot fill the receipt or
annotation stores. Members may edit only lists whose canonical member map admits
them. List deletion and granting/revoking members require the host owner.

## Implemented source slice and explicit limits

`code/lib/tend-core.hoon` extracts Tend's current deterministic rules from this
repository's own agent: list/section/task CRUD, revision checks, subtree completion,
placement/rank normalization, batch edits, schedules, recurrence and local
preferences. It has no Gall cards, watches or peer sends. `tend-clock.hoon` contains
the pure deadline/notification rules. `tend-native.hoon` adds the native authority,
actor-scoped receipt and private-addition boundaries. `app.hoon` is the real
`on-load`/`on-file` nexus and effects adapter.

The source retains current product data molds, including unused replica and
transfer fields, to avoid rewriting deterministic rules during the first slice.
There is one fresh native save schema; no old save union or load migration is
accepted. Existing `%tend-action-1`, `%tend-update-1`, `%tend-peer-1`, their wire
names and installed paths are unchanged. New internal marks are
`/tend/request-1` and `/tend/wake-1`; native JSON identifies `tend-native-1`, and
native snapshots require protocol **3**. No old versioned mark acquired a different
meaning.

The preview supports explicit owner grants/revocations. This is not yet the
invitation/acceptance product flow. The older invite/accept/remove action forms
are explicitly rejected by the native core with `use-native-membership`.
`can-invite` is retained as policy data; delegated invitation behavior is pending.

The native web preview supports creating/deleting lists, adding/completing tasks,
private additions and owner grants. Its recurrence editor, full desktop layout,
activity UI, notifications and collaboration UI are pending. It deliberately
refuses recurring completion without a recurrence hint editor. It retains an
uncertain command by ship and app route in browser storage and only reconciles
its original receipt; it does not automatically resubmit it.

The CLI exposes `tend native connect URL`, `state`, `private`, `whoami`,
`receipt OPERATION` and `command JSON`. It uses the existing credential/config
paths and submits once. The desktop's Python transport exposes the same explicit
native functions. The desktop QML service and normal CLI product commands still
use their existing transport; they have not been switched over.

Android has a compiled/tested `NativeEyre` preview transport with native login,
strict snapshot validation, polling, command and receipt calls. It keeps the
existing account types and session storage. The Compose repository/UI has not
been wired to it. No native Grubbery claim follows from an Android APK build.

## Reads, watches and actions

All HTTP routes are under `/apps/tend/api/v1/`, authenticated to the ship owner.

| Read or action | Response/behavior |
| --- | --- |
| `GET whoami` | Ship identity |
| `GET state` | `snapshot` with protocol 3; visible lists and owner-private settings |
| `GET private` | Only the caller's private additions, reference and availability |
| `GET receipt/ID` | Caller-scoped outcome, or JSON null when no receipt exists |
| `POST command` | Matching durable receipt: protocol, operation ID, committed/rejected, reason |
| `GET self-test` | Runs the native Hoon assertions in memory; does not edit saved state |
| Watch | Authenticated polling in this preview; no claim of native live keep delivery |

Peers use `/tend/request-1` to the resolved writer, with the typed `snapshot`,
`private`, `receipt` or `command` envelope. Replies target the authenticated
sender road rather than a callback supplied in JSON. The shell's alias and grants
must be qualified on a real instance before this is considered operational.
A peer snapshot filters visible lists and uses empty/default private settings.
Receipts contain an outcome, not a saved snapshot that could disclose content
after access revocation. There is no guest HTTP route and no arbitrary code/action
execution endpoint.

An edit command wraps one existing product action:

```json
{"kind":"edit","action":{"create-list":{"operation-id":"example-1","title":"Chores"}}}
```

An explicit owner grant uses the current list revision:

```json
{"kind":"grant","operation-id":"grant-1","list-id":1,"base-revision":1,"ship":"~nec","can-invite":false}
```

HTTP success, a poke acknowledgement, version equality and a copied source tree
are not operation success. Clients require a matching `tend-native-1` receipt
with a canonical outcome. A lost reply leaves the original ID uncertain. Reading
its receipt never submits it again. Reusing an ID with a different command is
rejected. Receipt capacity is 10,000: admission fails when full instead of silently
evicting results and later applying a forgotten operation twice. Receipt
compaction/acknowledged retention remains a separate design task.

## Private additions and composition experiment

A reference identifies a ship, app alias and logical resource, for example
`{"host":"~zod","app":"tend","resource":["lists","1","tasks","2"]}`.
It does not name the app's internal grub path. List and task IDs are not reused
inside the lifetime of a fresh instance. Replacing an entire fresh instance can
still collide; a persistent instance identity/reference epoch is required before
these references are suitable for cross-instance archival use.

An addition has its own actor, key, revision, title and completion state. Deleting
the shared task, deleting its list, or revoking list access does not delete the
addition. A local reference then reads `unavailable`. The former member may still
edit or remove an existing private addition. They cannot add new host storage or
edit an inaccessible shared list. A returning reference may become available
again. Reference resolution is a read; it never re-grants access.

Two storage experiments are present:

- Built-in host storage: Tend manages actor-private additions outside any shared
  subtree. Other participants cannot peek them. The host owner can inspect the
  saved noun, so this is **not confidential from the host**.
- Independent personal-ship storage: instantiate `/tend/annotations` on the user's
  ship. It has no peer admission group or host grant and only accepts annotation
  mutations. Its owner can store a reference without asking the target host to
  store or authorize the private item. Remote targets are `unresolved` until an
  explicitly permitted resolver exists. Missing permission is not proof of deletion.

Visual nesting is a UI choice: a personal item can appear under a shared task
while being stored on a different ship. Detached items need a visible home so
a disappearing shared row cannot hide surviving personal work. The preview has
a separate private-items section; a full inline hierarchy is pending.

The same reference shape can identify a Lattice page. A Tend-task-on-Lattice-page
and a Lattice-page-on-Tend-task should each be a separately owned attachment.
The source app retains authority. Required future contracts are: discover a
resource's read schema; render a supported live view; watch or poll only with
read permission; enumerate an explicit action allowlist; submit an action to the
authoritative writer with a receipt; represent denial/offline/deletion distinctly.
These cross-app live views/actions are **design contracts, not implemented
interoperability**. An unchanged frontend does not render arbitrary extensions.

## Cross-list transaction audit

The previous Gall implementation already has a durable cross-host coordinator,
source reservation, destination import receipt, finalization acknowledgement,
retry deadlines and restoration path. See `docs/TEND_PROTOCOL.md` and the
`take-transfer-*` arms in the existing agent. A copy/delete shortcut loses those
properties.

The native pure core preserves **same-host** subtree movement as one state
transition: both list revisions, allocated destination IDs, parent references,
section detachment, assignment validation, schedules and personal snoozes change
together. The native writer saves both lists and the operation receipt in one
`replace`. Source references become unavailable when IDs change; private
additions survive rather than silently following a newly allocated task.

Cross-host native transfers are not implemented. A missing hosted destination
fails with `cross-host-move`. Do not route such an operation to two independent
writers and call it atomic. Before enabling it, port the durable saga with
actor-bound payloads/import receipts, source reservations, explicit recovery and
reload tests at every phase. Only then consider a list-per-grub split.

## Remaining acceptance

First compile every native source on the pinned framework/runtime/pill and run
`GET self-test`; fix real compiler/runtime findings. Then implement/qualify remote
alias discovery, member read/write round trips, invitation consent, authoritative
activity, watch contracts and notification delivery. Complete the desktop, full
web and Android UI transport transition, client pending-operation persistence and
cross-host transfer recovery. Qualify app distribution/update/restart as described
in the evidence report. Public-network remote shipping remains a separate test.
