# Camunda Platform 7 - The open source BPMN platform

[![Maven Central Version](https://img.shields.io/maven-central/v/org.camunda.bpm/camunda-parent)](https://central.sonatype.com/artifact/org.camunda.bpm/camunda-parent) [![camunda manual latest](https://img.shields.io/badge/manual-7.24-brown.svg)](https://docs.camunda.org/manual/7.24/) [![License](https://img.shields.io/github/license/camunda/camunda-bpm-platform?color=blue&logo=apache)](https://github.com/camunda/camunda-bpm-platform/blob/master/LICENSE) [![Forum](https://img.shields.io/badge/forum-camunda-green)](https://forum.camunda.org/)

> [!Caution]
> **Camunda 7 Community Edition (CE)** is **End of Life (EoL)**, and the Enterprise Edition entered long-term support, receiving only maintenance improvements as well as bug and security fixes. Looking ahead, [Camunda 8](https://github.com/camunda/camunda) is the successor to this product, so we strongly encourage you to explore it and contribute there instead. You can also read more about the timeline of [Camunda 7 Enterprise End of Life](https://camunda.com/blog/2025/02/camunda-7-enterprise-end-of-life-extension/) (EoL). 

Camunda Platform 7 is a flexible framework for workflow and process automation. Its core is a native BPMN 2.0 process engine that runs inside the Java Virtual Machine. It can be embedded inside any Java application and any Runtime Container. It integrates with Java EE 6 and is a perfect match for the Spring Framework. On top of the process engine, you can choose from a stack of tools for human workflow management, operations and monitoring.

- Web Site: https://www.camunda.org/
- Getting Started: https://docs.camunda.org/get-started/
- User Forum: https://forum.camunda.org/
- Issue Tracker: https://github.com/camunda/camunda-bpm-platform/issues
- Contribution Guidelines: https://camunda.org/contribute/

## Alurkerja fork — publishing to Javan Nexus

### Sonar & Trivy scan scope

Both scans have concrete, versioned scripts in `scripts/` — run those instead of copy-pasting
commands from this README, so results are reproducible from a clean clone.

**Sonar project key.** The root `pom.xml` pins `sonar.projectKey=org.camunda.bpm:camunda-root` and
`sonar.projectName=Camunda Platform - Root Pom` explicitly. Why pinning matters and why this key:
the fork's groupId was renamed from `org.camunda.bpm` to `alurkerja.camunda.bpm` (commit
`c3307546`), and the Sonar Maven plugin derives the project key from `groupId:artifactId` when
none is pinned. Left unpinned, the first scan after the rename silently created a **second**,
history-less Sonar project (`alurkerja.camunda.bpm:camunda-root`) — confirmed on
`sonar.javan.co.id` (scanned once, 2026-08-24) — leaving the real baseline
(`org.camunda.bpm:camunda-root`, 302 843 ncloc / 11 383 code smells / 291 bugs as of 2026-08-19)
orphaned. A third stray project, `alurkerja-camunda-bpm-platform`, also exists from an earlier
unpinned/partial run (scanned 5×, last 2026-08-30) and reports 0 bugs / 0 vulnerabilities / 3 156
code smells on only 270 530 ncloc — i.e. it never covered the full reactor, so its "clean" numbers
prove nothing. **Keep pointing at `org.camunda.bpm:camunda-root`** so history stays continuous;
do not treat the other two keys as sources of truth, and consider deleting them on the Sonar
server once this is confirmed (out of scope for this change — flag to Purwa).

Sonar 26.8.0 exposes two parallel severity taxonomies for the same issues — legacy
(`INFO`/`MINOR`/`MAJOR`/`CRITICAL`/`BLOCKER`) and Clean Code "software quality" impact severity
(`LOW`/`MEDIUM`/`HIGH`/`BLOCKER`) — and they do not map 1:1. **The legacy taxonomy is the one this
fork's tasks (`code smell info/minor = 0`) are written against**, so treat legacy severity as
authoritative; report the Clean Code breakdown alongside it for anyone reading the dashboard
directly.

```bash
scripts/scan-sonar.sh          # needs SONAR_TOKEN in the environment
```

**Trivy scope.** `trivy fs .` on the raw reactor scans every module, including ones that are never
built into anything a consumer depends on: `qa/` (upgrade-path test fixtures), `examples/`
(build-time fixture only — needed because `spring-boot-starter`'s tests depend on the invoice
example), `javaee/*` and the JBoss/WildFly/Tomcat/`distro/run` packagings (no consumer here runs
Camunda in an app server or as a standalone distro — `alurkerja-saas-camunda` embeds the engine as
a Spring Boot 4 library), `connect`, `freemarker-template-engine`, `clients/java`, `quarkus-extension`,
and the test-only `test-utils/archunit` / `test-utils/testcontainers`. None of these ship in what
actually reaches production.

The "reaches production" set was **not guessed** — it was read off
`mvn -o dependency:list` on `alurkerja-saas-camunda`'s `master` branch (the trunk that's actually
deployed; `develop` is stale and still on plain upstream Camunda 7.21.0), which imports
`alurkerja.camunda.bpm:camunda-bom:7.24.0`. The resolved compile+test classpath maps to these
reactor modules: `engine`, `engine-spring`, `engine-rest`, `engine-dmn`, `engine-plugins/spin-plugin`
(not `engine-plugins/connect-plugin` or `engine-plugins/identity-ldap`), `juel`, `model-api`,
`spring-boot-starter`, `webapps`, `distro/webjar`, `commons`, `spin`,
`test-utils/junit5-extension`, `test-utils/junit5-extension-dmn`, plus the aggregator/version-pin
poms `parent`, `bom`, `database`, `internal-dependencies` (no source of their own, but they pin the
versions everything above resolves to, so a pinned-CVE manifest lives there).

Two scans are kept, both run by the same script and reported separately — do not average them:

| Scan | What | Why keep it |
|------|------|-------------|
| `full` | `trivy fs .` on the whole reactor | comparison baseline against the historical 1997-finding number; catches CVEs in modules we may adopt later |
| `distributed` | `trivy fs` restricted to the modules above via `--skip-dirs` | the number that actually describes production risk today |

`--severity CRITICAL,HIGH` is what fails the script (non-zero exit); `MEDIUM`/`LOW` are still
reported in the output files but are informational only — this mirrors how `alurkerja-saas-camunda`
already triages CVEs one-by-one in `.trivyignore` rather than gating on every severity. Anything
excluded for a real reason (not just "different scope") goes in `.trivyignore` at repo root, one
CVE per entry, each with a comment explaining why — see `alurkerja-saas-camunda/.trivyignore` for
the style to follow.

```bash
scripts/scan-trivy.sh          # writes trivy-result-full.json/html and trivy-result-distributed.json/html
```

This repository is the Alurkerja-maintained fork of Camunda 7 (upstream CE is EoL). Artifacts are
**not** published to Maven Central anymore; they go to the Javan Nexus:

| Repo | URL | Used for |
|------|-----|----------|
| `maven-snapshots` | https://maven.cloud.javan.co.id/repository/maven-snapshots | `*-SNAPSHOT` versions |
| `maven-releases`  | https://maven.cloud.javan.co.id/repository/maven-releases  | release versions (immutable: a version can be uploaded only once) |

Source of truth: GitLab `alurkerja/on-premises/camunda-bpm-platform` (GitHub `alurkerja-id/camunda-bpm-platform` is a mirror).
The root `pom.xml` `<distributionManagement>` already points at both repositories — nothing to configure in the poms.

What differs from upstream Camunda (all changes live in the root `pom.xml` unless noted):

| Upstream | This fork |
|----------|-----------|
| groupIds `org.camunda.bpm`, `org.camunda.bpm.springboot`, `org.camunda.bpm.webapp`, `org.camunda.spin`, `org.camunda.connect`, `org.camunda.commons`, … | same, prefixed `alurkerja.` instead of `org.`: `alurkerja.camunda.bpm`, `alurkerja.camunda.bpm.springboot`, … (Java packages are **unchanged**, still `org.camunda.bpm.*`). Only `org.camunda:camunda-bpm-release-parent` and `org.camunda.feel` stay — they are external. QA upgrade fixtures under `qa/` keep `org.camunda.*` for the *old upstream versions* they test against. |
| version hard-coded in 181 poms + 2 `package.json` | `<revision>` property in the root pom, every pom says `<version>${revision}</version>`; `flatten-maven-plugin` writes the concrete version into the deployed poms; the webapps/docs frontend builds get it as env `CAMUNDA_VERSION` (`webapps/pom.xml`, `engine-rest/docs/pom.xml` → `webpack.common.js`, `docs/build.js`), the `package.json` versions are a fixed placeholder |
| publishes to Maven Central (GPG + `central-publishing-maven-plugin`) | both unbound in the root pom; only the Nexus deploy (via `nexus-staging-maven-plugin`, `skipStaging=true`) remains |
| `central-sonatype-publish` profile module list | + `test-utils/archunit`, `test-utils/testcontainers`, `examples` (needed in the reactor because they can no longer be pulled from Camunda's Nexus) |
| `clients/java/client` test-depends on the Tomcat distro (`camunda-tomcat-assembly:tar.gz`) | that dependency sits in profile `it-runtime` (active unless `-DskipTests`); `camunda-engine` and `camunda-spin-core` are explicit test deps instead |
| Spring Boot **3.5** (`version.spring-boot` in `parent/pom.xml`) | Spring Boot **4.1** (Spring Framework 7, Jersey 4; `7.24.1` was built with 4.0, `7.24.2`+ with 4.1 — the code is identical, only `version.spring-boot` differs). `spring-boot-starter/**` and `distro/run/core` were adapted to the Spring Boot 4 module split: moved classes re-imported (`JerseyApplicationPath`, `DataSource*AutoConfiguration`, health `HealthIndicator`, `SecurityFilterProperties`, `TestRestTemplate` → `spring-boot-resttestclient`, `@MockBean` → `@MockitoBean`), `spring-boot-jdbc` is a direct dependency of the starter, `ClientsConfiguredCondition` (package-private in Boot 4) is copied into `starter-security`, `rest-assured` pinned in `distro/run/qa`. Consumers must be on Spring Boot 4.x — Spring Boot 3 apps stay on `7.24.0`. |

### Prerequisites

- JDK 21, Maven wrapper (`./mvnw`), network access (the webapps build downloads Node 20 via `frontend-maven-plugin`).
- Nexus credentials in `~/.m2/settings.xml`. The `<id>` must match the repository ids above:

  ```xml
  <settings>
    <servers>
      <server>
        <id>maven-snapshots</id>
        <username>NEXUS_USER</username>
        <password>NEXUS_PASSWORD</password>
      </server>
      <server>
        <id>maven-releases</id>
        <username>NEXUS_USER</username>
        <password>NEXUS_PASSWORD</password>
      </server>
    </servers>
  </settings>
  ```

### Snapshot vs release — the one rule

There is **one deploy command**. Maven decides *where* the artifacts go purely from the version string
in `<revision>` (root `pom.xml`):

| `<revision>` value | Maven treats it as | Uploaded to | Can be uploaded again? |
|---|---|---|---|
| ends with `-SNAPSHOT`, e.g. `7.24.1-SNAPSHOT` | snapshot | `maven-snapshots` (`<snapshotRepository>`) | yes — every deploy adds a new timestamped build (`7.24.1-20260822.161500-3.jar`); consumers get the newest with `mvn -U` |
| anything else, e.g. `7.24.0` | release | `maven-releases` (`<repository>`) | **no** — Nexus rejects a second upload of the same version |

So: to publish a snapshot the revision **must** end in `-SNAPSHOT` (exact spelling, upper case);
to publish a release it **must not**. `master` normally carries a `-SNAPSHOT` revision; it is switched to a
release number only for the release commit and bumped back right after.

```bash
# the deploy command, identical for snapshot and release
./mvnw -B clean deploy -Pcentral-sonatype-publish -DskipTests -Dmaven.javadoc.skip=true
```

Takes ~20–25 min (webapps `npm ci` + webpack included).

### Deploy a SNAPSHOT (day-to-day)

1. Check the revision ends in `-SNAPSHOT`:
   ```bash
   grep -m1 "<revision>" pom.xml      # → <revision>7.24.3-SNAPSHOT</revision>
   ```
   If it does not (e.g. right after a release), set it in the root `pom.xml` `<revision>` and commit.
2. Deploy:
   ```bash
   ./mvnw -B clean deploy -Pcentral-sonatype-publish -DskipTests -Dmaven.javadoc.skip=true
   ```
3. Check the upload (any module will do):
   `https://maven.cloud.javan.co.id/repository/maven-snapshots/alurkerja/camunda/bpm/camunda-engine/7.24.3-SNAPSHOT/maven-metadata.xml`
4. Consumers: depend on `7.24.3-SNAPSHOT` and build with `mvn -U …` to force-refresh the snapshot.

Repeat step 2 as often as you like — same version, newer timestamp each time.

### Deploy a RELEASE

1. Make sure `master` is green (`./mvnw -B install -Pcentral-sonatype-publish -DskipTests -Dmaven.javadoc.skip=true`).
2. Set the release version — **drop `-SNAPSHOT`**: root `pom.xml` `<revision>7.24.1</revision>`.
3. Commit and tag:
   ```bash
   git commit -am "chore(release): set version to 7.24.1"
   git tag 7.24.1
   ```
4. Deploy — same command:
   ```bash
   ./mvnw -B clean deploy -Pcentral-sonatype-publish -DskipTests -Dmaven.javadoc.skip=true
   ```
   Because the version has no `-SNAPSHOT`, Maven routes it to `maven-releases`
   (`nexus-staging-maven-plugin` with `skipStaging=true` = plain deploy to `<distributionManagement><repository>`; Nexus 3 has no staging workflow).
5. Check: `https://maven.cloud.javan.co.id/repository/maven-releases/alurkerja/camunda/bpm/camunda-engine/7.24.1/`
6. Bump back to a snapshot for further work — `<revision>7.24.2-SNAPSHOT</revision>`, commit, push branch **and** tag:
   ```bash
   git commit -am "chore(release): prepare next development version 7.24.2-SNAPSHOT"
   git push && git push --tags
   ```

`maven-releases` is immutable: if a release build fails **after** the upload started (rare — the upload is the
very last step) or something is wrong with a published release, fix it and release the **next** number
(`7.24.2`); never try to re-upload the same one.

Released so far: `7.24.0` (2026-08-22).

What the flags do:

| Flag | Why |
|------|-----|
| `-Pcentral-sonatype-publish` | selects exactly the modules Camunda publishes to Central (engine, spring-boot starters, engine-rest, webapps + webjar, spin/connect/dmn/juel, external-task client, bom, parent) plus the three fork additions listed above. The `distro*` profiles (Tomcat/WildFly/Run distributions) are switched off automatically. |
| `-DskipTests` | compiles test sources (some modules need test-jars) but runs nothing. |
| `-Dmaven.javadoc.skip=true` | no javadoc jars; drop it if you want them published. |

Notes:

- Upload is **deferred**: `nexus-staging-maven-plugin` collects every artifact locally and bulk-uploads in the last reactor module. A failure halfway = nothing uploaded (verified: three failed runs left Nexus untouched). After fixing, **rerun the full command** — do not resume with `-rf :<module>` for a deploy, the deferred upload would then only contain the modules of that partial run.
- `mvn validate` does not catch missing test-scoped dependencies; they surface at `test-compile` (~10 min into the build). To check the whole reactor before a release run `./mvnw -B install -Pcentral-sonatype-publish -DskipTests -Dmaven.javadoc.skip=true` first (same modules, no upload).
- Do **not** add `-Dskip.frontend.build=true` (or `-PskipFrontendBuild`) for a deploy — the webapp webjar would be published without Cockpit/Tasklist/Admin UI.
- Consumers pick up a new snapshot with `mvn -U ...`.

### Changing the version

One place: the `<revision>` property at the top of the root `pom.xml`.

```xml
<properties>
  <revision>7.24.1-SNAPSHOT</revision>   <!-- -SNAPSHOT → maven-snapshots; 7.24.1 → maven-releases -->
```

Every module declares `<version>${revision}</version>` (also for its `<parent>`), and `flatten-maven-plugin`
(`resolveCiFriendliesOnly`) replaces the placeholder in the poms that are installed/deployed, so consumers see
the concrete version (`7.24.1-SNAPSHOT`, `7.24.1`, …). Do not commit the generated `.flattened-pom.xml` files (git-ignored).

The two npm projects (`webapps/frontend`, `engine-rest/docs`) do **not** need an edit: Maven passes
`${project.version}` to their builds as the `CAMUNDA_VERSION` environment variable (frontend-maven-plugin
`<environmentVariables>` in `webapps/pom.xml` and `engine-rest/docs/pom.xml`), and `webpack.common.js` /
`docs/build.js` read that first. Their `package.json` `"version"` is a fixed placeholder (`0.0.0-set-by-maven`)
that is only used when someone runs `npm run build` by hand outside Maven.

Checklist for every version change: **one file, one line** — `pom.xml` → `<revision>…</revision>`.

Version rules: the version must start with digits (`7.24.0`, `7.24.1-SNAPSHOT`, `7.24.0-alurkerja`) — the
OSGi `maven-bundle-plugin` rejects anything else (e.g. `alurkerja-7.24.0` fails with
`Invalid syntax for version`). Because the groupIds are `alurkerja.camunda.*`, a plain `7.24.0` cannot clash
with the upstream `org.camunda.bpm:*:7.24.0` on Maven Central.

### Consuming the fork

```xml
<repositories>
  <repository>
    <id>maven-snapshots</id>
    <name>Alurkerja Snapshot</name>
    <url>https://maven.cloud.javan.co.id/repository/maven-snapshots</url>
  </repository>
  <repository>
    <id>maven-releases</id>
    <name>Alurkerja Releases</name>
    <url>https://maven.cloud.javan.co.id/repository/maven-releases</url>
  </repository>
</repositories>

<dependency>
  <groupId>alurkerja.camunda.bpm.springboot</groupId>   <!-- NOT org.camunda.bpm.springboot -->
  <artifactId>camunda-bpm-spring-boot-starter-webapp</artifactId>
  <version>7.24.2</version>                             <!-- Spring Boot 4 apps (or 7.24.3-SNAPSHOT); Spring Boot 3 apps use 7.24.0 -->
</dependency>
```

| Your app | Fork version |
|----------|--------------|
| Spring Boot 4.1 | `7.24.2` and later (`7.24.3-SNAPSHOT` for the current development build) |
| Spring Boot 4.0 | `7.24.1` (built with Boot 4.0; `7.24.2`+ should also work, same code) |
| Spring Boot 3.5 | `7.24.0` (last Spring Boot 3 build) |

Migrating an existing project from upstream Camunda: replace the groupId prefix `org.camunda.` with
`alurkerja.camunda.` in your poms (dependencies and BOM import `alurkerja.camunda.bpm:camunda-bom`);
imports in Java code stay `org.camunda.bpm.*`.

A complete Spring Boot 4 sample lives in GitLab `alurkerja/on-premises/sample/camunda-fork-sample`.

## Components

Camunda Platform 7 provides a rich set of components centered around the BPM lifecycle.

#### Process Implementation and Execution

- Camunda Engine - The core component responsible for executing BPMN 2.0 processes.
- REST API - The REST API provides remote access to running processes.
- Spring, CDI Integration - Programming model integration that allows developers to write Java Applications that interact with running processes.

#### Process Design

- Camunda Modeler - A [standalone desktop application](https://github.com/camunda/camunda-modeler) that allows business users and developers to design & configure processes.

#### Process Operations

- Camunda Engine - JMX and advanced Runtime Container Integration for process engine monitoring.
- Camunda Cockpit - Web application tool for process operations.
- Camunda Admin - Web application for managing users, groups, and their access permissions.

#### Human Task Management

- Camunda Tasklist - Web application for managing and completing user tasks in the context of processes.

#### And there's more...

- [bpmn.io](https://bpmn.io/) - Toolkits for BPMN, CMMN, and DMN in JavaScript (rendering, modeling)
- [Community Extensions](https://docs.camunda.org/manual/7.5/introduction/extensions/) - Extensions on top of Camunda Platform 7 provided and maintained by our great open source community

## A Framework

In contrast to other vendor BPM platforms, Camunda Platform 7 strives to be highly integrable and embeddable. We seek to deliver a great experience to developers that want to use BPM technology in their projects.

### Highly Integrable

Out of the box, Camunda Platform 7 provides infrastructure-level integration with Java EE Application Servers and Servlet Containers.

### Embeddable

Most of the components that make up the platform can even be completely embedded inside an application. For instance, you can add the process engine and the REST API as a library to your application and assemble your custom BPM platform configuration.

## Contributing

Please see our [contribution guidelines](CONTRIBUTING.md) for how to raise issues and how to contribute code to our project.

## Tests

To run the tests in this repository, please see our [testing tips and tricks](TESTING.md).


## License

The source files in this repository are made available under the [Apache License Version 2.0](./LICENSE).

Camunda Platform 7 uses and includes third-party dependencies published under various licenses. By downloading and using Camunda Platform 7 artifacts, you agree to their terms and conditions. Refer to https://docs.camunda.org/manual/latest/introduction/third-party-libraries/ for an overview of third-party libraries and particularly important third-party licenses we want to make you aware of.
