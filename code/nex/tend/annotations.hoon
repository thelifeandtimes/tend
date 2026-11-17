:: Tend's fresh Grubbery application nexus. There is no %tend Gall adapter.
:: state.noun is both the sole mutation writer and the transaction's stored noun.
:: HTTP request grubs have their own lifetimes; they cannot write canonical data.
/<  n  /lib/tend-native.hoon
/<  wire  /lib/tend-wire.hoon
/<  clock-rules  /lib/tend-clock.hoon
/<  tests  /lib/tend-native-tests.hoon
/<  ui  index.html
=<  ^-  nexus:nexus
|%
++  on-load
  |=  old=ball:tarball
  ^-  bole:tarball
  %+  spin:loader  old
  :~  (manifest:loader 1)
      [%over %& [/ %'link.json'] [[/ %json] (pairs:enjs:format ~[['name' s+'tend-notes']])]]
      [%over %& [/ %'tile.json'] [[/ %json] (pairs:enjs:format ~[['title' s+'Tend Notes'] ['href' s+'/apps/tend-notes'] ['info' s+'Native shared lists preview']])]]
      [%over %& [/ %'protocol.json'] [[/ %json] (pairs:enjs:format ~[['protocol' s+'tend-native-1'] ['writer' s+'state.noun'] ['reads' a+~[s+'snapshot' s+'receipt' s+'private']] ['watch' s+'authenticated-http-poll']])]]
      [%over %& [/ %'weir.json'] [[/ %json] (need (de:json:html '{"poke":[{"road":"/sys/bowl.sig","why":"Authenticate local ship identity and clock"},{"road":"/sys/eyre/","why":"Serve the signed-in Tend client"},{"road":"/sys/behn/","why":"Recover durable reminder deadlines after reload"}]}'))]]
      :: Poke access is only an admission channel. Every command is checked in
      :: the writer against the authenticated transport origin. Never grant a
      :: directory containing state.noun, private data, or receipts for peek.
      [%over %& [/ %'groups.json'] [[/ %json] (need (de:json:html '{}'))]]
      [%fall %& [/ %'state.noun'] [[/ %noun] fresh:n]]
      [%fall %& [/ %'clock.sig'] [[/ %sig] ~]]
      [%fall %& [/ %'http.sig'] [[/ %sig] ~]]
      [%over %& [/ %'index.html'] [[/ %mime] ui]]
      :: Requests are ephemeral. Clearing them on reload does not lose a
      :: committed operation: its actor-scoped receipt is in state.noun.
      [%over %| /requests empty-dir:loader]
  ==
++  on-file
  |=  [rail=rail:tarball blot=blot:tarball]
  ^-  spool:fiber:nexus
  |=  prod=prod:fiber:nexus
  =/  m  (fiber:fiber:nexus ,~)
  ^-  process:fiber:nexus
  ?+  rail  stay:m
    [~ %'state.noun']
      ;<  ~  bind:m  (rise-wait:io prod "Tend writer")
      writer
    [~ %'clock.sig']
      ;<  ~  bind:m  (rise-wait:io prod "Tend clock")
      ticker
    [~ %'http.sig']
      ;<  ~  bind:m  (rise-wait:io prod "Tend HTTP")
      ;<  ~  bind:m  (bind-http-self:io [~ /apps/tend-notes])
      dispatch
    [[%requests ~] @]
      ;<  ~  bind:m  (rise-wait:io prod "Tend request")
      (serve name.rail)
  ==
--
|%
+$  request
  $%  [%command command=command:n]
      [%snapshot ~]
      [%private ~]
      [%receipt id=@t]
  ==
++  writer
  =/  m  (fiber:fiber:nexus ,~)
  ;<  our=@p  bind:m  get-our:io
  |-
  ;<  [from=from:fiber:nexus sage=sage:tarball]  bind:m  take-poke-from:io
  ?:  =([/tend %'wake-1'] p.sage)
    ?.  &(=(0 p.from) =([/ %'clock.sig'] q.from))  $
    ;<  now=@da  bind:m  get-time:io
    ;<  st=state:n  bind:m  (get-state-as:io ,state:n)
    ;<  ~  bind:m  (replace:io st(product (tick:clock-rules now product.st)))
    $
  ?.  =([/tend %'request-1'] p.sage)  $
  ?:  (gth (met 3 (jam q.q.sage)) 1.048.576)  $
  =/  req=(unit request)  (mole |.(;;(request q.q.sage)))
  ?~  req  $
  =/  foreign=(unit @p)  (get-poke-src:io from)
  :: The only local caller is a request fiber created by our authenticated
  :: dispatcher. Neither a peer nor another local grub can supply an actor.
  ?~  foreign
    ?.  &(=(0 p.from) ?=([[%requests ~] @] q.from))  $
    (handle our our from u.req)
  :: No peer admission in this independent, personal-ship annotation nexus.
  $
