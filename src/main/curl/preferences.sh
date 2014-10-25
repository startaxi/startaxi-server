#!/usr/bin/env bash

PROVIDER=$(cat << EndOfMessage
{
  "orderId": $1,
  "radioStation": "Pūkas 2",
  "spotifyPlaylistId": 123,
  "chatMessage": "Kur tu?"
}
EndOfMessage
)

curl http://localhost:8080/api/taxi/order -H "Content-Type:application/json" -X PUT --data "$PROVIDER"