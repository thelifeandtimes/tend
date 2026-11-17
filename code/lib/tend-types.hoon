:: Fresh native data molds. No Gall save/load or migration boundary.
|%
+$  list-id      @ud
+$  section-id   @ud
+$  reminder-id  @ud
+$  op-id        @t
+$  priority     $?(%none %low %medium %high)
::
::  Frozen schema used only to load the Milestone 0 state.
+$  frequency  $?(%hourly %daily %weekly %monthly %yearly)
+$  month-week
  $:  index=@ud
      weekday=@ud
  ==
+$  recurrence
  $:  =frequency
      interval=@ud
      weekdays=(set @ud)
      month-days=(set @ud)
      month-week=(unit month-week)
      end-at=(unit @da)
      max-occurrences=(unit @ud)
  ==
+$  recurrence-advance
  $:  due-at=(unit @da)
      occurrence=@ud
  ==
+$  reminder-advance
  $:  =reminder-id
      advance=recurrence-advance
  ==
+$  schedule
  $:  due-at=@da
      all-day=?
      timezone=@t
      early-seconds=(set @ud)
      recurrence=(unit recurrence)
      occurrence=@ud
      alerted-offsets=(set @ud)
  ==
+$  schedule-input
  $:  due-at=@da
      all-day=?
      timezone=@t
      early-seconds=(set @ud)
      recurrence=(unit recurrence)
  ==
::
::  Frozen %3 reminder/list schema used while loading pre-multiplayer state.
+$  section
  $:  id=section-id
      title=@t
      rank=@ud
  ==
+$  sections  (map section-id section)
+$  reminder
  $:  id=reminder-id
      title=@t
      notes=@t
      url=(unit @t)
      =priority
      flagged=?
      tags=(set @t)
      parent-id=(unit reminder-id)
      section-id=(unit section-id)
      rank=@ud
      assignee=(unit @p)
      schedule=(unit schedule)
      completed=?
      last-completed-at=(unit @da)
      revision=@ud
      created-at=@da
      modified-at=@da
  ==
+$  reminders  (map reminder-id reminder)
+$  task-list
  $:  id=list-id
      title=@t
      color=@t
      symbol=@t
      revision=@ud
      sections=sections
      reminders=reminders
      created-at=@da
      modified-at=@da
  ==
+$  lists  (map list-id task-list)
+$  smart-view  $?(%today %scheduled %all %flagged %assigned %completed)
+$  badge-mode  $?(%all %today %assigned %none)
+$  preferences
  $:  revision=@ud
      default-list=(unit list-id)
      pinned-lists=(list list-id)
      pinned-views=(list smart-view)
      snooze-presets=(list @ud)
      =badge-mode
      all-day-alert-minute=@ud
      all-day-overdue=?
  ==
+$  list-sort  $?(%manual %due %created %priority %title)
+$  list-presentation
  $:  sort=list-sort
      descending=?
  ==
+$  list-presentations  (map list-id list-presentation)
+$  collaboration-policy
  $:  notify-added=?
      notify-completed=?
      notify-assigned=?
  ==
