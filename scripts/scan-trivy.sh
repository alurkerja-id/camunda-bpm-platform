#!/usr/bin/env bash
# Run two Trivy filesystem scans against the reactor:
#   1. "full"        — the whole reactor (README's original scope), kept as a
#                       comparison baseline against the historical 1997-finding number.
#   2. "distributed"  — only the modules that alurkerja-saas-camunda (master branch)
#                       actually resolves through alurkerja.camunda.bpm:camunda-bom:7.24.0.
#                       This is the number that describes production risk today.
#
# See README.md "Sonar & Trivy scan scope" for how the module list below was derived
# (mvn dependency:list on alurkerja-saas-camunda's master, not a guess).
#
# Usage: ./scripts/scan-trivy.sh
# Uses `trivy` from PATH if present, otherwise falls back to the aquasec/trivy
# Docker image (no local install required).
set -euo pipefail
cd "$(dirname "$0")/.."

SEVERITY_FAIL="CRITICAL,HIGH"   # informational-only below this: MEDIUM, LOW
TIMEOUT="30m"

if command -v trivy >/dev/null 2>&1; then
  trivy() { command trivy "$@"; }
else
  echo "trivy not found on PATH, using aquasec/trivy via Docker" >&2
  trivy() {
    # Mount the host ~/.m2 read-only: for a reactor this size, Trivy's Java/pom
    # analyzer walks parent-pom chains and — without a local repo to resolve
    # from — does it over the network, straight into Maven Central's rate
    # limit (`429 Too Many Requests`, then a 30 min IP-wide block; hit this
    # once with an unmounted container). --offline-scan is the actual fix
    # (forces Trivy to use whatever's on disk, never fall back to network);
    # the ~/.m2 mount just makes the offline resolution more complete.
    docker run --rm \
      -v "$(pwd)":/repo -w /repo \
      -v trivy-cache:/root/.cache/ \
      -v "${HOME}/.m2":/root/.m2:ro \
      aquasec/trivy:latest "$@"
  }
fi

# Modules NOT reachable from a consumer's classpath today (see README) —
# excluded from the "distributed" scan only, not from "full".
SKIP_DIRS=(
  "qa"
  "examples"
  "javaee/ejb-service"
  "javaee/ejb-client"
  "javaee/ejb-client-jakarta"
  "javaee/jobexecutor-ra"
  "javaee/jobexecutor-rar"
  "distro/license-book"
  "distro/jboss"
  "distro/tomcat"
  "distro/sql-script"
  "distro/run"
  "connect"
  "freemarker-template-engine"
  "clients/java"
  "quarkus-extension"
  "engine-plugins/connect-plugin"
  "engine-plugins/identity-ldap"
  "test-utils/archunit"
  "test-utils/testcontainers"
  "test-utils/assert"
  "engine-cdi"
)
SKIP_ARGS=()
for d in "${SKIP_DIRS[@]}"; do
  SKIP_ARGS+=(--skip-dirs "$d")
done

echo "=== full reactor scan ==="
trivy fs . \
  --scanners vuln \
  --offline-scan \
  --timeout "${TIMEOUT}" \
  --severity "${SEVERITY_FAIL},MEDIUM,LOW" \
  --exit-code 0 \
  --format json -o trivy-result-full.json
trivy fs . --scanners vuln --offline-scan --timeout "${TIMEOUT}" \
  --format template --template "@contrib/html.tpl" -o trivy-result-full.html \
  2>/dev/null || echo "(html.tpl not found locally — json report is authoritative, see trivy-result-full.json)"

echo "=== distributed (production-consumed) scan ==="
trivy fs . \
  --scanners vuln \
  --offline-scan \
  --timeout "${TIMEOUT}" \
  --severity "${SEVERITY_FAIL},MEDIUM,LOW" \
  --exit-code 0 \
  "${SKIP_ARGS[@]}" \
  --format json -o trivy-result-distributed.json
trivy fs . --scanners vuln --offline-scan --timeout "${TIMEOUT}" "${SKIP_ARGS[@]}" \
  --format template --template "@contrib/html.tpl" -o trivy-result-distributed.html \
  2>/dev/null || echo "(html.tpl not found locally — json report is authoritative, see trivy-result-distributed.json)"

echo
echo "Reports written: trivy-result-full.json, trivy-result-distributed.json"
echo "(+ .html if contrib/html.tpl was resolvable). Summarize with e.g.:"
echo '  jq [.Results[].Vulnerabilities[]? | .Severity] trivy-result-distributed.json | sort | uniq -c'
echo
echo "Gate: fail the pipeline (exit non-zero) only on ${SEVERITY_FAIL} findings that have no"
echo "reasoned .trivyignore entry — this script itself always exits 0 so both reports get written"
echo "even when there are findings; wire the actual gate into CI once one exists (there is none yet)."
