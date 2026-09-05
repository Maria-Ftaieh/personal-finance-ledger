#!/usr/bin/env bash
#
# Returns the public demo to a known-good state.
#
# This is what makes the deployment a demo rather than a long-lived instance that happens
# to be called one. Two reasons to run it on a schedule:
#
#   - The seeded data is generated relative to today. Left alone, "this month" slides into
#     the past and the year-on-year comparison — the thing the application exists to show —
#     quietly stops having a current month to compare. A demo that rots is worse than no
#     demo.
#   - It is the backstop for everything else. Read-only mode should mean nothing can
#     change, but a demo that rebuilds itself nightly does not depend on that being true.
#
# The database is dropped and recreated rather than truncated, so Flyway replays every
# migration from nothing on each run. That means this doubles as a continuous check that
# the migrations actually work on an empty database.
#
# Usage: deploy/demo-reset.sh [deployment directory]

set -euo pipefail

DIR="${1:-/root/ledger}"
cd "$DIR"

API="http://127.0.0.1:8090"
COMPOSE="docker compose"

log() { printf '%s  %s\n' "$(date -u +%H:%M:%S)" "$*"; }

wait_for_api() {
  for _ in $(seq 1 60); do
    if curl -sf "$API/api/categories" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  log "FAILED: the application did not come up"
  return 1
}

log "resetting the demo in $DIR"

# The application holds connections, so it has to be down before the database can go.
log "stopping the application"
$COMPOSE stop app >/dev/null

log "recreating the database"
$COMPOSE exec -T db psql -U ledger -d postgres \
  -c "DROP DATABASE IF EXISTS ledger WITH (FORCE);" \
  -c "CREATE DATABASE ledger OWNER ledger;" >/dev/null

# Writable for exactly as long as it takes to seed. The shell environment takes precedence
# over .env, so this never edits the file — a reset that dies half way cannot leave the
# demo writable, because the next `up` reads .env again.
log "starting writable, replaying migrations"
LEDGER_DEMO_READ_ONLY=false $COMPOSE up -d --force-recreate app >/dev/null
wait_for_api

# Extend the seeded price index with anything TCMB has published since. Best effort: the
# checked-in CSV already covers every month the demo shows, so a TCMB outage is harmless.
log "refreshing the price index"
curl -sf -X POST "$API/api/cpi/refresh" 2>/dev/null | head -c 200 || log "  (TCMB unreachable, using the seeded index)"
echo

log "seeding demo data"
docker run --rm --network host -v "$DIR:/w" -w /w node:22-alpine \
  node ledger-web/scripts/generate-demo-data.mjs --api "$API" 2>&1 | tail -2

log "locking read-only"
$COMPOSE up -d --force-recreate app >/dev/null
wait_for_api

# Prove it, rather than assume it: a reset that silently left the demo writable would be
# worse than no reset at all.
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/api/cpi/refresh")
if [ "$code" != "403" ]; then
  log "FAILED: writes are not refused (POST /api/cpi/refresh returned $code)"
  exit 1
fi

months=$(curl -sf "$API/api/reports/months" | tr -cd '"' | wc -c)
log "done: writes refused, $((months / 2)) months of data"
