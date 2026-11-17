:: Native HTTP representation, revision 1. No legacy mark is repurposed.
/<  n  /lib/tend-native.hoon
/<  tj  /lib/tend-json.hoon
|%
++  decode
  |=  j=json
  ^-  command:n
  =,  dejs:format
  =/  kind=@t  ((ot ~[[%kind so]]) j)
  ?:  =('edit' kind)
    [%edit ((ot ~[[%action action:dejs:tj]]) j)]
  ?:  =('grant' kind)
    =/  fields=[@t @ud @ud @p ?]
      ((ot ~[[%operation-id so] [%list-id ni] [%base-revision ni] [%ship ship:dejs:tj] [%can-invite bo]]) j)
    [%grant fields]
  ?:  =('revoke' kind)
    =/  fields=[@t @ud @ud @p]
      ((ot ~[[%operation-id so] [%list-id ni] [%base-revision ni] [%ship ship:dejs:tj]]) j)
    [%revoke fields]
  ?:  =('forget-annotation' kind)
    =/  fields=[@t @t @ud]
      ((ot ~[[%operation-id so] [%key so] [%base-revision ni]]) j)
    [%forget-annotation fields]
  ?>  =('annotate' kind)
  =/  fields=[@t @t reference:n @t ? @ud]
    ((ot ~[[%operation-id so] [%key so] [%reference reference] [%title so] [%completed bo] [%base-revision ni]]) j)
  [%annotate fields]
++  reference
  |=  j=json
  ^-  reference:n
  =,  dejs:format
  =/  fields=[host=@p app=@t resource=(list @t)]
    ((ot ~[[%host ship:dejs:tj] [%app so] [%resource (ar so)]]) j)
  ?>  &(!=('' app.fields) (lte (met 3 app.fields) 128) (lte (lent resource.fields) 16))
  ?>  (levy resource.fields |=(part=@t &(!=('' part) (lte (met 3 part) 256))))
  fields
--
