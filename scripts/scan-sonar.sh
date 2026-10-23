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
# `clients/java/client`'s Cargo ITs run for real: the `it-runtime` profile (active by default)
# makes camunda-tomcat-assembly a reactor dependency of the client, so the Tomcat it boots is
# built in this same reactor, never taken from a stale ~/.m2. Do NOT add `!it-runtime` to `-P`:
# it drops that reactor edge and a ~/.m2 assembly from before a Tomcat bump fails the build with
# "apache-tomcat-X/conf is not a directory", skipping ~150 modules. (The earlier belief that the
# profile "hangs on a clean checkout" was really an IT/pom port mismatch, 50080 vs 50180, fixed in
# clients/java/client.)
#
# `integration-test-spring-boot-starter` adds the spring-boot-starter / Camunda Run
# modules whose tests only run under that profile; without it their coverage is missing
# from the scan. Its id does not exist in the root pom, so the root's activeByDefault
# `distro*` profiles stay active — do not add those ids to `-P`, naming any of them turns
# the rest off and `distro` in engine-rest skips its whole test suite.
#
# `-Dcargo.rmi.port` is a second, independent workaround for the SAME
# clients/java/client module: the module's
# `cargo-maven3-plugin` `start-container`/`stop-container` executions are
# bound directly in <build><plugins> (not gated by any profile), so they
# always run. Cargo's RMI port (used for its own start/stop signalling, not
# app traffic) defaults to 8205 for every container type. Every OTHER port
# this module's cargo config uses (servlet/AJP/shutdown) is deliberately
# remapped into the 50000s in the pom's <properties> to avoid exactly this
# class of collision — RMI was simply missed. Fixed at the source in this
# branch: clients/java/client/pom.xml now declares `<cargo.rmi.port>50205
# </cargo.rmi.port>` (same 50000s scheme as its siblings) AND wires it into
# the cargo `<configuration><properties>` block, which is what actually
# matters — passing `-Dcargo.rmi.port` alone on the CLI does NOT reach the
# container's start-up configuration for this module (only the plugin's own
# stop-goal happens to read the raw system property), so a CLI-only
# workaround silently fails to prevent the collision at start time while
# looking like it worked. Confirmed the hard way: this dev box has a
# long-running `mailpit` Docker container permanently bound to host 8205,
# and the build hung/failed at this exact step even with the CLI flag set,
# until the pom itself was fixed. The env var below is kept only so a
# collision on 50205 itself can still be overridden without editing the pom.
set -euo pipefail
cd "$(dirname "$0")/.."

: "${SONAR_TOKEN:?SONAR_TOKEN must be set (a Sonar user token with Execute Analysis permission)}"
SONAR_HOST_URL="${SONAR_HOST_URL:-https://sonar.javan.co.id}"
CARGO_RMI_PORT="${CARGO_RMI_PORT:-50205}"

./mvnw -B clean install \
  "-Dmaven.test.failure.ignore=true" \
  -P integration-test-spring-boot-starter \
  "-Dcargo.rmi.port=${CARGO_RMI_PORT}" \
  org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  "-Dsonar.host.url=${SONAR_HOST_URL}" \
  "-Dsonar.token=${SONAR_TOKEN}"

echo
echo "Scan submitted. sonar.projectKey/sonar.projectName are pinned in sonar-project.properties —"
echo "verify the dashboard at ${SONAR_HOST_URL}/dashboard?id=alurkerja-camunda-bpm-platform"
echo "shows THIS run's date before quoting any numbers from it."
