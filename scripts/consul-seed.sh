```bash
#!/usr/bin/env bash
set -euo pipefail

# Seeds required Consul KV keys for Data Fetcher v1.
#
# Usage:
#   CONSUL_HTTP_ADDR=http://localhost:8500 ./scripts/consul-seed.sh
#
# Optional overrides:
#   BRONZE_ROOT=/data/bronze
#   TIMEOUT_MS=600000
#   MAX_ATTEMPTS=1
#   MAX_PARALLEL_JOBS=4

CONSUL_HTTP_ADDR="${CONSUL_HTTP_ADDR:-http://localhost:8500}"
BRONZE_ROOT="${BRONZE_ROOT:-/data/bronze}"
TIMEOUT_MS="${TIMEOUT_MS:-600000}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-1}"
MAX_PARALLEL_JOBS="${MAX_PARALLEL_JOBS:-4}"

if ! command -v curl >/dev/null 2>&1; then
  echo "ERROR: curl is required"
  exit 1
fi

put_kv () {
  local key="$1"
  local value="$2"
  curl -fsS -X PUT \
    --data-binary "${value}" \
    "${CONSUL_HTTP_ADDR}/v1/kv/${key}" >/dev/null
  echo "OK  ${key} = ${value}"
}

echo "Seeding Consul KV at ${CONSUL_HTTP_ADDR}"
echo

# Required keys
put_kv "datafetcher/bronze/rootPath" "${BRONZE_ROOT}"
put_kv "datafetcher/download/timeoutMs" "${TIMEOUT_MS}"
put_kv "datafetcher/download/retry/maxAttempts" "${MAX_ATTEMPTS}"
put_kv "datafetcher/download/concurrency/maxParallelJobs" "${MAX_PARALLEL_JOBS}"

echo
echo "Done."
echo "Tip: verify via Consul UI at ${CONSUL_HTTP_ADDR}/ui/"

