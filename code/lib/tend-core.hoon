:: Deterministic product transitions, extracted from Tend's own rules.
:: No bowl, Gall cards, peer sends, subscriptions or persistent-state migration.
:: The native writer passes an authenticated actor and persists the whole result.
/<  t  /lib/tend-types.hoon
|%
++  fresh
  ^-  store:t
  =/  prefs=preferences:t
    [0 ~ ~ [%today %scheduled %all %flagged %assigned %completed ~] [300 900 3.600 ~] %today 540 %.y]
  =/  st=store:t  *store:t
  st(next-id 1, preferences prefs)
++  apply
  |=  [our=@p actor=@p now=@da act=action:t state=store:t]
  ^-  [(list update:t) store:t]
  |^
  :: Never trust an actor field in a command body. The nexus supplies this value.
  ?.  =(our actor)
    =/  target=(unit list-id:t)  (action-list-id act)
    ?~  target  [(give [%rejected op-id.act %not-authorized ~]) state]
    =/  sharing=(unit share:t)  (~(get by share-map.state) u.target)
    ?~  sharing  [(give [%rejected op-id.act %not-authorized ~]) state]
    ?.  &((allowed-peer actor act u.sharing) (~(has by members.u.sharing) actor))
      [(give [%rejected op-id.act %not-authorized ~]) state]
    ?.  (peer-move-destination-allowed actor act state)
      [(give [%rejected op-id.act %not-authorized ~]) state]
    (poke-action act)
  (poke-action act)
  ++  poke-action
    |=  act=action:t
    ^-  [(list update:t) store:t]
    =/  prior=(unit update:t)  (~(get by receipt-map.state) op-id.act)
    ?^  prior  [(give u.prior) state]
    ?-  -.act
        %create-list
      ?:  (gte (lent ~(tap by list-map.state)) 10.000)
        (reject op-id.act %list-limit ~ state)
      ?:  (invalid-title title.act)
        (reject op-id.act %invalid-title ~ state)
      =/  appearance=[color=@t symbol=@t]
        (initial-appearance op-id.act now next-id.state)
      =/  lis=task-list:t
        :*  next-id.state
            title.act
            color.appearance
            symbol.appearance
            1
            *sections:t
            *reminders:t
            now
            now
        ==
      =/  prefs=preferences:t  preferences.state
      =.  prefs
        ?~  default-list.prefs
          prefs(revision +(revision.prefs), default-list (some id.lis))
        prefs
      =/  nex=store:t
        :*  %1
            +(next-id.state)
            (~(put by list-map.state) id.lis lis)
            receipt-map.state
            prefs
            timer-generation.state
            next-wake.state
            snooze-map.state
            share-map.state
            replica-map.state
            invitation-map.state
            in-flight-map.state
            host-session.state
            peer-session-map.state
            liveness-generation.state
            notification-map.state
            replica-alert-set.state
            receipt-order.state
            hosted-activity-map.state
            replica-activity-map.state
            (weld list-order.state [id.lis ~])
            presentation-map.state
            collaboration-policy-map.state
            collaboration-notification-map.state
            transfer-map.state
            transfer-reservation-map.state
            transfer-import-map.state
        ==
      =^  cards  nex  (commit op-id.act [%list-upserted op-id.act lis prefs] nex)
      =.  cards  (weld cards (give [%accesses (accesses-for nex)]))
      [(weld cards (give (local-settings-update nex))) nex]
    ::
        %rename-list
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?:  (invalid-title title.act)
        (reject op-id.act %invalid-title `revision.u.old state)
      =/  lis=task-list:t
        u.old(title title.act, revision +(revision.u.old), modified-at now)
      (save-list op-id.act lis state)
    ::
        %update-list
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?:  (invalid-title title.act)
        (reject op-id.act %invalid-title `revision.u.old state)
      ?:  (invalid-appearance color.act symbol.act)
        (reject op-id.act %invalid-appearance `revision.u.old state)
      =/  lis=task-list:t
        %_  u.old
            title        title.act
            color        color.act
            symbol       symbol.act
            revision     +(revision.u.old)
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %delete-list
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      =/  remaining=lists:t  (~(del by list-map.state) list-id.act)
      =/  all=(list [list-id:t task-list:t])  ~(tap by remaining)
      =/  default=(unit list-id:t)
        ?:  !=(default-list.preferences.state `list-id.act)
          default-list.preferences.state
        ?~(all ~ (some -.i.all))
      =/  pinned=(list list-id:t)
        %+  skim  pinned-lists.preferences.state
        |=(id=list-id:t !=(id list-id.act))
      =/  prefs=preferences:t
        %_  preferences.state
            revision      +(revision.preferences.state)
            default-list  default
            pinned-lists  pinned
        ==
      =/  kept-snoozes=(list [snooze-key:t @da])
        %+  skim  ~(tap by snooze-map.state)
        |=  [key=snooze-key:t until=@da]
        !=(-.key list-id.act)
      =/  nex=store:t
        :*  %1
            next-id.state
            remaining
            receipt-map.state
            prefs
            timer-generation.state
            next-wake.state
            (malt kept-snoozes)
            (~(del by share-map.state) list-id.act)
            replica-map.state
            invitation-map.state
            in-flight-map.state
            host-session.state
            peer-session-map.state
            liveness-generation.state
            (without-notifications list-id.act notification-map.state)
            (without-replica-alerts list-id.act replica-alert-set.state)
            receipt-order.state
            (~(del by hosted-activity-map.state) list-id.act)
            replica-activity-map.state
            (without-list-id list-id.act list-order.state)
            (~(del by presentation-map.state) list-id.act)
            (~(del by collaboration-policy-map.state) list-id.act)
            (without-collaboration-notifications list-id.act collaboration-notification-map.state)
            transfer-map.state
            transfer-reservation-map.state
            transfer-import-map.state
        ==
      =^  cards  nex  (commit op-id.act [%list-deleted op-id.act list-id.act prefs] nex)
      =.  cards  (weld cards (give [%accesses (accesses-for nex)]))
      [(weld cards (give (local-settings-update nex))) nex]
    ::
        %add-section
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?:  (gte (lent ~(tap by sections.u.old)) 10.000)
        (reject op-id.act %section-limit `revision.u.old state)
      ?:  (invalid-title title.act)
        (reject op-id.act %invalid-title `revision.u.old state)
      =/  sec=section:t  [next-id.state title.act rank.act]
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            sections    (~(put by sections.u.old) id.sec sec)
            modified-at  now
        ==
      (save-list-with-id op-id.act lis +(next-id.state) state)
    ::
        %update-section
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      =/  old-sec=(unit section:t)  (~(get by sections.u.old) section-id.act)
      ?~  old-sec  (reject op-id.act %unknown-section `revision.u.old state)
      ?:  (invalid-title title.act)
        (reject op-id.act %invalid-title `revision.u.old state)
      =/  sec=section:t  u.old-sec(title title.act, rank rank.act)
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            sections    (~(put by sections.u.old) section-id.act sec)
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %place-section
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      =/  source=(unit section:t)  (~(get by sections.u.old) section-id.act)
      ?~  source  (reject op-id.act %unknown-section `revision.u.old state)
      =/  target=(unit section:t)  (~(get by sections.u.old) target-id.act)
      ?~  target  (reject op-id.act %unknown-target `revision.u.old state)
      ?:  =(section-id.act target-id.act)
        (reject op-id.act %invalid-placement `revision.u.old state)
      =/  ordered=(list section:t)  (ordered-sections sections.u.old)
      =/  placed=(list section:t)
        (place-section-in-order ordered u.source target-id.act after.act)
      ?:  (same-section-order ordered placed)
        (reject op-id.act %invalid-placement `revision.u.old state)
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            sections    (rerank-sections placed sections.u.old)
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %delete-section
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?.  (~(has by sections.u.old) section-id.act)
        (reject op-id.act %unknown-section `revision.u.old state)
      =/  rems=reminders:t
        %-  ~(run by reminders.u.old)
        |=  rem=reminder:t
        ?:  =(section-id.rem `section-id.act)
          rem(section-id ~, revision +(revision.rem), modified-at now)
        rem
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            sections    (~(del by sections.u.old) section-id.act)
            reminders   rems
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %add-reminder
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?:  (gte (lent ~(tap by reminders.u.old)) 100.000)
        (reject op-id.act %reminder-limit `revision.u.old state)
      ?:  (invalid-title title.act)
        (reject op-id.act %invalid-title `revision.u.old state)
      ?:  (invalid-tags tags.act)
        (reject op-id.act %invalid-tags `revision.u.old state)
      =/  rem=reminder:t
        :*  next-id.state
            title.act
            ''
            ~
            %none
            %.n
            tags.act
            ~
            ~
            next-id.state
            ~
            ~
            %.n
            ~
            1
            now
            now
        ==
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   (~(put by reminders.u.old) id.rem rem)
            modified-at  now
        ==
      (save-list-with-id op-id.act lis +(next-id.state) state)
    ::
        %update-reminder
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      =/  old-rem=(unit reminder:t)
        (~(get by reminders.u.old) reminder-id.act)
      ?~  old-rem  (reject op-id.act %unknown-reminder `revision.u.old state)
      ?:  (invalid-title title.act)
        (reject op-id.act %invalid-title `revision.u.old state)
      ?:  (gth (met 3 notes.act) 65.536)
        (reject op-id.act %notes-too-large `revision.u.old state)
      ?:  (invalid-url url.act)
        (reject op-id.act %unsafe-url `revision.u.old state)
      ?:  (invalid-tags tags.act)
        (reject op-id.act %invalid-tags `revision.u.old state)
      ?.  (valid-assignee list-id.act assignee.act state)
        (reject op-id.act %invalid-assignee `revision.u.old state)
      =/  rem=reminder:t
        %_  u.old-rem
            title       title.act
            notes       notes.act
            url         url.act
            priority    priority.act
            flagged     flagged.act
            tags        tags.act
            assignee    assignee.act
            revision    +(revision.u.old-rem)
            modified-at  now
        ==
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   (~(put by reminders.u.old) reminder-id.act rem)
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %move-reminder
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      =/  old-rem=(unit reminder:t)
        (~(get by reminders.u.old) reminder-id.act)
      ?~  old-rem  (reject op-id.act %unknown-reminder `revision.u.old state)
      =/  bad-section=?
        ?~(section-id.act %.n !(~(has by sections.u.old) u.section-id.act))
      ?:  bad-section
        (reject op-id.act %unknown-section `revision.u.old state)
      =/  bad-parent=?
        ?~(parent-id.act %.n !(~(has by reminders.u.old) u.parent-id.act))
      ?:  bad-parent
        (reject op-id.act %unknown-parent `revision.u.old state)
      =/  cycle=?
        ?~  parent-id.act  %.n
        (descendant u.parent-id.act reminder-id.act reminders.u.old)
      ?:  cycle
        (reject op-id.act %parent-cycle `revision.u.old state)
      =/  rem=reminder:t
        %_  u.old-rem
            parent-id   parent-id.act
            section-id  section-id.act
            rank        rank.act
            revision    +(revision.u.old-rem)
            modified-at  now
        ==
      =/  rems=reminders:t
        %-  ~(run by reminders.u.old)
        |=  item=reminder:t
        ?:  =(id.item reminder-id.act)  rem
        ?.  (descendant id.item reminder-id.act reminders.u.old)  item
        ?:  =(section-id.item section-id.act)  item
        item(section-id section-id.act, revision +(revision.item), modified-at now)
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   rems
            modified-at  now
      ==
      (save-list op-id.act lis state)
    ::
        %move-reminder-to-list
      ?:  =(source-list-id.act destination-list-id.act)
        (reject op-id.act %same-list ~ state)
      =/  source=(unit task-list:t)
        (~(get by list-map.state) source-list-id.act)
      ?~  source  (reject op-id.act %unknown-source-list ~ state)
      =/  destination=(unit task-list:t)
        (~(get by list-map.state) destination-list-id.act)
      ?~  destination  (reject op-id.act %cross-host-move ~ state)
      ?.  =(source-base-revision.act revision.u.source)
        (reject op-id.act %stale-source-list `revision.u.source state)
      ?.  =(destination-base-revision.act revision.u.destination)
        (reject op-id.act %stale-destination-list `revision.u.destination state)
      =/  root=(unit reminder:t)
        (~(get by reminders.u.source) reminder-id.act)
      ?~  root
        (reject op-id.act %unknown-reminder `revision.u.source state)
      =/  original=(list [reminder-id:t reminder:t])
        %+  skim  ~(tap by reminders.u.source)
        |=  [rid=reminder-id:t rem=reminder:t]
        (descendant rid reminder-id.act reminders.u.source)
      =/  moved-count=@ud  (lent original)
      ?:  (gth (add moved-count (lent ~(tap by reminders.u.destination))) 100.000)
        (reject op-id.act %reminder-limit `revision.u.destination state)
      =/  destination-rank=@ud
        (next-root-rank reminders.u.destination)
      =/  [id-map=(map reminder-id:t reminder-id:t) next=@ud]
        (allocate-moved-ids original next-id.state)
      =/  moved=(list [reminder-id:t reminder:t])
        %+  turn  original
        |=  [rid=reminder-id:t rem=reminder:t]
        =/  new-id=reminder-id:t  (~(got by id-map) rid)
        =/  valid=(unit @p)
          ?:  (valid-assignee destination-list-id.act assignee.rem state)
            assignee.rem
          ~
        =/  new-parent=(unit reminder-id:t)
          ?:  =(rid reminder-id.act)  ~
          ?~(parent-id.rem ~ `(~(got by id-map) u.parent-id.rem))
        :-  new-id
        %_  rem
            id          new-id
            parent-id   new-parent
            section-id  ~
            rank        ?:(=(rid reminder-id.act) destination-rank rank.rem)
            assignee    valid
            revision    +(revision.rem)
            modified-at  now
        ==
      =/  source-entries=(list [reminder-id:t reminder:t])
        %+  skim  ~(tap by reminders.u.source)
        |=  [rid=reminder-id:t rem=reminder:t]
        !(descendant rid reminder-id.act reminders.u.source)
      =/  destination-rems=reminders:t
        =/  entries=(list [reminder-id:t reminder:t])  moved
        =/  result=reminders:t  reminders.u.destination
        |-
        ?~  entries  result
        $(entries t.entries, result (~(put by result) -.i.entries +.i.entries))
      =/  next-source=task-list:t
        %_  u.source
            revision    +(revision.u.source)
            reminders   (malt source-entries)
            modified-at  now
        ==
      =/  next-destination=task-list:t
        %_  u.destination
            revision    +(revision.u.destination)
            reminders   destination-rems
            modified-at  now
        ==
      =/  liss=lists:t
        %-  ~(put by (~(put by list-map.state) source-list-id.act next-source))
        [destination-list-id.act next-destination]
      =/  moved-snoozes=(list [snooze-key:t @da])
        %+  turn  ~(tap by snooze-map.state)
        |=  [key=snooze-key:t until=@da]
        ?:  ?&  =(-.key source-list-id.act)
                (descendant +.key reminder-id.act reminders.u.source)
            ==
          [[destination-list-id.act (~(got by id-map) +.key)] until]
        [key until]
      =/  interim=store:t
        state(next-id next, list-map liss, snooze-map (malt moved-snoozes))
      =/  snoozes=snoozes:t
        (valid-snoozes (visible-lists interim) snooze-map.interim)
      =/  nex=store:t  interim(snooze-map snoozes)
      (commit op-id.act [%snapshot (visible-lists nex) preferences.nex snoozes] nex)
    ::
        %place-reminder
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      =/  source=(unit reminder:t)
        (~(get by reminders.u.old) reminder-id.act)
      ?~  source  (reject op-id.act %unknown-reminder `revision.u.old state)
      =/  target=(unit reminder:t)
        (~(get by reminders.u.old) target-id.act)
      ?~  target  (reject op-id.act %unknown-target `revision.u.old state)
      ?:  =(reminder-id.act target-id.act)
        (reject op-id.act %invalid-placement `revision.u.old state)
      ?.  =([parent-id.u.source section-id.u.source] [parent-id.u.target section-id.u.target])
        (reject op-id.act %different-group `revision.u.old state)
      =/  ordered=(list reminder:t)
        (ordered-siblings reminders.u.old parent-id.u.source section-id.u.source)
      =/  placed=(list reminder:t)
        (place-sibling ordered u.source target-id.act after.act)
      ?:  (same-reminder-order ordered placed)
        (reject op-id.act %invalid-placement `revision.u.old state)
      =/  rems=reminders:t
        (rerank-siblings placed reminders.u.old)
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   rems
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %delete-reminder
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?.  (~(has by reminders.u.old) reminder-id.act)
        (reject op-id.act %unknown-reminder `revision.u.old state)
      =/  kept=(list [reminder-id:t reminder:t])
        %+  skim  ~(tap by reminders.u.old)
        |=  [rid=reminder-id:t rem=reminder:t]
        !(descendant rid reminder-id.act reminders.u.old)
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   (malt kept)
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %batch-delete-reminders
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?:  (invalid-selection reminder-ids.act reminders.u.old)
        (reject op-id.act %invalid-selection `revision.u.old state)
      =/  kept=(list [reminder-id:t reminder:t])
        %+  skim  ~(tap by reminders.u.old)
        |=  [rid=reminder-id:t rem=reminder:t]
        !(selected-tree rid reminder-ids.act reminders.u.old)
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   (malt kept)
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %batch-move-reminders
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?:  (invalid-selection reminder-ids.act reminders.u.old)
        (reject op-id.act %invalid-selection `revision.u.old state)
      ?:  ?~(section-id.act %.n !(~(has by sections.u.old) u.section-id.act))
        (reject op-id.act %unknown-section `revision.u.old state)
      =/  rems=reminders:t
        %-  ~(run by reminders.u.old)
        |=  rem=reminder:t
        ?.  (selected-tree id.rem reminder-ids.act reminders.u.old)  rem
        =/  root=?  (~(has in reminder-ids.act) id.rem)
        %_  rem
            parent-id   ?:(root ~ parent-id.rem)
            section-id  section-id.act
            rank        ?:(root (add starting-rank.act id.rem) rank.rem)
            revision    +(revision.rem)
            modified-at  now
        ==
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   rems
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %set-schedule
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      =/  old-rem=(unit reminder:t)
        (~(get by reminders.u.old) reminder-id.act)
      ?~  old-rem  (reject op-id.act %unknown-reminder `revision.u.old state)
      ?:  (invalid-schedule schedule.act)
        (reject op-id.act %invalid-schedule `revision.u.old state)
      =/  next-schedule=(unit schedule:t)
        ?~  schedule.act  ~
        =/  input=schedule-input:t  u.schedule.act
        =/  normalized-recurrence=(unit recurrence:t)
          ?~  recurrence.input  ~
          =/  rec=recurrence:t  u.recurrence.input
          =/  needs-anchor=?
            ?|  ?&  =(%monthly frequency.rec)
                    ?=(~ month-week.rec)
                    ?=(~ ~(tap in month-days.rec))
                ==
                ?&  =(%yearly frequency.rec)
                    ?=(~ ~(tap in month-days.rec))
                ==
            ==
          ?.  needs-anchor  (some rec)
          =/  anchor=@ud  d.t:(yore due-at.input)
          (some rec(month-days (~(put in month-days.rec) anchor)))
        =/  built=schedule:t
          :*  due-at.input
              all-day.input
              timezone.input
              early-seconds.input
              normalized-recurrence
              0
              *(set @ud)
          ==
        (some built)
      =/  rem=reminder:t
        %_  u.old-rem
            schedule     next-schedule
            revision     +(revision.u.old-rem)
            modified-at  now
        ==
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   (~(put by reminders.u.old) reminder-id.act rem)
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %set-completed
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?.  (~(has by reminders.u.old) reminder-id.act)
        (reject op-id.act %unknown-reminder `revision.u.old state)
      =/  target=reminder:t
        (~(got by reminders.u.old) reminder-id.act)
      ?:  (invalid-completion-advance target completed.act advance.act now)
        (reject op-id.act %invalid-schedule `revision.u.old state)
      =/  repeated=(unit reminder:t)
        ?.  completed.act  ~
        ?~  schedule.target  ~
        =/  sch=schedule:t  u.schedule.target
        ?~  recurrence.sch  ~
        =/  advanced=(unit schedule:t)  (advance-schedule sch now advance.act)
        ?~  advanced  ~
        `target(schedule advanced, completed %.n, last-completed-at `now, revision +(revision.target), modified-at now)
      ?^  repeated
        =/  lis=task-list:t
          %_  u.old
              revision    +(revision.u.old)
              reminders   (~(put by reminders.u.old) reminder-id.act u.repeated)
              modified-at  now
          ==
        (save-list op-id.act lis state)
      =/  rems=reminders:t
        %-  ~(run by reminders.u.old)
        |=  rem=reminder:t
        ?.  (descendant id.rem reminder-id.act reminders.u.old)  rem
        %_  rem
            completed         completed.act
            last-completed-at  ?:(completed.act `now ~)
            revision          +(revision.rem)
            modified-at       now
        ==
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   rems
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %batch-set-completed
      =/  old=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  old  (reject op-id.act %unknown-list ~ state)
      ?.  =(base-revision.act revision.u.old)
        (reject op-id.act %stale-list `revision.u.old state)
      ?:  (invalid-selection reminder-ids.act reminders.u.old)
        (reject op-id.act %invalid-selection `revision.u.old state)
      ?:  (invalid-batch-advances advances.act reminder-ids.act reminders.u.old now)
        (reject op-id.act %invalid-schedule `revision.u.old state)
      =/  rems=reminders:t
        %-  ~(run by reminders.u.old)
        |=  rem=reminder:t
        (batch-complete-reminder rem reminder-ids.act completed.act reminders.u.old now advances.act)
      =/  lis=task-list:t
        %_  u.old
            revision    +(revision.u.old)
            reminders   rems
            modified-at  now
        ==
      (save-list op-id.act lis state)
    ::
        %set-preferences
      ?.  =(base-revision.act revision.preferences.state)
        (reject op-id.act %stale-preferences `revision.preferences.state state)
      =/  prefs=preferences:t
        %_  preferences.state
            revision        +(revision.preferences.state)
            default-list    default-list.act
            pinned-lists    pinned-lists.act
            pinned-views    pinned-views.act
            snooze-presets  snooze-presets.act
        ==
      ?:  (invalid-preferences prefs (visible-lists state))
        (reject op-id.act %invalid-preferences `revision.preferences.state state)
      =/  nex=store:t  state(preferences prefs)
      (commit op-id.act [%preferences-updated op-id.act prefs] nex)
    ::
        %set-reminder-policy
      ?.  =(base-revision.act revision.preferences.state)
        (reject op-id.act %stale-preferences `revision.preferences.state state)
      ?:  (gte all-day-alert-minute.act 1.440)
        (reject op-id.act %invalid-preferences `revision.preferences.state state)
      =/  prefs=preferences:t
        %_  preferences.state
            revision              +(revision.preferences.state)
            badge-mode            badge-mode.act
            all-day-alert-minute  all-day-alert-minute.act
            all-day-overdue       all-day-overdue.act
        ==
      =/  nex=store:t  state(preferences prefs)
      (commit op-id.act [%preferences-updated op-id.act prefs] nex)
    ::
        %set-list-order
      =/  visible=lists:t  (visible-lists state)
      ?:  (invalid-list-order list-ids.act visible)
        (reject op-id.act %invalid-list-order ~ state)
      =/  nex=store:t  state(list-order list-ids.act)
      (commit op-id.act (local-settings-update nex) nex)
    ::
        %set-list-presentation
      ?.  (~(has by (visible-lists state)) list-id.act)
        (reject op-id.act %unknown-list ~ state)
      =/  presentations=list-presentations:t
        (~(put by presentation-map.state) list-id.act [sort.act descending.act])
      =/  nex=store:t  state(presentation-map presentations)
      (commit op-id.act (local-settings-update nex) nex)
    ::
        %set-collaboration-policy
      ?.  (~(has by (visible-lists state)) list-id.act)
        (reject op-id.act %unknown-list ~ state)
      =/  policy=collaboration-policy:t
        [notify-added.act notify-completed.act notify-assigned.act]
      =/  policies=collaboration-policies:t
        (~(put by collaboration-policy-map.state) list-id.act policy)
      =/  nex=store:t  state(collaboration-policy-map policies)
      (commit op-id.act (local-settings-update nex) nex)
    ::
        %snooze-reminder
      =/  lis=(unit task-list:t)  (~(get by list-map.state) list-id.act)
      ?~  lis  (reject op-id.act %unknown-list ~ state)
      =/  rem=(unit reminder:t)  (~(get by reminders.u.lis) reminder-id.act)
      ?~  rem  (reject op-id.act %unknown-reminder `revision.u.lis state)
      ?:  |(completed.u.rem ?=(~ schedule.u.rem) (lte until.act now))
        (reject op-id.act %invalid-snooze `revision.u.lis state)
      =/  key=snooze-key:t  [list-id.act reminder-id.act]
      =/  nex=store:t
        state(snooze-map (~(put by snooze-map.state) key until.act))
      (commit op-id.act [%snoozed op-id.act list-id.act reminder-id.act until.act] nex)
    ::
        %replace-tag
      ?:  |((invalid-tag from.act) ?~(to.act %.n (invalid-tag u.to.act)))
        (reject op-id.act %invalid-tag ~ state)
      =/  liss=lists:t
        %-  ~(run by list-map.state)
        |=  lis=task-list:t
        =/  rems=reminders:t
          %-  ~(run by reminders.lis)
          |=  rem=reminder:t
          ?.  (~(has in tags.rem) from.act)  rem
          =/  next-tags=(set @t)  (~(del in tags.rem) from.act)
          =.  next-tags  ?~(to.act next-tags (~(put in next-tags) u.to.act))
          rem(tags next-tags, revision +(revision.rem), modified-at now)
        ?:  =(rems reminders.lis)  lis
        lis(reminders rems, revision +(revision.lis), modified-at now)
      =/  interim=store:t  state(list-map liss)
      =/  visible=lists:t  (visible-lists interim)
      =/  snoozes=snoozes:t  (valid-snoozes visible snooze-map.state)
      =/  nex=store:t  interim(snooze-map snoozes)
      (commit op-id.act [%snapshot visible preferences.state snoozes] nex)
    ::
        %invite-member
      (reject op-id.act %use-native-membership ~ state)
        %accept-invitation
      (reject op-id.act %use-native-membership ~ state)
        %decline-invitation
      (reject op-id.act %use-native-membership ~ state)
        %remove-member
      (reject op-id.act %use-native-membership ~ state)
        %leave-shared-list
      (reject op-id.act %use-native-membership ~ state)
        %ack-notification
      =/  found=(unit notification:t)
        (~(get by notification-map.state) notification-id.act)
      =/  collaboration=(unit collaboration-notification:t)
        (~(get by collaboration-notification-map.state) notification-id.act)
      ?:  ?&  ?=(~ found)
              ?=(~ collaboration)
          ==
        (reject op-id.act %unknown-notification ~ state)
      =/  nex=store:t
        %_  state
            notification-map  (~(del by notification-map.state) notification-id.act)
            collaboration-notification-map  (~(del by collaboration-notification-map.state) notification-id.act)
        ==
      (commit op-id.act [%notification-acked op-id.act notification-id.act] nex)
    ::
        %restore-empty
      ?.  (restorable-empty state)
        (reject op-id.act %not-empty ~ state)
      ?:  (invalid-restore lists.act preferences.act snoozes.act)
        (reject op-id.act %invalid-backup ~ state)
      =/  snoozes=snoozes:t
        (restored-snoozes lists.act snoozes.act now)
      =/  nex=store:t
        :*  %1
            (next-id-for lists.act)
            lists.act
            *receipts:t
            preferences.act
            timer-generation.state
            ~
            snoozes
            *shares:t
            *replicas:t
            *invitations:t
            *in-flights:t
            host-session.state
            *peer-sessions:t
            liveness-generation.state
            *notifications:t
            *replica-alerts:t
            ~
            *activity-map:t
            *activity-map:t
            (list-order-for lists.act)
            *list-presentations:t
            *collaboration-policies:t
            *collaboration-notifications:t
            *transfer-coordinations:t
            *transfer-reservations:t
            *transfer-imports:t
        ==
      =^  cards  nex  (commit op-id.act [%snapshot lists.act preferences.act snoozes] nex)
      [(weld cards (give (local-settings-update nex))) nex]
    ==
  ++  action-list-id
    |=  act=action:t
    ^-  (unit list-id:t)
    ?-  -.act
        %create-list              ~
        %rename-list              `list-id.act
        %update-list              `list-id.act
        %delete-list              `list-id.act
        %add-section              `list-id.act
        %update-section           `list-id.act
        %place-section            `list-id.act
        %delete-section           `list-id.act
        %add-reminder             `list-id.act
        %update-reminder          `list-id.act
        %move-reminder            `list-id.act
        %move-reminder-to-list    `source-list-id.act
        %place-reminder           `list-id.act
        %delete-reminder          `list-id.act
        %batch-delete-reminders   `list-id.act
        %batch-move-reminders     `list-id.act
        %set-schedule             `list-id.act
        %set-completed            `list-id.act
        %batch-set-completed      `list-id.act
        %set-preferences          ~
        %set-reminder-policy      ~
        %set-list-order           ~
        %set-list-presentation    ~
        %set-collaboration-policy  ~
        %snooze-reminder          `list-id.act
        %replace-tag              ~
        %invite-member            `list-id.act
        %accept-invitation        ~
        %decline-invitation       ~
        %remove-member            `list-id.act
        %leave-shared-list        `list-id.act
        %ack-notification         ~
        %restore-empty            ~
    ==
  ++  allowed-peer
    |=  [sender=@p act=action:t sharing=share:t]
    ^-  ?
    ?-  -.act
        %create-list              %.n
        %delete-list              %.n
        %set-preferences          %.n
        %set-reminder-policy      %.n
        %set-list-order           %.n
        %set-list-presentation    %.n
        %set-collaboration-policy  %.n
        %snooze-reminder          %.n
        %replace-tag              %.n
        %accept-invitation        %.n
        %decline-invitation       %.n
        %remove-member            %.n
        %leave-shared-list        %.n
        %ack-notification         %.n
        %restore-empty            %.n
        %invite-member
      =/  policy=(unit member-policy:t)  (~(get by members.sharing) sender)
      ?~(policy %.n can-invite.u.policy)
    ::
        %rename-list              %.y
        %update-list              %.y
        %add-section              %.y
        %update-section           %.y
        %place-section            %.y
        %delete-section           %.y
        %add-reminder             %.y
        %update-reminder          %.y
        %move-reminder            %.y
        %move-reminder-to-list    %.y
        %place-reminder           %.y
        %delete-reminder          %.y
        %batch-delete-reminders   %.y
        %batch-move-reminders     %.y
        %set-schedule             %.y
        %set-completed            %.y
        %batch-set-completed      %.y
    ==
  ++  peer-move-destination-allowed
    |=  [sender=@p act=action:t st=store:t]
    ^-  ?
    ?.  ?=(%move-reminder-to-list -.act)  %.y
    =/  destination=(unit task-list:t)
      (~(get by list-map.st) destination-list-id.act)
    ?~  destination  %.n
    =/  sharing=(unit share:t)
      (~(get by share-map.st) destination-list-id.act)
    ?~  sharing  %.n
    (~(has by members.u.sharing) sender)
  ++  hosted-activities
    |=  [=list-id:t st=store:t]
    ^-  activity-log:t
    =/  found=(unit activity-log:t)
      (~(get by hosted-activity-map.st) list-id)
    ?~(found ~ u.found)
  ++  visible-lists
    |=  st=store:t
    ^-  lists:t
    =/  entries=(list [list-id:t replica:t])  ~(tap by replica-map.st)
    =/  result=lists:t  list-map.st
    |-
    ?~  entries  result
    =/  alias=list-id:t  -.i.entries
    =/  rep=replica:t  +.i.entries
    =.  result
      ?:  (~(has by result) alias)
        result
      (~(put by result) alias (alias-list alias list.rep))
    $(entries t.entries)
  ++  accesses-for
    |=  st=store:t
    ^-  accesses:t
    =/  hosted=accesses:t
      %-  ~(rep by list-map.st)
      |=  [[id=list-id:t lis=task-list:t] acc=accesses:t]
      =/  found=(unit share:t)  (~(get by share-map.st) id)
      =/  mem=members:t  ?~(found *members:t members.u.found)
      =/  pending-ships=(list @p)
        ?~  found  ~
        %+  turn  ~(tap by pending.u.found)
        |=  [ship=@p invite=pending-invite:t]
        ship
      [[id our id %online %.y mem pending-ships] acc]
    =/  remote=accesses:t
      %-  ~(rep by replica-map.st)
      |=  [[alias=list-id:t rep=replica:t] acc=accesses:t]
      :_  acc
      [alias host.ref.rep id.ref.rep status.rep %.n members.rep ~]
    (weld hosted remote)
  ++  invalid-title
    |=  title=@t
    |(=(0 title) (gth (met 3 title) 1.024))
  ++  initial-appearance
    |=  [operation=op-id:t now=@da list-id=list-id:t]
    ^-  [color=@t symbol=@t]
    =/  choices=(list [@t @t])
      :~  ['#3b82f6' '●']
          ['#8b5cf6' '✦']
          ['#ec4899' '♥']
          ['#ef4444' '◆']
          ['#f97316' '☀']
          ['#eab308' '★']
          ['#22c55e' '♣']
          ['#14b8a6' '◈']
          ['#06b6d4' '☁']
          ['#6366f1' '☾']
          ['#a855f7' '✎']
          ['#64748b' '⌂']
      ==
    =/  choice=@ud  (mod (mug [operation now list-id]) 12)
    (snag choice choices)
  ++  invalid-appearance
    |=  [color=@t symbol=@t]
    ?|  =(0 color)
        (gth (met 3 color) 128)
        =(0 symbol)
        (gth (met 3 symbol) 128)
    ==
  ++  invalid-tag
    |=  tag=@t
    |(=(0 tag) (gth (met 3 tag) 128))
  ++  invalid-tags
    |=  tags=(set @t)
    ^-  ?
    =/  values=(list @t)  ~(tap in tags)
    |((gth (lent values) 100) !(levy values |=(tag=@t !(invalid-tag tag))))
  ++  invalid-preferences
    |=  [prefs=preferences:t liss=lists:t]
    ^-  ?
    =/  bad-default=?
      ?~(default-list.prefs %.n !(~(has by liss) u.default-list.prefs))
    ?:  bad-default  %.y
    ?:  (gth (lent pinned-lists.prefs) 10.000)  %.y
    ?:  (gth (lent pinned-views.prefs) 6)  %.y
    ?:  (gth (lent snooze-presets.prefs) 32)  %.y
    ?.  (levy pinned-lists.prefs |=(id=list-id:t (~(has by liss) id)))
      %.y
    ?.  (levy snooze-presets.prefs |=(seconds=@ud &((gth seconds 0) (lte seconds 2.592.000))))
      %.y
    (gte all-day-alert-minute.prefs 1.440)
  ++  local-settings-update
    |=  st=store:t
    ^-  update:t
    [%local-settings-updated list-order.st presentation-map.st collaboration-policy-map.st]
  ++  invalid-list-order
    |=  [order=(list list-id:t) liss=lists:t]
    ^-  ?
    ?:  (gth (lent order) 10.000)  %.y
    =/  unique=(set list-id:t)  (~(gas in *(set list-id:t)) order)
    ?:  !=((lent order) (lent ~(tap in unique)))  %.y
    ?:  !=((lent order) (lent ~(tap by liss)))  %.y
    !(levy order |=(id=list-id:t (~(has by liss) id)))
  ++  list-order-for
    |=  liss=lists:t
    ^-  (list list-id:t)
    %+  turn  ~(tap by liss)
    |=  [id=list-id:t lis=task-list:t]
    id
  ++  without-list-id
    |=  [target=list-id:t order=(list list-id:t)]
    ^-  (list list-id:t)
    %+  skim  order
    |=(id=list-id:t !=(id target))
  ++  without-collaboration-notifications
    |=  [target=list-id:t values=collaboration-notifications:t]
    ^-  collaboration-notifications:t
    %-  malt
    %+  skim  ~(tap by values)
    |=  [id=op-id:t note=collaboration-notification:t]
    !=(list-id.note target)
  ++  without-notifications
    |=  [target=list-id:t values=notifications:t]
    ^-  notifications:t
    %-  malt
    %+  skim  ~(tap by values)
    |=  [id=op-id:t note=notification:t]
    !=(list-id.note target)
  ++  without-replica-alerts
    |=  [target=list-id:t values=replica-alerts:t]
    ^-  replica-alerts:t
    =/  kept=(list alert-key:t)
      %+  skim  ~(tap in values)
      |=(key=alert-key:t !=(list-id.key target))
    (~(gas in *(set alert-key:t)) kept)
  ++  restorable-empty
    |=  st=store:t
    ^-  ?
    ?&  =(1 next-id.st)
        ?=(~ ~(tap by list-map.st))
        ?=(~ ~(tap by snooze-map.st))
        ?=(~ ~(tap by share-map.st))
        ?=(~ ~(tap by replica-map.st))
        ?=(~ ~(tap by invitation-map.st))
        ?=(~ ~(tap by in-flight-map.st))
        ?=(~ ~(tap by notification-map.st))
        ?=(~ ~(tap by collaboration-notification-map.st))
        ?=(~ ~(tap by transfer-map.st))
        ?=(~ ~(tap by transfer-reservation-map.st))
        ?=(~ ~(tap by transfer-import-map.st))
    ==
  ++  invalid-restore
    |=  [liss=lists:t prefs=preferences:t values=snoozes:t]
    ^-  ?
    =/  entries=(list [list-id:t task-list:t])  ~(tap by liss)
    ?:  (gth (lent entries) 10.000)  %.y
    ?:  (invalid-preferences prefs liss)  %.y
    =/  remaining=(list [list-id:t task-list:t])  entries
    =/  invalid-list=?
      |-
      ?~  remaining  %.n
      ?:  (invalid-restore-list +.i.remaining)  %.y
      $(remaining t.remaining)
    ?:  invalid-list  %.y
    =/  snooze-entries=(list [snooze-key:t @da])  ~(tap by values)
    ?:  (gth (lent snooze-entries) 100.000)  %.y
    |-
    ?~  snooze-entries  %.n
    =/  key=snooze-key:t  -.i.snooze-entries
    =/  lis=(unit task-list:t)  (~(get by liss) -.key)
    ?~  lis  %.y
    ?.  (~(has by reminders.u.lis) +.key)  %.y
    $(snooze-entries t.snooze-entries)
  ++  invalid-restore-list
    |=  lis=task-list:t
    ^-  ?
    ?:  (invalid-title title.lis)  %.y
    ?:  (invalid-appearance color.lis symbol.lis)  %.y
    =/  sections=(list [section-id:t section:t])  ~(tap by sections.lis)
    ?:  (gth (lent sections) 10.000)  %.y
    =/  remaining-sections=(list [section-id:t section:t])  sections
    =/  invalid-section=?
      |-
      ?~  remaining-sections  %.n
      ?:  (invalid-title title.+.i.remaining-sections)  %.y
      $(remaining-sections t.remaining-sections)
    ?:  invalid-section  %.y
    =/  reminders=(list [reminder-id:t reminder:t])  ~(tap by reminders.lis)
    ?:  (gth (lent reminders) 100.000)  %.y
    =/  remaining=(list [reminder-id:t reminder:t])  reminders
    |-
    ?~  remaining  %.n
    =/  rem=reminder:t  +.i.remaining
    ?:  (invalid-title title.rem)  %.y
    ?:  (gth (met 3 notes.rem) 65.536)  %.y
    ?:  (invalid-url url.rem)  %.y
    ?:  (invalid-tags tags.rem)  %.y
    ?:  ?~(section-id.rem %.n !(~(has by sections.lis) u.section-id.rem))
      %.y
    ?:  ?~(parent-id.rem %.n !(~(has by reminders.lis) u.parent-id.rem))
      %.y
    ?:  ?~(parent-id.rem %.n (descendant u.parent-id.rem id.rem reminders.lis))
      %.y
    ?:  (invalid-stored-schedule schedule.rem)  %.y
    $(remaining t.remaining)
  ++  invalid-stored-schedule
    |=  value=(unit schedule:t)
    ^-  ?
    ?~  value  %.n
    =/  sch=schedule:t  u.value
    =/  input=schedule-input:t
      [due-at.sch all-day.sch timezone.sch early-seconds.sch recurrence.sch]
    (invalid-schedule `input)
  ++  restored-snoozes
    |=  [liss=lists:t values=snoozes:t now=@da]
    ^-  snoozes:t
    =/  valid=snoozes:t  (valid-snoozes liss values)
    =/  kept=(list [snooze-key:t @da])
      %+  skim  ~(tap by valid)
      |=  [key=snooze-key:t until=@da]
      (gth until now)
    (malt kept)
  ++  next-id-for
    |=  liss=lists:t
    ^-  @ud
    =/  entries=(list [list-id:t task-list:t])  ~(tap by liss)
    =/  highest=@ud  0
    |-
    ?~  entries  +(highest)
    =/  lis=task-list:t  +.i.entries
    =.  highest  (max highest id.lis)
    =/  sections=(list [section-id:t section:t])  ~(tap by sections.lis)
    =.  highest
      %+  roll  sections
      |=  [[id=section-id:t sec=section:t] acc=@ud]
      (max id acc)
    =/  reminders=(list [reminder-id:t reminder:t])  ~(tap by reminders.lis)
    =.  highest
      %+  roll  reminders
      |=  [[id=reminder-id:t rem=reminder:t] acc=@ud]
      (max id acc)
    $(entries t.entries)
  ++  valid-assignee
    |=  [=list-id:t assignee=(unit @p) st=store:t]
    ^-  ?
    ?~  assignee  %.y
    ?:  =(u.assignee our)  %.y
    =/  sharing=(unit share:t)  (~(get by share-map.st) list-id)
    ?~  sharing  %.n
    |((~(has by members.u.sharing) u.assignee) (~(has by pending.u.sharing) u.assignee))
  ++  invalid-selection
    |=  [ids=(set reminder-id:t) rems=reminders:t]
    ^-  ?
    =/  values=(list reminder-id:t)  ~(tap in ids)
    ?:  ?=(~ values)  %.y
    ?:  (gth (lent values) 500)  %.y
    =/  remaining=(list reminder-id:t)  values
    |-
    ?~  remaining  %.n
    ?.  (~(has by rems) i.remaining)  %.y
    $(remaining t.remaining)
  ++  selected-tree
    |=  [candidate=reminder-id:t ids=(set reminder-id:t) rems=reminders:t]
    ^-  ?
    (lien ~(tap in ids) |=(ancestor=reminder-id:t (descendant candidate ancestor rems)))
  ++  batch-complete-reminder
    |=  [rem=reminder:t ids=(set reminder-id:t) completed=? rems=reminders:t now=@da advances=(list reminder-advance:t)]
    ^-  reminder:t
    =/  advanced=(unit schedule:t)
      ?.  completed  ~
      ?.  (~(has in ids) id.rem)  ~
      ?~  schedule.rem  ~
      =/  sch=schedule:t  u.schedule.rem
      ?~  recurrence.sch  ~
      (advance-schedule sch now (advance-for id.rem advances))
    ?^  advanced
      rem(schedule advanced, completed %.n, last-completed-at `now, revision +(revision.rem), modified-at now)
    ?.  (selected-tree id.rem ids rems)  rem
    rem(completed completed, last-completed-at ?:(completed `now ~), revision +(revision.rem), modified-at now)
  ++  advance-schedule
    |=  [sch=schedule:t now=@da hint=(unit recurrence-advance:t)]
    ^-  (unit schedule:t)
    ?^  hint
      ?~  due-at.u.hint  ~
      `sch(due-at u.due-at.u.hint, occurrence occurrence.u.hint, alerted-offsets *(set @ud))
    =/  remaining=@ud  100.000
    |-
    =/  advanced=(unit schedule:t)  (advance-schedule-once sch)
    ?~  advanced  ~
    ?:  (gth due-at.u.advanced now)  advanced
    ?:  =(0 remaining)  ~
    $(sch u.advanced, remaining (dec remaining))
  ++  advance-schedule-once
    |=  sch=schedule:t
    ^-  (unit schedule:t)
    ?~  recurrence.sch  ~
    =/  rec=recurrence:t  u.recurrence.sch
    =/  next-occurrence=@ud  +(occurrence.sch)
    ?:  ?~(max-occurrences.rec %.n (gte next-occurrence u.max-occurrences.rec))
      ~
    =/  next-due=@da
      ?-  frequency.rec
          %hourly   (add due-at.sch (mul interval.rec ~h1))
          %daily    (add due-at.sch (mul interval.rec ~d1))
          %weekly   (next-weekly due-at.sch interval.rec weekdays.rec)
          %monthly  (next-monthly due-at.sch interval.rec month-days.rec month-week.rec)
          %yearly   (add-years due-at.sch interval.rec month-days.rec)
      ==
    ?:  ?~(end-at.rec %.n (gth next-due u.end-at.rec))  ~
    `sch(due-at next-due, occurrence next-occurrence, alerted-offsets *(set @ud))
  ++  advance-for
    |=  [id=reminder-id:t advances=(list reminder-advance:t)]
    ^-  (unit recurrence-advance:t)
    ?~  advances  ~
    ?:  =(id reminder-id.i.advances)  `advance.i.advances
    $(advances t.advances)
  ++  invalid-completion-advance
    |=  [rem=reminder:t completed=? hint=(unit recurrence-advance:t) now=@da]
    ^-  ?
    ?.  completed  ?^(hint %.y %.n)
    ?~  hint  %.n
    ?~  schedule.rem  %.y
    =/  sch=schedule:t  u.schedule.rem
    ?~  recurrence.sch  %.y
    ?:  (lte occurrence.u.hint occurrence.sch)  %.y
    ?:  (gth (sub occurrence.u.hint occurrence.sch) 100.000)  %.y
    ?~  due-at.u.hint
      =/  rec=recurrence:t  u.recurrence.sch
      ?:  ?^(max-occurrences.rec (gte occurrence.u.hint u.max-occurrences.rec) %.n)  %.n
      ?~(end-at.rec %.y %.n)
    ?:  (lte u.due-at.u.hint now)  %.y
    ?:  (lte u.due-at.u.hint due-at.sch)  %.y
    =/  rec=recurrence:t  u.recurrence.sch
    ?:  ?~(max-occurrences.rec %.n (gte occurrence.u.hint u.max-occurrences.rec))  %.y
    ?~(end-at.rec %.n (gth u.due-at.u.hint u.end-at.rec))
  ++  invalid-batch-advances
    |=  [advances=(list reminder-advance:t) ids=(set reminder-id:t) rems=reminders:t now=@da]
    ^-  ?
    ?:  (gth (lent advances) (lent ~(tap in ids)))  %.y
    %+  levy  advances
    |=  item=reminder-advance:t
    ?.  (~(has in ids) reminder-id.item)  %.n
    =/  rem=(unit reminder:t)  (~(get by rems) reminder-id.item)
    ?~  rem  %.n
    !(invalid-completion-advance u.rem %.y `advance.item now)
  ++  next-weekly
    |=  [due=@da interval=@ud weekdays=(set @ud)]
    ^-  @da
    ?~  ~(tap in weekdays)  (add due (mul (mul interval 7) ~d1))
    =/  current=@ud  (daws:chrono:userlib (yore due))
    =/  later=(list @ud)
      %+  skim  ~(tap in weekdays)
      |=(day=@ud (gth day current))
    =/  days=@ud
      ?^  later
        (sub (smallest later) current)
      =/  first=@ud  (smallest ~(tap in weekdays))
      (sub (add (mul interval 7) first) current)
    (add due (mul days ~d1))
  ++  next-monthly
    |=  [due=@da interval=@ud month-days=(set @ud) month-week=(unit month-week:t)]
    ^-  @da
    =/  dat=date  (yore due)
    ?^  ~(tap in month-days)
      =/  maximum=@ud  (days-in-month y.dat m.dat)
      =/  later=(list @ud)
        %+  skim  ~(tap in month-days)
        |=  day=@ud
        &((gth day d.t.dat) (lte day maximum))
      ?^  later
        (year dat(d.t (smallest later)))
      =/  target=date  (shift-month dat interval)
      =/  day=@ud  (min (smallest ~(tap in month-days)) (days-in-month y.target m.target))
      (year target(d.t day))
    ?^  month-week
      =/  candidate=@ud
        (ordinal-day y.dat m.dat index.u.month-week weekday.u.month-week)
      ?:  (gth candidate d.t.dat)
        (year dat(d.t candidate))
      =/  target=date  (shift-month dat interval)
      =/  day=@ud
        (ordinal-day y.target m.target index.u.month-week weekday.u.month-week)
      (year target(d.t day))
    =/  target=date  (shift-month dat interval)
    =/  day=@ud  (min d.t.dat (days-in-month y.target m.target))
    (year target(d.t day))
  ++  add-years
    |=  [due=@da interval=@ud month-days=(set @ud)]
    ^-  @da
    =/  dat=date  (yore due)
    =/  next-year=@ud  (add y.dat interval)
    =/  values=(list @ud)  ~(tap in month-days)
    =/  desired=@ud  ?~(values d.t.dat (smallest values))
    =/  day=@ud  (min desired (days-in-month next-year m.dat))
    (year dat(y next-year, d.t day))
  ++  shift-month
    |=  [dat=date interval=@ud]
    ^-  date
    =/  total=@ud  (add (mul y.dat 12) (dec m.dat))
    =.  total  (add total interval)
    =/  next-year=@ud  (div total 12)
    =/  next-month=@ud  +((mod total 12))
    dat(y next-year, m next-month)
  ++  days-in-month
    |=  [year-number=@ud month-number=@ud]
    ^-  @ud
    =/  base=date  [[& year-number] month-number [1 0 0 0 ~]]
    =/  following=date  (shift-month base 1)
    d.t:(yore (sub (year following) ~d1))
  ++  ordinal-day
    |=  [year-number=@ud month-number=@ud index=@ud weekday=@ud]
    ^-  @ud
    =/  maximum=@ud  (days-in-month year-number month-number)
    =/  first=date  [[& year-number] month-number [1 0 0 0 ~]]
    =/  first-weekday=@ud  (daws:chrono:userlib first)
    =/  first-match=@ud  +((mod (add (sub (add weekday 7) first-weekday) 7) 7))
    =/  candidate=@ud  (add first-match (mul (dec index) 7))
    ?:  &(!=(index 5) (lte candidate maximum))  candidate
    =/  last=date  first(d.t maximum)
    =/  last-weekday=@ud  (daws:chrono:userlib last)
    (sub maximum (mod (sub (add last-weekday 7) weekday) 7))
  ++  smallest
    |=  values=(list @ud)
    ^-  @ud
    ?~  values  !!
    =/  result=@ud  i.values
    =/  remaining=(list @ud)  t.values
    |-
    ?~  remaining  result
    =.  result  (min result i.remaining)
    $(remaining t.remaining)
  ++  invalid-url
    |=  url=(unit @t)
    ?~  url  %.n
    ?:  (gth (met 3 u.url) 8.192)  %.y
    =/  value=tape  (trip u.url)
    ?~  value  %.n
    =/  http=(unit @ud)    (find "http://" value)
    =/  https=(unit @ud)   (find "https://" value)
    =/  mailto=(unit @ud)  (find "mailto:" value)
    =/  safe=?
      ?|  =(http `0)
          =(https `0)
          =(mailto `0)
      ==
    !safe
  ++  invalid-schedule
    |=  value=(unit schedule-input:t)
    ^-  ?
    ?~  value  %.n
    =/  sch=schedule-input:t  u.value
    ?:  |(=(0 timezone.sch) (gth (met 3 timezone.sch) 128))  %.y
    =/  offsets=(list @ud)  ~(tap in early-seconds.sch)
    ?:  (gth (lent offsets) 64)  %.y
    ?.  (levy offsets |=(seconds=@ud (lte seconds 31.536.000)))  %.y
    ?~  recurrence.sch  %.n
    (invalid-recurrence u.recurrence.sch due-at.sch)
  ++  invalid-recurrence
    |=  [rec=recurrence:t due=@da]
    ^-  ?
    ?:  =(0 interval.rec)  %.y
    =/  weekdays=(list @ud)  ~(tap in weekdays.rec)
    ?:  (gth (lent weekdays) 7)  %.y
    ?.  (levy weekdays |=(day=@ud (lth day 7)))  %.y
    =/  month-days=(list @ud)  ~(tap in month-days.rec)
    ?:  (gth (lent month-days) 31)  %.y
    ?.  (levy month-days |=(day=@ud &((gth day 0) (lte day 31))))  %.y
    =/  bad-month-week=?
      ?~  month-week.rec  %.n
      ?|  =(0 index.u.month-week.rec)
          (gth index.u.month-week.rec 5)
          (gth weekday.u.month-week.rec 6)
      ==
    ?:  bad-month-week  %.y
    ?:  ?~(end-at.rec %.n (lte u.end-at.rec due))  %.y
    ?:  ?~(max-occurrences.rec %.n =(0 u.max-occurrences.rec))  %.y
    %.n
  ++  descendant
    |=  [candidate=reminder-id:t ancestor=reminder-id:t rems=reminders:t]
    ^-  ?
    ?:  =(candidate ancestor)  %.y
    =/  item=(unit reminder:t)  (~(get by rems) candidate)
    ?~  item  %.n
    ?~  parent-id.u.item  %.n
    $(candidate u.parent-id.u.item)
  ++  next-root-rank
    |=  rems=reminders:t
    ^-  @ud
    =/  greatest=@ud
      %+  roll  ~(tap by rems)
      |=  [[rid=reminder-id:t rem=reminder:t] highest=@ud]
      ?:  ?|(?=(^ parent-id.rem) ?=(^ section-id.rem))  highest
      ?:((gth rank.rem highest) rank.rem highest)
    (add greatest 1.024)
  ++  allocate-moved-ids
    |=  [entries=(list [reminder-id:t reminder:t]) next=@ud]
    ^-  [(map reminder-id:t reminder-id:t) @ud]
    =/  result=(map reminder-id:t reminder-id:t)  *(map reminder-id:t reminder-id:t)
    |-
    ?~  entries  [result next]
    $(entries t.entries, next +(next), result (~(put by result) -.i.entries next))
  ++  ordered-siblings
    |=  [rems=reminders:t parent=(unit reminder-id:t) section=(unit section-id:t)]
    ^-  (list reminder:t)
    =/  selected=(list reminder:t)
      %+  turn
        %+  skim  ~(tap by rems)
        |=  [rid=reminder-id:t rem=reminder:t]
        =([parent section] [parent-id.rem section-id.rem])
      |=  [rid=reminder-id:t rem=reminder:t]
      rem
    %+  sort  selected
    |=  [a=reminder:t b=reminder:t]
    ?:  =(rank.a rank.b)  (lth id.a id.b)
    (lth rank.a rank.b)
  ++  ordered-sections
    |=  secs=sections:t
    ^-  (list section:t)
    =/  values=(list section:t)
      %+  turn  ~(tap by secs)
      |=  [sid=section-id:t sec=section:t]
      sec
    %+  sort  values
    |=  [a=section:t b=section:t]
    ?:  =(rank.a rank.b)  (lth id.a id.b)
    (lth rank.a rank.b)
  ++  place-section-in-order
    |=  [ordered=(list section:t) source=section:t target=section-id:t after=?]
    ^-  (list section:t)
    =/  remaining=(list section:t)
      %+  skim  ordered
      |=  sec=section:t
      !=(id.sec id.source)
    |-
    ?~  remaining  [source ~]
    ?:  =(id.i.remaining target)
      ?:  after  [i.remaining source t.remaining]
      [source remaining]
    [i.remaining $(remaining t.remaining)]
  ++  same-section-order
    |=  [a=(list section:t) b=(list section:t)]
    ^-  ?
    ?~  a  ?=(~ b)
    ?~  b  %.n
    ?&  =(id.i.a id.i.b)
        $(a t.a, b t.b)
    ==
  ++  rerank-sections
    |=  [ordered=(list section:t) secs=sections:t]
    ^-  sections:t
    =/  next-rank=@ud  1.024
    |-
    ?~  ordered  secs
    =/  sec=section:t  i.ordered
    =/  next-secs=sections:t
      ?:  =(rank.sec next-rank)  secs
      (~(put by secs) id.sec sec(rank next-rank))
    $(ordered t.ordered, secs next-secs, next-rank (add next-rank 1.024))
  ++  place-sibling
    |=  [ordered=(list reminder:t) source=reminder:t target=reminder-id:t after=?]
    ^-  (list reminder:t)
    =/  remaining=(list reminder:t)
      %+  skim  ordered
      |=  rem=reminder:t
      !=(id.rem id.source)
    |-
    ?~  remaining  [source ~]
    ?:  =(id.i.remaining target)
      ?:  after  [i.remaining source t.remaining]
      [source remaining]
    [i.remaining $(remaining t.remaining)]
  ++  same-reminder-order
    |=  [a=(list reminder:t) b=(list reminder:t)]
    ^-  ?
    ?~  a  ?=(~ b)
    ?~  b  %.n
    ?&  =(id.i.a id.i.b)
        $(a t.a, b t.b)
    ==
  ++  rerank-siblings
    |=  [ordered=(list reminder:t) rems=reminders:t]
    ^-  reminders:t
    =/  next-rank=@ud  1.024
    |-
    ?~  ordered  rems
    =/  rem=reminder:t  i.ordered
    =/  next-rems=reminders:t
      ?:  =(rank.rem next-rank)  rems
      =/  changed=reminder:t
        rem(rank next-rank, revision +(revision.rem), modified-at now)
      (~(put by rems) id.changed changed)
    $(ordered t.ordered, rems next-rems, next-rank (add next-rank 1.024))
  ++  save-list
    |=  [=op-id:t lis=task-list:t st=store:t]
    ^-  [(list update:t) store:t]
    (save-list-with-id op-id lis next-id.st st)
  ++  save-list-with-id
    |=  [=op-id:t lis=task-list:t next=@ud st=store:t]
    ^-  [(list update:t) store:t]
    =/  liss=lists:t  (~(put by list-map.st) id.lis lis)
    =/  interim=store:t  st(list-map liss)
    =/  snoozes=snoozes:t
      (valid-snoozes (visible-lists interim) snooze-map.st)
    =/  nex=store:t
      :*  %1
          next
          liss
          receipt-map.st
          preferences.st
          timer-generation.st
          next-wake.st
          snoozes
          share-map.st
          replica-map.st
          invitation-map.st
          in-flight-map.st
          host-session.st
          peer-session-map.st
          liveness-generation.st
          notification-map.st
          replica-alert-set.st
          receipt-order.st
          hosted-activity-map.st
          replica-activity-map.st
          list-order.st
          presentation-map.st
          collaboration-policy-map.st
          collaboration-notification-map.st
          transfer-map.st
          transfer-reservation-map.st
          transfer-import-map.st
      ==
    (commit op-id [%list-upserted op-id lis preferences.st] nex)
  ++  valid-snoozes
    |=  [liss=lists:t values=snoozes:t]
    ^-  snoozes:t
    =/  kept=(list [snooze-key:t @da])
      %+  skim  ~(tap by values)
      |=  [key=snooze-key:t until=@da]
      =/  lis=(unit task-list:t)  (~(get by liss) -.key)
      ?~  lis  %.n
      =/  rem=(unit reminder:t)  (~(get by reminders.u.lis) +.key)
      ?~  rem  %.n
      &(!completed.u.rem !?=(~ schedule.u.rem))
    (malt kept)
  ++  reject
    |=  [=op-id:t reason=@tas current=(unit @ud) st=store:t]
    ^-  [(list update:t) store:t]
    (commit op-id [%rejected op-id reason current] st)
  ++  commit
    |=  [=op-id:t upd=update:t nex=store:t]
    ^-  [(list update:t) store:t]
    (arm-timer (give upd) nex(receipt-map (~(put by receipt-map.nex) op-id upd)))
  ++  give
    |=  upd=update:t
    ^-  (list update:t)
    [upd ~]
  ++  arm-timer
    |=  [events=(list update:t) st=store:t]
    ^-  [(list update:t) store:t]
    =/  wake=(unit @da)
      (earlier (earliest-wake list-map.st) (earliest-snooze snooze-map.st))
    [events st(next-wake wake)]
  ++  earliest-snooze
    |=  values=snoozes:t
    ^-  (unit @da)
    =/  entries=(list [snooze-key:t @da])  ~(tap by values)
    =/  result=(unit @da)  ~
    |-
    ?~  entries  result
    =.  result  (earlier result `+.i.entries)
    $(entries t.entries)
  ++  earliest-wake
    |=  liss=lists:t
    ^-  (unit @da)
    =/  entries=(list [list-id:t task-list:t])  ~(tap by liss)
    =/  result=(unit @da)  ~
    |-
    ?~  entries  result
    =.  result  (earlier result (earliest-reminders reminders.+.i.entries))
    $(entries t.entries)
  ++  earliest-reminders
    |=  rems=reminders:t
    ^-  (unit @da)
    =/  entries=(list [reminder-id:t reminder:t])  ~(tap by rems)
    =/  result=(unit @da)  ~
    |-
    ?~  entries  result
    =.  result  (earlier result (reminder-wake +.i.entries))
    $(entries t.entries)
  ++  reminder-wake
    |=  rem=reminder:t
    ^-  (unit @da)
    ?:  completed.rem  ~
    ?~  schedule.rem  ~
    =/  sch=schedule:t  u.schedule.rem
    =/  offsets=(set @ud)  (~(put in early-seconds.sch) 0)
    =/  pending=(list @ud)
      %+  skim  ~(tap in offsets)
      |=  seconds=@ud
      !(~(has in alerted-offsets.sch) seconds)
    =/  result=(unit @da)  ~
    |-
    ?~  pending  result
    =/  delta=@dr  (mul i.pending ~s1)
    =/  candidate=@da
      ?:((lte due-at.sch delta) `@da`0 (sub due-at.sch delta))
    =.  result  (earlier result `candidate)
    $(pending t.pending)
  ++  earlier
    |=  [a=(unit @da) b=(unit @da)]
    ^-  (unit @da)
    ?~  a  b
    ?~  b  a
    ?:((lth u.a u.b) a b)
  --
--
