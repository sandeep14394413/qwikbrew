#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-qwikbrew}"
SERVICE_NAME="${SERVICE_NAME:-api-gateway}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-600}"
SLEEP_SECONDS="${SLEEP_SECONDS:-10}"

start_ts="$(date +%s)"
end_ts=$((start_ts + TIMEOUT_SECONDS))

echo "Waiting for LoadBalancer hostname/IP for ${NAMESPACE}/${SERVICE_NAME} (timeout=${TIMEOUT_SECONDS}s)"

get_external() {
  kubectl -n "$NAMESPACE" get svc "$SERVICE_NAME" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true
}

while true; do
  external="$(get_external)"
  if [[ -n "$external" ]]; then
    echo "✅ LoadBalancer assigned: ${external}"
    exit 0
  fi

  now="$(date +%s)"
  if (( now >= end_ts )); then
    echo "❌ Timed out waiting for LoadBalancer assignment for ${NAMESPACE}/${SERVICE_NAME}."
    echo
    echo "Service snapshot:"
    kubectl -n "$NAMESPACE" get svc "$SERVICE_NAME" -o wide || true
    echo
    echo "Service describe (events at bottom):"
    kubectl -n "$NAMESPACE" describe svc "$SERVICE_NAME" || true
    echo
    echo "Recent cluster events:"
    kubectl get events -A --sort-by=.lastTimestamp | tail -n 100 || true
    echo
    echo "AWS LBC health (if installed):"
    kubectl -n kube-system get deploy aws-load-balancer-controller || true
    kubectl -n kube-system logs deploy/aws-load-balancer-controller --tail=100 || true
    exit 1
  fi

  echo "...still pending (${SERVICE_NAME})"
  sleep "$SLEEP_SECONDS"
done
