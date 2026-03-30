#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-qwikbrew}"
SERVICE_NAME="${SERVICE_NAME:-api-gateway}"
INGRESS_NAME="${INGRESS_NAME:-qwikbrew-ingress}"
CHECK_RESOURCE="${CHECK_RESOURCE:-auto}" # auto|service|ingress
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-600}"
SLEEP_SECONDS="${SLEEP_SECONDS:-10}"

start_ts="$(date +%s)"
end_ts=$((start_ts + TIMEOUT_SECONDS))

echo "Waiting for LoadBalancer endpoint (timeout=${TIMEOUT_SECONDS}s, mode=${CHECK_RESOURCE})"
echo "  service=${NAMESPACE}/${SERVICE_NAME}"
echo "  ingress=${NAMESPACE}/${INGRESS_NAME}"

get_service_external() {
  kubectl -n "$NAMESPACE" get svc "$SERVICE_NAME" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true
}

get_ingress_external() {
  kubectl -n "$NAMESPACE" get ingress "$INGRESS_NAME" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true
}

get_external() {
  case "$CHECK_RESOURCE" in
    service)
      get_service_external
      ;;
    ingress)
      get_ingress_external
      ;;
    auto)
      # Prefer ingress endpoint when ALB is used, fallback to Service LB.
      lb="$(get_ingress_external)"
      if [[ -n "$lb" ]]; then
        echo "$lb"
        return 0
      fi
      get_service_external
      ;;
    *)
      echo "Invalid CHECK_RESOURCE=${CHECK_RESOURCE}. Use auto|service|ingress." >&2
      exit 2
      ;;
  esac
}

while true; do
  external="$(get_external)"
  if [[ -n "$external" ]]; then
    echo "✅ LoadBalancer assigned: ${external}"
    echo "LB_ENDPOINT=${external}"
    exit 0
  fi

  now="$(date +%s)"
  if (( now >= end_ts )); then
    echo "❌ Timed out waiting for LoadBalancer assignment."
    echo
    echo "Service snapshot:"
    kubectl -n "$NAMESPACE" get svc "$SERVICE_NAME" -o wide || true
    echo
    echo "Ingress snapshot:"
    kubectl -n "$NAMESPACE" get ingress "$INGRESS_NAME" -o wide || true
    echo
    echo "Service describe (events at bottom):"
    kubectl -n "$NAMESPACE" describe svc "$SERVICE_NAME" || true
    echo
    echo "Ingress describe (events at bottom):"
    kubectl -n "$NAMESPACE" describe ingress "$INGRESS_NAME" || true
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