+$  collaboration-policies  (map list-id collaboration-policy)
+$  snooze-key  [list-id reminder-id]
+$  snoozes     (map snooze-key @da)
+$  action
  $%  [%create-list =op-id title=@t]
      [%rename-list =op-id =list-id title=@t base-revision=@ud]
      $:  %update-list
          =op-id
          =list-id
          title=@t
          color=@t
          symbol=@t
          base-revision=@ud
      ==
      [%delete-list =op-id =list-id base-revision=@ud]
      [%add-section =op-id =list-id title=@t rank=@ud base-revision=@ud]
      [%update-section =op-id =list-id =section-id title=@t rank=@ud base-revision=@ud]
      [%place-section =op-id =list-id =section-id target-id=section-id after=? base-revision=@ud]
      [%delete-section =op-id =list-id =section-id base-revision=@ud]
      [%add-reminder =op-id =list-id title=@t tags=(set @t) base-revision=@ud]
      $:  %update-reminder
          =op-id
          =list-id
          =reminder-id
          title=@t
          notes=@t
          url=(unit @t)
          =priority
          flagged=?
          tags=(set @t)
          assignee=(unit @p)
          base-revision=@ud
      ==
      $:  %move-reminder
          =op-id
          =list-id
          =reminder-id
          parent-id=(unit reminder-id)
          section-id=(unit section-id)
          rank=@ud
          base-revision=@ud
      ==
      $:  %move-reminder-to-list
          =op-id
          source-list-id=list-id
          destination-list-id=list-id
          =reminder-id
          source-base-revision=@ud
          destination-base-revision=@ud
      ==
      $:  %place-reminder
          =op-id
          =list-id
          =reminder-id
          target-id=reminder-id
          after=?
          base-revision=@ud
      ==
      [%delete-reminder =op-id =list-id =reminder-id base-revision=@ud]
      [%batch-delete-reminders =op-id =list-id reminder-ids=(set reminder-id) base-revision=@ud]
      $:  %batch-move-reminders
          =op-id
          =list-id
          reminder-ids=(set reminder-id)
          section-id=(unit section-id)
          starting-rank=@ud
          base-revision=@ud
      ==
      $:  %set-schedule
          =op-id
          =list-id
          =reminder-id
          schedule=(unit schedule-input)
          base-revision=@ud
      ==
      [%set-completed =op-id =list-id =reminder-id completed=? advance=(unit recurrence-advance) base-revision=@ud]
      [%batch-set-completed =op-id =list-id reminder-ids=(set reminder-id) completed=? advances=(list reminder-advance) base-revision=@ud]
      $:  %set-preferences
          =op-id
          default-list=(unit list-id)
          pinned-lists=(list list-id)
          pinned-views=(list smart-view)
          snooze-presets=(list @ud)
          base-revision=@ud
      ==
      $:  %set-reminder-policy
          =op-id
          =badge-mode
          all-day-alert-minute=@ud
          all-day-overdue=?
          base-revision=@ud
      ==
      [%set-list-order =op-id list-ids=(list list-id)]
      [%set-list-presentation =op-id =list-id sort=list-sort descending=?]
      $:  %set-collaboration-policy
          =op-id
          =list-id
          notify-added=?
          notify-completed=?
          notify-assigned=?
      ==
      [%snooze-reminder =op-id =list-id =reminder-id until=@da]
      [%replace-tag =op-id from=@t to=(unit @t)]
      [%invite-member =op-id =list-id ship=@p can-invite=?]
      [%accept-invitation =op-id host=@p token=op-id]
      [%decline-invitation =op-id host=@p token=op-id]
      [%remove-member =op-id =list-id ship=@p]
      [%leave-shared-list =op-id =list-id]
      [%ack-notification =op-id notification-id=op-id]
      [%restore-empty =op-id =lists =preferences =snoozes]
  ==
+$  host-status  $?(%checking %online %offline)
+$  member-policy
  $:  can-invite=?
      notify-added=?
      notify-completed=?
  ==
+$  members  (map @p member-policy)
+$  pending-invite
  $:  token=op-id
      policy=member-policy
  ==
+$  pending-invites  (map @p pending-invite)
+$  share
  $:  members=members
      pending=pending-invites
  ==
+$  shares  (map list-id share)
+$  list-ref  [host=@p id=list-id]
+$  replica
  $:  alias=list-id
      ref=list-ref
      list=task-list
      members=members
      status=host-status
      last-seen=@da
  ==
+$  replicas  (map list-id replica)
+$  invitation
  $:  token=op-id
      host=@p
      host-list-id=list-id
      title=@t
      can-invite=?
      received-at=@da
  ==
+$  invitation-key  [@p op-id]
+$  invitations     (map invitation-key invitation)
+$  in-flight
  $:  alias=list-id
      submitted-at=@da
  ==
+$  in-flights  (map op-id in-flight)
::
::  Cross-host reminder movement is a durable, recoverable transfer.  The
::  initiating ship coordinates the operation, the source host temporarily
::  reserves the exported subtree, and the destination retains a bounded
::  import receipt so retries cannot duplicate reminders.
+$  transfer-phase  $?(%requesting %importing %finalizing %restoring)
+$  transfer-payload
  $:  source-host=@p
      source-list-id=list-id
      root-id=reminder-id
      source-base-revision=@ud
      destination-host=@p
      destination-list-id=list-id
      destination-base-revision=@ud
      reminders=(list [reminder-id reminder])
      snoozes=(list [reminder-id @da])
      exported-at=@da
      expires-at=@da
  ==
