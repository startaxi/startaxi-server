#!/usr/bin/env bash

ESTIMATE=$(cat << 'EndOfMessage'
{
  "userPosition": {
    "coordinates": {
      "lon": 25.270403,
      "lat": 54.688723
    }
  },
  "destinations": [
    {
      "id": 1,
      "coordinates": {
        "lon": 25.21243,
        "lat": 54.678241
      }
    },
    {
      "id": 2,
      "coordinates": {
        "lon": 25.286632,
        "lat": 54.678296
      }
    }
  ]
}
EndOfMessage
)

curl http://localhost:8080/api/taxi/estimate -H "Content-Type:application/json" -X GET --data "$ESTIMATE"