++  handle
  |=  [our=@p actor=@p from=from:fiber:nexus req=request]
  =/  m  (fiber:fiber:nexus ,~)
  ;<  st=state:n  bind:m  (get-state-as:io ,state:n)
  =/  [next=state:n response=json]
    ?-  -.req
      %command
        :: Clock is supplied by the writer below, never taken from JSON.
        [st ~]
      %snapshot  [st (snapshot:n our actor st)]
      %private  [st (private-view:n our actor st)]
      %receipt  [st (fall (read-receipt:n actor id.req st) ~)]
    ==
  ;<  now=@da  bind:m  get-time:io
  =/  pair=[next=state:n response=json]
    ?:  ?=(%command -.req)  ?:  !|(?=(%annotate -.command.req) ?=(%forget-annotation -.command.req))
        [st (result:n (operation:n command.req) %.n %annotation-only)]
      (transact:n our actor now command.req st)
    [next response]
  :: This replace is the commit boundary: lists, both sides of a local transfer,
  :: and its receipt enter the same saved noun before any success is sent.
  ;<  ~  bind:m  (replace:io next.pair)
  ;<  *  bind:m  (poke-soft:io [%| from] [[/ %json] response.pair])
  writer
++  ticker
  =/  m  (fiber:fiber:nexus ,~)
  ;<  *  bind:m  (keep:io /deadline [%| 0 %& / %'state.noun'] ~)
  |-
  ;<  st=(unit state:n)  bind:m  (peek-as:io [%| 0 %& / %'state.noun'] ,state:n)
  ?~  st
    ;<  *  bind:m  (take-news:io /deadline)
    $
  ?~  next-wake.product.u.st
    ;<  *  bind:m  (take-news:io /deadline)
    $
  ;<  now=@da  bind:m  get-time:io
  ;<  ~  bind:m
    ?:  (lte u.next-wake.product.u.st now)  (pure:m ~)
    :: A changed schedule can advance a deadline. Waking at most once a second
    :: bounds that delay; pending deadlines themselves stay in canonical state.
    (wait:io (min u.next-wake.product.u.st (add now ~s1)))
  ;<  *  bind:m  (poke:io [%| 0 %& / %'state.noun'] [[/tend %'wake-1'] ~])
  $
++  dispatch
  =/  m  (fiber:fiber:nexus ,~)
  |-
  ;<  [from=from:fiber:nexus sage=sage:tarball]  bind:m  take-poke-from:io
  ?.  =(*from:fiber:nexus from)  $
  ?+  name.p.sage  $
    %handle-http-request
      =/  [id=@ta src=@p req=inbound-request:eyre]
        !<([@ta @p inbound-request:eyre] q.sage)
      ;<  ~  bind:m  (over:io [%| 0 %& /requests id] [[/ %noun] [src req]])
      $
    %handle-http-cancel
      =/  id=@ta  !<(@ta q.sage)
      ;<  *  bind:m  (cull:io [%| 0 %& /requests id])
      $
  ==
++  serve
  |=  id=@ta
  =/  m  (fiber:fiber:nexus ,~)
  ;<  [src=@p req=inbound-request:eyre]  bind:m  (get-state-as:io ,[@p inbound-request:eyre])
  ;<  our=@p  bind:m  get-our:io
  ?.  authenticated.req  (reply id 401 s+'Sign in to your ship')
  ?.  =(src our)  (reply id 403 s+'Owner session required')
  =/  [site=path args=quay:eyre]  (parse-url:http-utils url.request.req)
  =/  suffix=path  (slag 2 site)
  ?:  =(~ suffix)
    ;<  v=view:nexus  bind:m  (peek:io [%| 1 %& / %'index.html'] ~)
    ?>  ?=(%file -.v)
    (send-simple:http-res:io id (mime-response:http-utils !<(mime (need-vase:tarball sang.v))))
  ?:  =(/api/v1/self-test suffix)  (reply id 200 (numb:enjs:format (run:tests ~)))
  ?:  =(/api/v1/whoami suffix)  (reply id 200 s+(scot %p our))
  =/  message=(unit request)
    ?:  =(/api/v1/state suffix)  `[%snapshot ~]
    ?:  =(/api/v1/private suffix)  `[%private ~]
    ?:  ?=([%api %v1 %receipt @ ~] suffix)  `[%receipt i.t.t.t.suffix]
    ?.  =(/api/v1/command suffix)  ~
    ?.  =(%'POST' method.request.req)  ~
    ?~  body.request.req  ~
    ?:  (gth p.u.body.request.req 1.048.576)  ~
    =/  j=(unit json)  (de:json:html q.u.body.request.req)
    ?~  j  ~
    =/  cmd=(unit command:n)  (mole |.((decode:wire u.j)))
    ?~  cmd  ~
    `[%command u.cmd]
  ?~  message  (reply id 400 s+'Invalid native request')
  ;<  *  bind:m  (poke:io [%| 1 %& / %'state.noun'] [[/tend %'request-1'] u.message])
  ;<  [from=from:fiber:nexus sage=sage:tarball]  bind:m  take-poke-from:io
  ?>  &(=(1 p.from) =([/ %'state.noun'] q.from) =([/ %json] p.sage))
  (reply id 200 ;;(json q.q.sage))
++  reply
  |=  [id=@ta status=@ud j=json]
  (send-simple:http-res:io id [[status ~[['content-type' 'application/json'] ['cache-control' 'no-store']]] `(as-octs:mimes:html (en:json:html j))])
--
