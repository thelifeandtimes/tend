:: Native authority and transaction boundary. This library has no effects.
:: A single writer persists the returned state before answering its caller.
/<  t  /lib/tend-types.hoon
/<  rules  /lib/tend-core.hoon
/<  tj  /lib/tend-json.hoon
|%
+$  reference  [host=@p app=@t resource=path]
+$  addition  [target=reference title=@t completed=? revision=@ud]
+$  command
  $%  [%edit action=action:t]
      [%grant operation=@t list=@ud base=@ud member=@p can-invite=?]
      [%revoke operation=@t list=@ud base=@ud member=@p]
      [%annotate operation=@t key=@t target=reference title=@t completed=? base=@ud]
      [%forget-annotation operation=@t key=@t base=@ud]
  ==
+$  receipt  [command=command accepted=? reason=@tas]
+$  state
  $:  version=%1
      product=store:t
      receipts=(map [@p @t] receipt)
      :: Host-stored private additions: no foreign read grant covers this map.
      :: This is private from participants; the hosting ship owner can read it.
      additions=(map [@p @t] addition)
  ==
++  fresh  ^-  state  [%1 fresh:rules ~ ~]
++  operation
  |=  cmd=command
  ^-  @t
  ?-  -.cmd
    %edit  op-id.action.cmd
    %grant  operation.cmd
    %revoke  operation.cmd
    %annotate  operation.cmd
    %forget-annotation  operation.cmd
  ==
++  readable
  |=  [our=@p actor=@p id=@ud st=state]
  ^-  ?
  ?.  (~(has by list-map.product.st) id)  %.n
  ?:  =(our actor)  %.y
  =/  share=(unit share:t)  (~(get by share-map.product.st) id)
  ?~  share  %.n
  (~(has by members.u.share) actor)
++  visible
  |=  [our=@p actor=@p st=state]
  ^-  lists:t
  %-  malt
  %+  skim  ~(tap by list-map.product.st)
  |=  [id=@ud list=task-list:t]
  (readable our actor id st)
++  admitted
  |=  [our=@p actor=@p cmd=command st=state]
  ^-  ?
  ?:  =(our actor)  %.y
  ?:  |(?=(%grant -.cmd) ?=(%revoke -.cmd))  %.n
  =/  member=?
    (lien ~(val by share-map.product.st) |=(share=share:t (~(has by members.share) actor)))
  ?:  member  %.y
  :: Losing shared access does not remove the right to tend an existing private
  :: addition. It does not admit new storage or edits to inaccessible lists.
  ?+  -.cmd  %.n
    %annotate  (~(has by additions.st) [actor key.cmd])
    %forget-annotation  (~(has by additions.st) [actor key.cmd])
  ==
