# Tend mobile

Read `README.md`, `DECISIONS.md`, and `QA.md` before changing this client.

- Keep this workstream in its own `codex/tend-mobile` worktree. Do not merge other
  Omabit workstreams incidentally.
- Gall (`desk/`), the browser client, and `omarchy-plugin/` are frozen. Client
  limitations are not authorization to change the server protocol.
- Shared protocol/state/UI belong in `shared/`; Android storage, lifecycle, and
  network adapters belong in `androidApp/`. Keep notifications out of this alpha.
- Gall is authoritative. HTTP acceptance or an Eyre poke ack is not proof that a
  remote host committed a write. Preserve uncertain operations; never retry a
  mutation under a new ID automatically.
- Every session, cache, pending operation, and UI navigation scope is account
  specific. Never log credentials or commit signing identities.
- Use `./gradlew :shared:jvmTest` first, then `:androidApp:lintDebug` and the
  documented native tests. Live-ship tests are opt-in, create isolated data, and
  must clean up only their own lists. Never deploy Gall as a mobile test step.
- Keep build outputs and screenshots in ignored `artifacts/`, with evidence
  summarized in `QA.md`. A successful debug test is not a release/physical-device
  compatibility claim.
