#!/usr/bin/env bash

PROVIDER=$(cat << 'EndOfMessage'
{
  "coordinates": {
    "lon": 25.270403,
    "lat": 54.688723
  }
}
EndOfMessage
)

curl http://localhost:8080/api/taxi/provider -H "Content-Type:application/json" -X GET --data "$PROVIDER"
