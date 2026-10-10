---
name: tend
description: Read and manage Tend reminders, lists, schedules, recurrence, sections, assignments, and shared-list invitations through the local Tend CLI. Use when a user asks an agent to inspect or change their Tend data; do not use for unrelated todo applications.
---

# Tend

Use the dependency-free `tend` CLI. Prefer machine-readable output:

```bash
tend --json status
tend --json lists
tend --json list
```

Before changing data, read the relevant list and reminder so you have current
IDs and revisions. Lists may be addressed by exact title or local numeric ID.
Reminder and section arguments are numeric IDs.

## Read

```bash
tend --json today
tend --json show "Personal" 42
tend --json access "Shared chores"
tend --json sections "Shared chores"
tend --json invitations
```

`show` includes the reminder's `ancestors` and recursive `descendants`.
`access` identifies the host, connection state, accepted members and pending
invitees. The data model has an owner and editor members; `can-invite` is an
extra member capability, not a separate role.

## Mutate

Use the smallest command matching the request:

```bash
tend --json add "Buy oats" --list Personal --tag groceries
tend --json edit Personal 42 --notes "Get steel cut" --priority high --assignee ~sampel-palnet
tend --json flag Personal 42
tend --json complete 1 42
tend --json move-list Personal 42 "Shared chores"
tend --json section-add "Shared chores" "This week"
tend --json schedule Personal 42 --due 2026-09-25T09:00 --timezone America/Los_Angeles
tend --json schedule Personal 42 --due 2026-09-25T09:00 --timezone America/Los_Angeles --repeat weekly --weekday 5 --count 8
tend --json unschedule Personal 42
```

For an existing reminder, `edit --tag` replaces its whole tag set; repeat the
flag to keep multiple tags. Use `--clear-tags`, `--clear-url`, or `--unassign`
for explicit removal. Cross-list movement moves the selected reminder and its
entire descendant subtree.

For collaboration:

```bash
tend --json share "Shared chores" ~sampel-palnet
tend --json share "Shared chores" ~sampel-palnet --can-invite
tend --json accept 'omabit://tend/invite/...'
tend --json unshare "Shared chores" ~sampel-palnet
tend --json leave "Someone else’s list"
```

Accepted members can edit list metadata, sections and reminders while the host
is online. Pending invitees may be assigned reminders but cannot edit until
they accept. When the host is offline, treat its replica as read-only and
report that condition rather than retrying writes.

## Destructive actions

Do not infer permission to delete. Ask for confirmation immediately before
running a destructive command, then pass `--yes`:

```bash
tend --json delete 1 42 --yes
tend --json section-delete Personal 8 --yes
tend --json list-delete Personal --yes
```

If a mutation fails with a stale revision, read state again and reconsider the
requested change. Do not blindly replay mutations. Never request, print or
store an Urbit `+code`; `connect` reads it interactively or from standard input
only when the user explicitly asks to authenticate.
