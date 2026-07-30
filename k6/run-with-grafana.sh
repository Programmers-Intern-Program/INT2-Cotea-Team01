#!/usr/bin/env bash
# Grafana로 메트릭을 보내며 k6 실행
# 예: ./k6/run-with-grafana.sh k6/smoke.js
#     VUS=10 DURATION=30s ./k6/run-with-grafana.sh k6/load-recommend.js

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="${1:-k6/smoke.js}"
shift || true

export K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-http://localhost:9090/api/v1/write}"
# native histogram 대신 stats로 보내면 대시보드 PromQL이 단순해진다
export K6_PROMETHEUS_RW_TREND_STATS="${K6_PROMETHEUS_RW_TREND_STATS:-p(95),p(99),avg,min,max}"

cd "$ROOT"

if ! curl -sf "http://localhost:9090/-/ready" >/dev/null 2>&1; then
  echo "[cotea-k6] Prometheus가 안 떠 있습니다. 먼저 실행하세요:"
  echo "  docker compose -f k6/docker-compose.yml up -d"
  exit 1
fi

echo "[cotea-k6] Grafana: http://localhost:3000  (admin/admin)"
echo "[cotea-k6] Dashboard: Cotea k6 Load Test"
echo "[cotea-k6] running: k6 run -o experimental-prometheus-rw ${SCRIPT} $*"

exec k6 run -o experimental-prometheus-rw "$SCRIPT" "$@"
