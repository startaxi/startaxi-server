#!/usr/bin/env bash

ORDER=$(cat << 'EndOfMessage'
{
  "providerId": "blue-taxi",
  "userPosition": {
    "coordinates": {
      "lon": 25.270403,
      "lat": 54.688723
    }
  },
  "destination": {
    "id": 1,
    "coordinates": {
      "lon": 25.21243,
      "lat": 54.678241
    }
  }
}
EndOfMessage
)

curl http://localhost:8080/api/taxi/order -H "Content-Type:application/json" -X POST --data "$ORDER"