++  transact
  |=  [our=@p actor=@p now=@da cmd=command st=state]
  ^-  [state json]
  =/  id=@t  (operation cmd)
  :: A new ID never replays another actor's operation. Keep the exact command,
  :: rather than only a short hash, to detect reuse with a different payload.
  ?:  |(=('' id) (gth (met 3 id) 256))
    [st (result id %.n %invalid-operation)]
  ?.  (admitted our actor cmd st)
    [st (result id %.n %not-authorized)]
  =/  prior=(unit receipt)  (~(get by receipts.st) [actor id])
  ?^  prior
    ?.  =(cmd command.u.prior)  [st (result id %.n %operation-id-reused)]
    [st (result id accepted.u.prior reason.u.prior)]
  :: Refuse admission when full. Never silently evict a receipt and later apply
  :: a forgotten operation a second time. Compaction needs its own contract.
  ?:  (gte ~(wyt by receipts.st) 10.000)
    [st (result id %.n %receipt-capacity)]
  =/  [next=state accepted=? reason=@tas]
    ?-  -.cmd
      %edit
        =/  pair=[events=(list update:t) next=store:t]
          (apply:rules our actor now action.cmd product.st(receipt-map ~, receipt-order ~))
        =/  rejection=(list update:t)
          (skim events.pair |=(event=update:t ?=(%rejected -.event)))
        ?^  rejection  [st %.n reason.i.rejection]
        :: Scratch receipts support the product transition; the native ledger
        :: below is the sole persistent deduplication boundary.
        [st(product next.pair(receipt-map ~, receipt-order ~)) %.y %ok]
      %grant
        ?.  =(our actor)  [st %.n %owner-required]
        =/  lis=(unit task-list:t)  (~(get by list-map.product.st) list.cmd)
        ?~  lis  [st %.n %unavailable]
        ?.  =(base.cmd revision.u.lis)  [st %.n %stale-list]
        ?:  =(member.cmd our)  [st %.n %invalid-member]
        =/  sh=share:t  (fall (~(get by share-map.product.st) list.cmd) *share:t)
        ?:  (gte ~(wyt by members.sh) 1.000)  [st %.n %member-capacity]
        =.  sh  sh(members (~(put by members.sh) member.cmd [can-invite.cmd %.y %.y]))
        =/  p=store:t  product.st
        =.  p  p(share-map (~(put by share-map.p) list.cmd sh))
        =.  p  p(list-map (~(put by list-map.p) list.cmd u.lis(revision +(revision.u.lis), modified-at now)))
        [st(product p) %.y %ok]
      %revoke
        ?.  =(our actor)  [st %.n %owner-required]
        =/  lis=(unit task-list:t)  (~(get by list-map.product.st) list.cmd)
        ?~  lis  [st %.n %unavailable]
        ?.  =(base.cmd revision.u.lis)  [st %.n %stale-list]
        =/  sh=share:t  (fall (~(get by share-map.product.st) list.cmd) *share:t)
        =.  sh  sh(members (~(del by members.sh) member.cmd), pending (~(del by pending.sh) member.cmd))
        =/  p=store:t  product.st
        =.  p  p(share-map (~(put by share-map.p) list.cmd sh))
        =.  p  p(list-map (~(put by list-map.p) list.cmd u.lis(revision +(revision.u.lis), modified-at now)))
        :: Do not cascade into additions. Their owner and lifetime are separate.
        [st(product p) %.y %ok]
      %annotate
        ?:  |(=('' key.cmd) (gth (met 3 key.cmd) 256) =('' title.cmd) (gth (met 3 title.cmd) 1.024))
          [st %.n %invalid-addition]
        ?:  |(=('' app.target.cmd) (gth (met 3 app.target.cmd) 128) (gth (lent resource.target.cmd) 16))
          [st %.n %invalid-reference]
        ?.  (levy resource.target.cmd |=(part=@ta &(!=('' part) (lte (met 3 part) 256))))
          [st %.n %invalid-reference]
        =/  old=(unit addition)  (~(get by additions.st) [actor key.cmd])
        ?:  &(?=(~ old) (gte ~(wyt by additions.st) 10.000))
          [st %.n %addition-capacity]
        ?.  =(base.cmd ?~(old 0 revision.u.old))  [st %.n %stale-addition]
        =/  note=addition  [target.cmd title.cmd completed.cmd +(base.cmd)]
        [st(additions (~(put by additions.st) [actor key.cmd] note)) %.y %ok]
      %forget-annotation
        =/  old=(unit addition)  (~(get by additions.st) [actor key.cmd])
        ?~  old  [st %.n %unavailable]
        ?.  =(base.cmd revision.u.old)  [st %.n %stale-addition]
        [st(additions (~(del by additions.st) [actor key.cmd])) %.y %ok]
    ==
  =/  saved=state
    next(receipts (~(put by receipts.next) [actor id] [cmd accepted reason]))
  [saved (result id accepted reason)]
++  result
  |=  [id=@t accepted=? reason=@tas]
  ^-  json
  (pairs:enjs:format ~[['protocol' s+'tend-native-1'] ['operation-id' s+id] ['outcome' s+?:(accepted 'committed' 'rejected')] ['reason' s+reason]])
++  read-receipt
  |=  [actor=@p id=@t st=state]
  ^-  (unit json)
  =/  held=(unit receipt)  (~(get by receipts.st) [actor id])
  ?~  held  ~
  `(result id accepted.u.held reason.u.held)
++  snapshot
  |=  [our=@p actor=@p st=state]
  ^-  json
  :: Never serialize the product's private preferences/snoozes to a member.
  =/  p=store:t  product.st
  =/  prefs=preferences:t  ?:(=(our actor) preferences.p preferences.fresh:rules)
  =/  snoozes=snoozes:t  ?:(=(our actor) snooze-map.p ~)
  (update:enjs:tj [%snapshot (visible our actor st) prefs snoozes])
++  private-view
  |=  [our=@p actor=@p st=state]
  ^-  json
  =/  notes=(list [key=[@p @t] note=addition])
    (skim ~(tap by additions.st) |=([key=[@p @t] note=addition] =(actor -.key)))
  :-  %a
  %+  turn  notes
  |=  [key=[@p @t] note=addition]
  =/  ref=reference  target.note
  =/  attached=?  (reference-available our actor ref st)
  (pairs:enjs:format ~[['key' [%s +.key]] ['title' s+title.note] ['completed' b+completed.note] ['revision' (numb:enjs:format revision.note)] ['reference' (reference-json ref)] ['availability' s+?:(attached 'available' ?:(=(host.ref our) 'unavailable' 'unresolved'))]])
++  reference-available
  |=  [our=@p actor=@p ref=reference st=state]
  ^-  ?
  ?.  &(=(host.ref our) =('tend' app.ref))  %.n
  =/  path=path  resource.ref
  ?.  ?=([%lists @ %tasks @ ~] path)  %.n
  =/  list=(unit @ud)  (slaw %ud i.t.path)
  =/  task=(unit @ud)  (slaw %ud i.t.t.t.path)
  ?.  &(?=(^ list) ?=(^ task))  %.n
  ?.  (readable our actor u.list st)  %.n
  =/  lis=task-list:t  (~(got by list-map.product.st) u.list)
  (~(has by reminders.lis) u.task)
++  reference-json
  |=  ref=reference
  ^-  json
  (pairs:enjs:format ~[['host' s+(scot %p host.ref)] ['app' s+app.ref] ['resource' a+(turn resource.ref |=(part=@ta s+part))]])
--
