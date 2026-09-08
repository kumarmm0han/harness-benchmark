#!/usr/bin/env bash
# Run the backend **PostgreSQL** integration tests against a throwaway database and
# remove the container when done (NFR-041, DES-016). Unit tests (*Test) are run by
# `mvn test`; this script runs the *IT classes, which need a real PostgreSQL.
#
# Uses the same postgres:16-alpine image as the Compose stack so the database-specific
# behavior (constraints, transactions, JSONB) is exercised against the real engine.
set -uo pipefail

PORT="${SOPDEMO_VERIFY_DB_PORT:-15433}"
NAME="sopdemo-verify-db-$$"
IMAGE="postgres:16-alpine"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "==> starting throwaway PostgreSQL (host port $PORT)"
docker rm -f "$NAME" >/dev/null 2>&1 || true
if ! docker run -d --name "$NAME" \
    -e POSTGRES_DB=sopdemo \
    -e POSTGRES_USER=sopdemo \
    -e POSTGRES_PASSWORD=sopdemo \
    -p "${PORT}:5432" \
    "$IMAGE" >/dev/null; then
  echo "ERROR: could not start $IMAGE (is Docker available?)" >&2
  exit 1
fi

echo "==> waiting for PostgreSQL readiness (pg_isready)"
ready=0
for _ in $(seq 1 90); do
  if docker exec "$NAME" pg_isready -U sopdemo -d sopdemo >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
if [ "$ready" != "1" ]; then
  echo "ERROR: throwaway PostgreSQL did not become ready in 90s" >&2
  docker logs --tail 50 "$NAME" 2>&1 || true
  exit 1
fi

echo "==> running backend integration tests: mvn -f backend -Dtest='*IT'"
set +e
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${PORT}/sopdemo" \
SPRING_DATASOURCE_USERNAME=sopdemo \
SPRING_DATASOURCE_PASSWORD=sopdemo \
  mvn -q -B -f backend test -Dtest='*IT' -DfailIfNoTests=false
rc=$?
set -e
if [ "$rc" -ne 0 ]; then
  echo "==> integration tests FAILED (rc=$rc)" >&2
  exit "$rc"
fi
echo "==> integration tests PASSED"
