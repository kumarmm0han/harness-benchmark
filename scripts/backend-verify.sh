#!/usr/bin/env bash
set -euo pipefail
name="sop-verify-${RANDOM}-$$"
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT
docker run -d --name "$name" -e POSTGRES_DB=sop -e POSTGRES_USER=sop -e POSTGRES_PASSWORD=local-demo-only -p 127.0.0.1::5432 --health-cmd='pg_isready -U sop -d sop' --health-interval=1s --health-timeout=3s --health-retries=60 postgres:17-alpine >/dev/null
for attempt in {1..90}; do
  if [ "$(docker inspect --format '{{.State.Health.Status}}' "$name")" = healthy ]; then break; fi
  if [ "$attempt" = 90 ]; then docker logs "$name"; exit 1; fi
  sleep 1
done
port=$(docker port "$name" 5432/tcp | cut -d: -f2)
DB_URL="jdbc:postgresql://localhost:$port/sop" mvn -f backend/pom.xml verify "$@"
