:: Run on a qualified disposable Grubbery instance through /api/v1/self-test.
:: These assertions execute the actual native transition code, not a replica.
:: They have not been run merely because the offline Python tests pass.
/<  n  /lib/tend-native.hoon
/<  t  /lib/tend-types.hoon
/<  clock  /lib/tend-clock.hoon
|%
++  run
  |=  ~
  ^-  @ud
  =/  st=state:n  fresh:n
  =/  create=command:n  [%edit [%create-list 'create' 'Shared chores']]
  =/  made=[st=state:n json=json]  (transact:n ~zod ~zod ~2026.11.17 create st)
  =.  st  st.made
  ?>  =(1 ~(wyt by list-map.product.st))
  =/  replay=[st=state:n json=json]  (transact:n ~zod ~zod ~2026.11.17 create st)
  ?>  =(st st.replay)
  =/  reused=[st=state:n json=json]
    (transact:n ~zod ~zod ~2026.11.17 [%edit [%create-list 'create' 'Different']] st)
  ?>  =(st st.reused)
  =/  denied=[st=state:n json=json]
    (transact:n ~zod ~nec ~2026.11.17 [%edit [%create-list 'peer-create' 'Forbidden']] st)
  ?>  =(st st.denied)
  =/  granted=[st=state:n json=json]
    (transact:n ~zod ~zod ~2026.11.17 [%grant 'grant' 1 1 ~nec %.n] st)
  =.  st  st.granted
  ?>  (readable:n ~zod ~nec 1 st)
  ?>  !(readable:n ~zod ~bud 1 st)
  =/  add=command:n  [%edit [%add-reminder 'add' 1 'Wash dishes' ~ 2]]
  =/  added=[st=state:n json=json]  (transact:n ~zod ~nec ~2026.11.17 add st)
  =.  st  st.added
  =/  lis=task-list:t  (~(got by list-map.product.st) 1)
  ?>  &(=(3 revision.lis) =(1 ~(wyt by reminders.lis)))
  :: Fork a side scenario: both lists and the import receipt share one commit.
  =/  destination=[st=state:n json=json]
    (transact:n ~zod ~zod ~2026.11.17 [%edit [%create-list 'destination' 'Other list']] st)
  =/  move=command:n  [%edit [%move-reminder-to-list 'move' 1 3 2 3 1]]
  =/  moved=[st=state:n json=json]
    (transact:n ~zod ~zod ~2026.11.17 move st.destination)
  =/  from=task-list:t  (~(got by list-map.product.st.moved) 1)
  =/  dest=task-list:t  (~(got by list-map.product.st.moved) 3)
  ?>  &(=(0 ~(wyt by reminders.from)) =(1 ~(wyt by reminders.dest)))
  ?>  &(=(4 revision.from) =(2 revision.dest))
  =/  repeated=[st=state:n json=json]
    (transact:n ~zod ~zod ~2026.11.17 move st.moved)
  ?>  =(st.moved st.repeated)
  :: Timer re-entry uses saved alerted offsets and stable notification IDs.
  =/  scheduled=[st=state:n json=json]
    (transact:n ~zod ~zod ~2026.11.17 [%edit [%set-schedule 'schedule' 1 2 `[~2026.11.17..00.00.01 %.n 'UTC' ~ ~] 3]] st)
  =/  fired=store:t  (tick:clock ~2026.11.17..00.00.02 product.st.scheduled)
  ?>  =(1 ~(wyt by notification-map.fired))
  ?>  =(fired (tick:clock ~2026.11.17..00.00.02 fired))
  =/  note=command:n
    [%annotate 'note' 'personal' [~zod 'tend' /lists/1/tasks/2] 'My private step' %.n 0]
  =/  annotated=[st=state:n json=json]  (transact:n ~zod ~nec ~2026.11.17 note st)
  =.  st  st.annotated
  ?>  (reference-available:n ~zod ~nec [~zod 'tend' /lists/1/tasks/2] st)
  ?>  =(0 (lent p:(private-view:n ~zod ~bud st)))
  =/  revoked=[st=state:n json=json]
    (transact:n ~zod ~zod ~2026.11.17 [%revoke 'revoke' 1 3 ~nec] st)
  =.  st  st.revoked
  ?>  !(readable:n ~zod ~nec 1 st)
  ?>  (~(has by additions.st) [~nec 'personal'])
  ?>  !(reference-available:n ~zod ~nec [~zod 'tend' /lists/1/tasks/2] st)
  =/  edited=[st=state:n json=json]
    (transact:n ~zod ~nec ~2026.11.17 [%annotate 'after-revoke' 'personal' [~zod 'tend' /lists/1/tasks/2] 'Still mine' %.y 1] st)
  =.  st  st.edited
  ?>  =(2 revision:(~(got by additions.st) [~nec 'personal']))
  =/  deleted=[st=state:n json=json]
    (transact:n ~zod ~zod ~2026.11.17 [%edit [%delete-list 'delete' 1 4]] st)
  =.  st  st.deleted
  ?>  =(0 ~(wyt by list-map.product.st))
  ?>  (~(has by additions.st) [~nec 'personal'])
  :: Serialize and reload the saved noun without a migration: data and receipts
  :: must survive byte-for-byte. The loader's %fall row covers this same noun.
  =/  restored=state:n  ;;(state:n (cue (jam st)))
  ?>  =(st restored)
  ?>  ?=(^ (read-receipt:n ~nec 'add' restored))
  ?>  ?=(~ (read-receipt:n ~bud 'add' restored))
  23
--
