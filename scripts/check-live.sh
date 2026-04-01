#!/usr/bin/env bash
set -euo pipefail

NS="${1:-qwikbrew}"

if ! command -v kubectl >/dev/null 2>&1; then
  echo "[ERROR] kubectl is not installed or not available in PATH."
  echo "        Install kubectl and configure cluster access, then rerun this script."
  exit 1
fi

echo "==========================================="
echo "QWIKBREW LIVE CHECK (${NS})"
echo "==========================================="

api_lb="$(kubectl get svc api-gateway -n "$NS" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
front_lb="$(kubectl get svc frontend -n "$NS" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"

if [[ -z "$api_lb" || -z "$front_lb" ]]; then
  echo "[WARN] Could not resolve one or more LB hostnames."
  echo "       api-gateway: ${api_lb:-<empty>}"
  echo "       frontend:    ${front_lb:-<empty>}"
fi

api_base="http://${api_lb}"
front_base="http://${front_lb}"

echo "API LB:      ${api_base}"
echo "Frontend LB: ${front_base}"

echo "\n---- Pod Status ----"
kubectl get pods -n "$NS" -o wide

echo "\n---- Services ----"
kubectl get svc -n "$NS"

echo "\n---- Endpoints ----"
kubectl get endpoints -n "$NS"

request() {
  local method="$1"; shift
  local url="$1"; shift
  echo "\n>>> ${method} ${url}"
  if [[ "$method" == "GET" ]]; then
    curl -sS -m 20 -o /tmp/qb_resp.txt -w 'HTTP %{http_code}\n' "$url" || true
  else
    curl -sS -m 20 -o /tmp/qb_resp.txt -w 'HTTP %{http_code}\n' -X "$method" -H 'Content-Type: application/json' "$url" "$@" || true
  fi
  echo "--- response body (first 400 chars) ---"
  head -c 400 /tmp/qb_resp.txt || true
  echo
}

if [[ -n "$api_lb" ]]; then
  request GET "${api_base}/actuator/health"
  request GET "${api_base}/api/v1/menu"
  request POST "${api_base}/api/v1/users/register" -d '{"name":"Live Check","email":"livecheck@example.com","password":"Pass@12345","phone":"9999999999"}'
  request POST "${api_base}/api/v1/users/login" -d '{"email":"livecheck@example.com","password":"Pass@12345"}'
fi

if [[ -n "$front_lb" ]]; then
  request GET "${front_base}/"
fi

echo "\n==========================================="
echo "Live check complete"
echo "==========================================="
