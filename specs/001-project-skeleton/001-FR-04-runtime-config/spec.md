# Feature Specification: Runtime Configuration & Dev Boot

**Feature Branch**: `001-project-skeleton` (sub-feature 001-FR-04)
**Created**: 2026-09-05
**Status**: Draft
**Input**: Decomposition of feature 001 (Project Skeleton), parent user story "Build and run the app" (P1) and parent FR-006, FR-007.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Runtime defaults and virtual threads (Priority: P1)

A developer boots the Quarkus app in dev mode and sees sane local-dev defaults
take effect: virtual-thread concurrency enabled, a PostgreSQL 18 datasource
pointed at the composed host/port, and a configurable Bronze root default of
`/data/bronze`. Every knob is overridable without a code change.

**Why this priority**: User Story 2 of the parent spec requires the boot log to
prove virtual threads and the datasource are configured. Defaults that match
the compose setup remove the first class of local-dev friction (port/path
mismatches) before feature code lands.

**Independent Test**: Can be fully tested by booting the app from sub-feature
001-FR-03 with these `application.properties` defaults and checking the
startup log, then overriding one value via an environment variable and
confirming the override wins.

**Acceptance Scenarios**:

1. **Given** the app booted from sub-feature 001-FR-03, **When** the developer
   checks the log, **Then** virtual-thread concurrency is enabled.
2. **Given** the app booted with the datasource defaulting to the composed
   host/port, **When** the developer checks the log/startup output, **Then**
   the PostgreSQL 18 data source is configured against PostgreSQL 18 in the compose
   network.
3. **Given** `application.properties` sets `/data/bronze` as the Bronze root
   default, **When** a configuration value is overridden via environment
   variable, **Then** the override applies with no code change.
4. **Given** PostgreSQL 18 is down, **When** the app boots, **Then** the dependency
   failure surfaces at boot (no silent fallback, no fake success).

---

> Configuration validation, Consul 2.0 KV binding, and fail-fast boot are owned by
> feature 005 and are NOT in scope here. This sub-feature only ships the
> local-dev defaults contract and proves runtime flags take effect.

### Edge Cases

- What happens when the composed PostgreSQL 18 port differs from the default?
  (Must be overridable via env var without editing `application.properties`.)
- What happens when the Bronze root path is wrong or unwritable? (Boot should
  not silently succeed; feature 005 will own fail-fast validation, but the
  default must match the 001-FR-02 mount contract.)
- What happens when an invalid config value is supplied? (On-boarding to
  feature 005's validation; skeleton accepts defaults as documented here.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-006**: Virtual threads MUST be enabled by default in the runtime
  configuration.
- **FR-007**: `application.properties` MUST carry sane local-dev defaults
  (PostgreSQL 18 datasource at the composed host/port, Bronze root default
  `/data/bronze`), overridable without code change.

### Key Entities *(include if feature involves data)*

- **runtime config artifact**: `application.properties` (defaults) + compose
  env vars. Not authoritative data, but the contract skeleton features 001-004
  read; its values must match sub-feature 001-FR-01's compose ports and
  sub-feature 001-FR-02's mount path.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-005**: The startup log of a clean `./mvnw quarkus:dev` run explicitly
  shows virtual-thread concurrency enabled and a configured PostgreSQL 18 data
  source.
- **SC-006**: Overriding the datasource port or Bronze root via an environment
  variable changes effective configuration with zero code or file edits.

## Assumptions

- Datasource defaults point at the compose service as reachable from the app
  (e.g. `localhost:5432` or the compose network name) and MUST match sub-feature
  001-FR-01's `docker-compose.yml`.
- Bronze root default is `/data/bronze`, matching sub-feature 001-FR-02's
  container-side mount path and the constitution.
- Consul 2.0 KV sourcing and fail-fast validation of required keys are explicitly
  deferred to feature 005; no Consul 2.0 client dependency is added here.
- Depends on sub-feature 001-FR-03 (bootable skeleton) and 001-FR-01/02
  (compose + mount) for its acceptance scenarios.