+$  transfer-coordination
  $:  source-alias=list-id
      destination-alias=list-id
      source-ref=list-ref
      destination-ref=list-ref
      root-id=reminder-id
      source-base-revision=@ud
      destination-base-revision=@ud
      =transfer-phase
      submitted-at=@da
      expires-at=@da
      payload=(unit transfer-payload)
      failure-reason=(unit @tas)
      failure-current=(unit @ud)
  ==
+$  transfer-coordinations  (map op-id transfer-coordination)
+$  transfer-reservation
  $:  coordinator=@p
      =transfer-payload
  ==
+$  transfer-reservations  (map op-id transfer-reservation)
+$  transfer-import
  $:  coordinator=@p
      destination-list-id=list-id
      destination-root-id=reminder-id
      imported-at=@da
  ==
+$  transfer-imports  (map op-id transfer-import)
+$  access
  $:  alias=list-id
      host=@p
      host-list-id=list-id
      status=host-status
      owner=?
      members=members
      pending=(list @p)
  ==
+$  accesses  (list access)
+$  activity-kind  $?(%list-edited %section-added %section-edited %section-moved %section-deleted %reminder-added %reminder-edited %reminder-moved %reminder-deleted %completed %uncompleted %assigned %schedule-changed %member-invited %member-removed)
+$  activity
  $:  id=op-id
      actor=@p
      =activity-kind
      =list-id
      reminder-id=(unit reminder-id)
      occurred-at=(unit @da)
      at=@da
  ==
+$  activity-log  (list activity)
+$  activity-map  (map list-id activity-log)
+$  collaboration-kind  $?(%added %completed %assigned)
+$  collaboration-notification
  $:  id=op-id
      =list-id
      reminder-id=(unit reminder-id)
      actor=@p
      kind=collaboration-kind
      created-at=@da
  ==
+$  collaboration-notifications  (map op-id collaboration-notification)
+$  update
  $%  [%snapshot =lists =preferences =snoozes]
      [%list-upserted =op-id list=task-list =preferences]
      [%list-deleted =op-id =list-id =preferences]
      [%preferences-updated =op-id =preferences]
      [%snoozed =op-id =list-id =reminder-id until=@da]
      [%alert notification-id=op-id =list-id =reminder-id due-at=@da early-seconds=@ud snoozed=?]
      [%notification-acked =op-id notification-id=op-id]
      [%activities-updated =list-id activities=activity-log]
      $:  %local-settings-updated
          list-order=(list list-id)
          presentations=list-presentations
          collaboration-policies=collaboration-policies
      ==
      $:  %collaboration-alert
          notification-id=op-id
          =list-id
          reminder-id=(unit reminder-id)
          actor=@p
          kind=collaboration-kind
      ==
      [%accesses =accesses]
      [%invitations-updated =invitations]
      [%operation-pending =op-id =list-id]
      [%operation-settled =op-id]
      [%rejected =op-id reason=@tas current-revision=(unit @ud)]
  ==
+$  receipts  (map op-id update)
+$  peer-sessions  (map list-id @da)
+$  alert-key  [=list-id =reminder-id due-at=@da early-seconds=@ud snoozed=?]
+$  replica-alerts  (set alert-key)
+$  notification
  $:  id=op-id
      =list-id
      =reminder-id
      due-at=@da
      early-seconds=@ud
      snoozed=?
      created-at=@da
  ==
+$  notifications  (map op-id notification)
+$  store
  $:  %1
      next-id=@ud
      list-map=lists
      receipt-map=receipts
      preferences=preferences
      timer-generation=@ud
      next-wake=(unit @da)
      snooze-map=snoozes
      share-map=shares
      replica-map=replicas
      invitation-map=invitations
      in-flight-map=in-flights
      host-session=@da
      peer-session-map=peer-sessions
      liveness-generation=@ud
      notification-map=notifications
      replica-alert-set=replica-alerts
      receipt-order=(list op-id)
      hosted-activity-map=activity-map
      replica-activity-map=activity-map
      list-order=(list list-id)
      presentation-map=list-presentations
      collaboration-policy-map=collaboration-policies
      collaboration-notification-map=collaboration-notifications
      transfer-map=transfer-coordinations
      transfer-reservation-map=transfer-reservations
      transfer-import-map=transfer-imports
  ==
--
