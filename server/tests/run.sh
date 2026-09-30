#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_dir=$(mktemp -d)
test_container="yayacal-moments-test-$$"
server_pid=""
cleanup() {
  if [ -n "$server_pid" ]; then kill "$server_pid" 2>/dev/null || true; wait "$server_pid" 2>/dev/null || true; fi
  docker rm -fv "$test_container" >/dev/null 2>&1 || true
  rm -rf "$test_dir"
}
trap cleanup EXIT
docker run -d --name "$test_container" -e POSTGRES_DB=moments -e POSTGRES_USER=moments \
  -e POSTGRES_PASSWORD=integration-test -p 127.0.0.1::5432 postgres:18.6-alpine >/dev/null
db_port=$(docker port "$test_container" 5432 | head -1 | cut -d: -f2)
for _ in {1..40}; do
  if docker exec "$test_container" pg_isready -U moments -d moments >/dev/null 2>&1; then break; fi
  sleep 0.5
done
cargo build --locked
api_port=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
DATABASE_URL="postgres://moments:integration-test@127.0.0.1:$db_port/moments" \
  MEDIA_DIR="$test_dir/media" BIND_ADDR="127.0.0.1:$api_port" \
  ./target/debug/yayacal-server > "$test_dir/server.log" 2>&1 &
server_pid=$!
for _ in {1..40}; do
  if curl -fsS "http://127.0.0.1:$api_port/health" >/dev/null 2>&1; then break; fi
  if ! kill -0 "$server_pid" 2>/dev/null; then cat "$test_dir/server.log"; exit 1; fi
  sleep 0.5
done
MOMENTS_TEST_URL="http://127.0.0.1:$api_port" python3 tests/api.py || { cat "$test_dir/server.log"; exit 1; }
