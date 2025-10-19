#!/bin/bash
set -e

docker pull paidyinc/one-frame
docker compose down
exec docker compose up --build --force-recreate
