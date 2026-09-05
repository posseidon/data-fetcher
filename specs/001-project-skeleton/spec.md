# Feature Specification: Project Skeleton

**Feature Branch**: `001-project-skeleton`
**Created**: 2026-09-04
**Status**: Draft
**Input**: User description: "Quarkus Java 21 Maven project skeleton with docker-compose dependencies (PostgreSQL 18, Consul 2.0 KV, Bronze volume)"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Boot local infrastructure (Priority: P1)

A developer brings up the local dependencies with a single command
(`docker compose up -d`). PostgreSQL 18 and Consul 2.0 start in containers; a Docker
volume mounting the host directory `~/data/bronze` is wired so the service's
Bronze staging area is reachable on the host.

**Why this priority**: Every other feature (catalog, jobs, artifacts, config)
depends on DB + Consul 2.0 + Bronze being reachable in local dev. Without them
nothing else runs.

**Independent Test**: Can be fully tested by running the single compose command,
then verifying both containers report healthy and the Bronze mount is visible
at `~/data/bronze` on the host.

**Acceptance Scenarios**:

1. **Given** a fresh checkout, **When** a developer runs the documented compose
   command, **Then** PostgreSQL 18 and Consul 2.0 containers start without errors.
2. **Given** Compose running, **When** a file is written inside the container's
   Bronze path, **Then** it is visible under `~/data/bronze` on the host
   (bidirectional mount).

### User Story 2 - Build and run the app (Priority: P1)

A developer runs the documented dev boot (`./mvnw quarkus:dev`). The Quarkus
app on Java 21 starts in dev mode with virtual threads enabled, loads
configuration from `application.properties`, and connects to the local
PostgreSQL 18. No feature endpoints are required yet.

**Why this priority**: A bootable skeleton proves the locked stack works before
feature code lands; it is the foundation all four planned features build on.

**Independent Test**: Can be fully tested by running the dev command and
observing a successful startup log line (Quarkus started, JDBC source
configured, virtual threads on) with no errors.

**Acceptance Scenarios**:

1. **Given** a Java 21 toolchain and the Maven wrapper, **When** a developer
   runs `./mvnw quarkus:dev`, **Then** the app boots with no errors and reports
   its HTTP port.
2. **Given** the app booted, **When** the developer checks the log, **Then**
   virtual-thread concurrency is enabled and the PostgreSQL 18 data source is
   configured.

### User Story 3 - Verify build integrity (Priority: P2)

A developer (or CI-style check) runs `./mvnw package` / `./mvnw verify` on a
clean checkout and confirms the dependency set from the constitution compiles
and tests pass.

**Why this priority**: Locked stack = the build must succeed reproducibly on a
clean machine with zero pre-installed artifacts beyond Maven/Java.

**Independent Test**: Can be fully tested by running the build command and
observing a successful build; no application code exists, so the build gate is
the dependency graph itself.

**Acceptance Scenarios**:

1. **Given** a clean checkout, **When** `./mvnw package` runs, **Then** the
   build succeeds.
2. **Given** a modified pom that drops a mandated dependency, **When** the build
   runs, **Then** any feature relying on that dependency fails to compile.

### Edge Cases

- What happens when port 5432 or the Consul 2.0 port is already in use? (Compose
  must fail loudly with a clear port-conflict message, or expose configurable
  host ports.)
- What happens when `~/data/bronze` does not exist on first boot? (Compose must
  create it (or explicitly fail with guidance) — never silently bind a wrong
  path.)
- What happens when the Maven wrapper JAR is missing from the repo? (Clone must
  be self-contained; wrapper must be committed so `./mvnw` works offline.)
- What happens when the app starts with PostgreSQL 18 down? (Fetch/query features
  fail later; skeleton must not mask the dependency — no silent fallback.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST be a Maven (Java 21) Quarkus application with a
  committed Maven wrapper (`./mvnw`) usable without a pre-installed Maven.
- **FR-002**: `docker compose up -d` MUST start PostgreSQL 18 and Consul 2.0
  containers; the compose file MUST be the single source of truth for local
  dependencies.
- **FR-003**: A Docker volume/bind mount MUST expose the service's Bronze path
  such that `~/data/bronze` on the host and the container's Bronze root point
  to the same files (default container path `/data/bronze`).
- **FR-004**: The `pom.xml` MUST declare the dependency set mandated by the
  constitution: RESTEasy Reactive + Jackson for the HTTP tool surface, JPA
  Panache + PostgreSQL 18 JDBC for persistence, and SmallRye Config for
  configuration loading. WebSockets and MCP wire-protocol SDKs MUST NOT be
  added (deferred to iteration 2 / out of REST-first plan).
- **FR-005**: Consul 2.0 MUST appear as an infrastructure container only; no Consul 2.0
  client/config-source dependency belongs in this feature (wiring is feature
  004).
- **FR-006**: Virtual threads MUST be enabled by default in the runtime
  configuration.
- **FR-007**: `application.properties` MUST carry sane local-dev defaults
  (PostgreSQL 18 datasource at the composed host/port, Bronze root default
  `/data/bronze`), overridable without code change.
- **FR-008**: The app MUST boot in dev mode (`./mvnw quarkus:dev`) with no
  application feature code written, producing a successful startup log.
- **FR-009**: The project MUST be single-replica / local-dev-first (no
  deployment or OpenShift concerns in Month 1).
- **FR-010**: Secrets MUST NOT be committed; any default credentials are local
  dev-only placeholders in the compose file.

### Key Entities *(include if feature involves data)*

- **Infrastructure footprint**: the two containers (PostgreSQL 18, Consul 2.0) and the
  Bronze volume mount form the local runtime environment all features share.
- **runtime config artifact**: `application.properties` (defaults) + compose
  env vars; not authoritative data, but the contract skeleton features 001-004
  read.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `docker compose up -d` reaches a healthy state for both
  containers with zero manual steps beyond the documented command.
- **SC-002**: A bind-mount check passes: a file written to the container's
  Bronze root appears at `~/data/bronze` on the host within seconds.
- **SC-003**: `./mvnw quarkus:dev` boots the app on a clean checkout in under
  ~2 minutes on a warmed toolchain with zero build errors.
- **SC-004**: `./mvnw package` succeeds on a clean checkout, proving the
  constitution-mandated dependency set resolves and compiles with no
  unapproved libraries.

## Assumptions

- The stack is locked per the constitution: Quarkus on JVM (Java 21), virtual
  threads, REST-first HTTP surface, PostgreSQL 18, Consul 2.0 KV, Docker Compose local
  dev — no amendments in this feature.
- The host path is `~/data/bronze`; the container path is `/data/bronze`
  (matches the constitution's default Bronze root).
- Consul 2.0 client wiring, runtime-key validation, and fail-fast boot are owned by
  feature 004, not this skeleton.
- No application feature code (sources/datasets/jobs/artifacts) ships in this
  feature; it only proves the platform boots.
- Default credentials in compose are throwaway local-dev values, not secrets.