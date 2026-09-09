#!/usr/bin/env bash
# Run the reactor build + Sonar scan against the pinned project key.
#
# Usage:
#   SONAR_TOKEN=squ_xxx ./scripts/scan-sonar.sh
#
# Requires: JDK 21 on PATH (or JAVA_HOME set), network access, SONAR_TOKEN with
# permission to analyze the project. sonar.host.url defaults to the Javan
# instance but can be overridden with SONAR_HOST_URL.
#
# Note: this runs the full reactor test suite (README's documented command,
# `-Dmaven.test.failure.ignore=true` so a red test does not abort the scan).
# On this reactor (~8.7k Java files) that takes a long time — do not run this
# expecting a quick answer, and check the tail of the log for the surefire
# summary (tests run / failures / errors) before trusting the Sonar numbers.
#
# `-P '!it-runtime'` works around a real build-ordering bug, not a scan
# decision: clients/java/client's `it-runtime` profile (active by default)
# needs the tar.gz built by distro/tomcat, which sits ~120 modules LATER in
# the reactor — so on a clean clone (empty local .m2) this profile always
# hangs Cargo trying to start a Tomcat container against an artifact that
# does not exist yet, with no clear build-failure message (had to kill it
# manually after a multi-minute stall to find this). Filed as a separate
# fix (module reordering, or make it-runtime genuinely opt-in); until then,
# this flag is required for the script to finish on a clean checkout.
#
# `-Dcargo.rmi.port` is a second, independent workaround for the SAME
# clients/java/client module: even with `it-runtime` disabled, the module's
# `cargo-maven3-plugin` `start-container`/`stop-container` executions are
# bound directly in <build><plugins> (not gated by any profile), so they
# always run. Cargo's RMI port (used for its own start/stop signalling, not
# app traffic) defaults to 8205 for every container type and is NOT declared
# anywhere in this repo's poms — it is the plugin's own hardcoded default,
# overridable only via this Maven-recognized system property. On machines
# where 8205 is already bound by something else entirely unrelated to this
# build (e.g. this dev box has a long-running `mailpit` Docker container
# mapping host 8205), the build fails immediately with "Port number 8205
# ... is in use" instead of the tar.gz-hang above. Override the value if
# 18205 is also taken on your machine.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${SONAR_TOKEN:?SONAR_TOKEN must be set (a Sonar user token with Execute Analysis permission)}"
SONAR_HOST_URL="${SONAR_HOST_URL:-https://sonar.javan.co.id}"
CARGO_RMI_PORT="${CARGO_RMI_PORT:-18205}"

./mvnw -B clean install \
  "-Dmaven.test.failure.ignore=true" \
  -P '!it-runtime' \
  "-Dcargo.rmi.port=${CARGO_RMI_PORT}" \
  org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  "-Dsonar.host.url=${SONAR_HOST_URL}" \
  "-Dsonar.token=${SONAR_TOKEN}"

echo
echo "Scan submitted. sonar.projectKey/sonar.projectName are pinned in the root pom.xml —"
echo "verify the dashboard at ${SONAR_HOST_URL}/dashboard?id=org.camunda.bpm:camunda-root"
echo "shows THIS run's date before quoting any numbers from it."
