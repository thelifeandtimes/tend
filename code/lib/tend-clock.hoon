:: Pure timer recovery. Pending notifications and alerted offsets are saved
:: with the reminders; a repeated/recovered wake cannot mint another alert.
/<  t  /lib/tend-types.hoon
|%
++  tick
  |=  [now=@da st=store:t]
  ^-  store:t
  |^
  =/  [lists=lists:t notices=notifications:t events=(list update:t)]
    (fire-lists list-map.st notification-map.st now)
  =/  [snoozes=snoozes:t notices=notifications:t events=(list update:t)]
    (fire-snoozes snooze-map.st lists notices now)
  =/  wake=(unit @da)  (earlier (earliest-wake lists) (earliest-snooze snoozes))
  st(list-map lists, snooze-map snoozes, notification-map notices, next-wake wake)
  ++  fire-lists
    |=  [liss=lists:t notices=notifications:t now=@da]
    ^-  [lists:t notifications:t (list update:t)]
    =/  entries=(list [list-id:t task-list:t])  ~(tap by liss)
    =/  result=lists:t  *lists:t
    =/  cards=(list update:t)  ~
    |-
    ?~  entries  [result notices cards]
    =/  lis=task-list:t  +.i.entries
    =/  [rems=reminders:t notices=notifications:t emitted=(list update:t)]
      (fire-reminders id.lis reminders.lis notices now)
    =/  next-list=task-list:t  lis(reminders rems)
    =.  result  (~(put by result) id.lis next-list)
    =.  cards  (weld cards emitted)
    $(entries t.entries)
  ++  fire-reminders
    |=  [=list-id:t rems=reminders:t notices=notifications:t now=@da]
    ^-  [reminders:t notifications:t (list update:t)]
    =/  entries=(list [reminder-id:t reminder:t])  ~(tap by rems)
    =/  result=reminders:t  *reminders:t
    =/  cards=(list update:t)  ~
    |-
    ?~  entries  [result notices cards]
    =/  rem=reminder:t  +.i.entries
    ?~  schedule.rem
      =.  result  (~(put by result) id.rem rem)
      $(entries t.entries)
    =/  sch=schedule:t  u.schedule.rem
    =/  offsets=(list @ud)  (due-offsets rem now)
    ?~  offsets
      =.  result  (~(put by result) id.rem rem)
      $(entries t.entries)
    =/  next-sch=schedule:t
      sch(alerted-offsets (~(gas in alerted-offsets.sch) offsets))
    =/  next-rem=reminder:t  rem(schedule (some next-sch))
    =/  [next-notices=notifications:t emitted=(list update:t)]
      (queue-offsets list-id id.rem due-at.sch offsets notices now)
    =.  notices  next-notices
    =.  result  (~(put by result) id.rem next-rem)
    =.  cards  (weld cards emitted)
    $(entries t.entries)
  ++  due-offsets
    |=  [rem=reminder:t now=@da]
    ^-  (list @ud)
    ?:  completed.rem  ~
    ?~  schedule.rem  ~
    =/  sch=schedule:t  u.schedule.rem
    =/  offsets=(set @ud)  (~(put in early-seconds.sch) 0)
    %+  skim  ~(tap in offsets)
    |=  seconds=@ud
    ?.  !(~(has in alerted-offsets.sch) seconds)  %.n
    =/  delta=@dr  (mul seconds ~s1)
    =/  candidate=@da
      ?:((lte due-at.sch delta) `@da`0 (sub due-at.sch delta))
    (lte candidate now)
  ++  queue-offsets
    |=  $:  =list-id:t
            =reminder-id:t
            due-at=@da
            offsets=(list @ud)
            notices=notifications:t
            now=@da
        ==
    ^-  [notifications:t (list update:t)]
    =/  remaining=(list @ud)  offsets
    =/  cards=(list update:t)  ~
    |-
    ?~  remaining  [notices cards]
    =/  [next-notices=notifications:t notification-card=update:t]
      (queue-alert list-id reminder-id due-at i.remaining %.n due-at notices now)
    =.  notices  next-notices
    =.  cards  [notification-card cards]
    $(remaining t.remaining)
  ++  queue-alert
    |=  $:  =list-id:t
            =reminder-id:t
            due-at=@da
            early-seconds=@ud
            snoozed=?
            salt=@da
            notices=notifications:t
            now=@da
        ==
    ^-  [notifications:t update:t]
    =/  id=op-id:t
      (scot %uv (sham [list-id reminder-id due-at early-seconds snoozed salt]))
    =/  note=notification:t
      [id list-id reminder-id due-at early-seconds snoozed now]
    =/  upd=update:t
      [%alert id list-id reminder-id due-at early-seconds snoozed]
    =/  card=update:t  upd
    [(~(put by notices) id note) card]
  ++  fire-snoozes
    |=  [values=snoozes:t liss=lists:t notices=notifications:t now=@da]
    ^-  [snoozes:t notifications:t (list update:t)]
    =/  entries=(list [snooze-key:t @da])  ~(tap by values)
    =/  remaining=snoozes:t  *snoozes:t
    =/  cards=(list update:t)  ~
    |-
    ?~  entries  [remaining notices cards]
    =/  key=snooze-key:t  -.i.entries
    =/  until=@da  +.i.entries
    ?:  (gth until now)
      =.  remaining  (~(put by remaining) key until)
      $(entries t.entries)
    =/  lis=(unit task-list:t)  (~(get by liss) -.key)
    ?~  lis  $(entries t.entries)
    =/  rem=(unit reminder:t)  (~(get by reminders.u.lis) +.key)
    ?~  rem  $(entries t.entries)
    ?:  completed.u.rem  $(entries t.entries)
    ?~  schedule.u.rem  $(entries t.entries)
    =/  sch=schedule:t  u.schedule.u.rem
    =/  [next-notices=notifications:t notification-card=update:t]
      (queue-alert -.key +.key due-at.sch 0 %.y until notices now)
    =.  notices  next-notices
    =.  cards  [notification-card cards]
    $(entries t.entries)
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
