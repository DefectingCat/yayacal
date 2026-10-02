#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

image=""
if [ "${1:-}" = "--image" ] && [ "$#" = 2 ]; then
  image=$2
elif [ "$#" != 0 ]; then
  echo "Usage: bash tests/run.sh [--image IMAGE]" >&2
  exit 2
fi
postgres_mode=${MOMENTS_TEST_POSTGRES:-docker}
if [ "$postgres_mode" != docker ] && [ "$postgres_mode" != local ]; then
  echo "MOMENTS_TEST_POSTGRES must be docker or local" >&2
  exit 2
fi
if [ -n "$image" ] && [ "$postgres_mode" = local ]; then
  echo "Image tests require disposable Docker PostgreSQL" >&2
  exit 2
fi
if [ "$postgres_mode" = docker ]; then
  command -v docker >/dev/null || { echo "Docker is required; native tests can use MOMENTS_TEST_POSTGRES=local" >&2; exit 1; }
fi

test_dir=$(mktemp -d)
test_db="yayacal-test-db-$$"
test_api="yayacal-test-api-$$"
test_network="yayacal-test-network-$$"
server_pid=""
local_db_started=""
network_created=""
db_created=""
api_created=""
cleanup() {
  status=$?
  if [ "$status" != 0 ]; then
    if [ -n "$api_created" ]; then docker logs "$test_api" 2>&1 || true; fi
    if [ -f "$test_dir/server.log" ]; then cat "$test_dir/server.log"; fi
    if [ -f "$test_dir/postgres.log" ]; then cat "$test_dir/postgres.log"; fi
  fi
  if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; wait "$server_pid" 2>/dev/null || true; fi
  if [ -n "$local_db_started" ]; then pg_ctl -D "$test_dir/postgres" -m immediate -w stop >/dev/null 2>&1 || true; fi
  if [ -n "$api_created" ]; then docker rm -fv "$test_api" >/dev/null 2>&1 || true; fi
  if [ -n "$db_created" ]; then docker rm -fv "$test_db" >/dev/null 2>&1 || true; fi
  if [ -n "$network_created" ]; then docker network rm "$test_network" >/dev/null 2>&1 || true; fi
  rm -rf "$test_dir"
}
trap cleanup EXIT

free_port() {
  python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()'
}

if [ "$postgres_mode" = local ]; then
  db_port=$(free_port)
  initdb -D "$test_dir/postgres" -U moments --auth=trust >/dev/null
  local_db_started=1
  pg_ctl -D "$test_dir/postgres" -l "$test_dir/postgres.log" \
    -o "-h 127.0.0.1 -p $db_port -k $test_dir" -w -t 30 start >/dev/null
  createdb -h 127.0.0.1 -p "$db_port" -U moments moments
else
  docker network create "$test_network" >/dev/null
  network_created=1
  docker run -d --name "$test_db" --network "$test_network" --network-alias db \
    -e POSTGRES_DB=moments -e POSTGRES_USER=moments -e POSTGRES_PASSWORD=integration-test \
    -p 127.0.0.1::5432 postgres:18.6-alpine >/dev/null
  db_created=1
  db_port=$(docker port "$test_db" 5432 | head -1 | cut -d: -f2)
  db_ready=""
  for _ in {1..80}; do
    if docker exec "$test_db" pg_isready -h 127.0.0.1 -U moments -d moments >/dev/null 2>&1; then db_ready=1; break; fi
    sleep 0.5
  done
  if [ -z "$db_ready" ]; then docker logs "$test_db"; echo "Test PostgreSQL did not become ready" >&2; exit 1; fi
fi

if [ -n "$image" ]; then
  docker run -d --name "$test_api" --network "$test_network" \
    -e DATABASE_URL=postgres://moments:integration-test@db/moments \
    -e BIND_ADDR=0.0.0.0:8088 -e MEDIA_DIR=/data/media \
    -e RUST_LOG=yayacal_server=info,tower_http=info \
    -p 127.0.0.1::8088 "$image" >/dev/null
  api_created=1
  api_port=$(docker port "$test_api" 8088 | head -1 | cut -d: -f2)
else
  profile=${MOMENTS_TEST_PROFILE:-debug}
  build_args=(--locked)
  case "$profile" in
    debug) ;;
    release) build_args+=(--release) ;;
    *) echo "MOMENTS_TEST_PROFILE must be debug or release" >&2; exit 2 ;;
  esac
  cargo build "${build_args[@]}"
  api_port=$(free_port)
  DATABASE_URL="postgres://moments:integration-test@127.0.0.1:$db_port/moments" \
    MEDIA_DIR="$test_dir/media" BIND_ADDR="127.0.0.1:$api_port" \
    RUST_LOG=yayacal_server=info,tower_http=info \
    "${CARGO_TARGET_DIR:-target}/$profile/yayacal-server" > "$test_dir/server.log" 2>&1 &
  server_pid=$!
fi

api_ready=""
for _ in {1..80}; do
  if curl -fsS "http://127.0.0.1:$api_port/health" >/dev/null 2>&1; then api_ready=1; break; fi
  if [ -n "$server_pid" ] && ! kill -0 "$server_pid" 2>/dev/null; then exit 1; fi
  sleep 0.5
done
if [ -z "$api_ready" ]; then echo "Test server did not become ready" >&2; exit 1; fi

# 用实际响应头检查启动日志；CI 另传入期望值，防止日志和响应头一起使用错误 hash。
server_identity=$(python3 -c 'import sys, urllib.request; print(urllib.request.urlopen(sys.argv[1]).headers["X-Server"])' "http://127.0.0.1:$api_port/health")
if [ -n "$image" ]; then docker logs "$test_api" > "$test_dir/server.log" 2>&1; fi
SERVER_IDENTITY="$server_identity" python3 - "$test_dir/server.log" <<'PY'
import os
import pathlib
import sys
first_line = pathlib.Path(sys.argv[1]).read_text().splitlines()[0]
assert os.environ["SERVER_IDENTITY"] in first_line, first_line
print("Startup identity:", os.environ["SERVER_IDENTITY"])
PY
MOMENTS_TEST_URL="http://127.0.0.1:$api_port" python3 tests/api.py
