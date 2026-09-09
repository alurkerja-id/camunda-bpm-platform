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
set -euo pipefail
cd "$(dirname "$0")/.."

: "${SONAR_TOKEN:?SONAR_TOKEN must be set (a Sonar user token with Execute Analysis permission)}"
SONAR_HOST_URL="${SONAR_HOST_URL:-https://sonar.javan.co.id}"

./mvnw -B clean install \
  "-Dmaven.test.failure.ignore=true" \
  org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  "-Dsonar.host.url=${SONAR_HOST_URL}" \
  "-Dsonar.token=${SONAR_TOKEN}"

echo
echo "Scan submitted. sonar.projectKey/sonar.projectName are pinned in the root pom.xml —"
echo "verify the dashboard at ${SONAR_HOST_URL}/dashboard?id=org.camunda.bpm:camunda-root"
echo "shows THIS run's date before quoting any numbers from it."
