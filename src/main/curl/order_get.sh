#!/usr/bin/env bash

curl http://localhost:8080/api/taxi/order/$1 -H "Content-Type:application/json" -X GET
