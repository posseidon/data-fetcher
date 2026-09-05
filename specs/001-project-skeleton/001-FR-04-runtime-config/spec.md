# Feature Specification: Runtime Configuration & Dev Boot

**Feature Branch**: `001-project-skeleton` (sub-feature 001-FR-04)
**Created**: 2026-09-05
**Status**: Draft
**Input**: Decomposition of feature 001 (Project Skeleton), parent user story "Build and run the app" (P1) and parent FR-006, FR-007.

## Clarifications

### Session 2026-09-05

- Q: Datasource default target? → A: `localhost:5432`; the compose-network hostname (`df-postgres`) is reachable only via env override for future containerized runs.
- Q: Config artifact placement? → A: `application-dev.properties` (dev-profile only, per 001-FR-01 credential mirroring); other profiles supply their own configuration.
- Q: Postgres-down at boot (scenario 4 vs 005 deferral)? → A: keep scenario 4 in this sub-feature; ship a minimal startup DB-reachability probe (FR-007.1). Config-value validation/Consul fail-fast stays with feature 005.
- Q: How to prove virtual threads in startup log? → A: app-code `StartupEvent` observer (FR-007.2) logging VT status + resolved datasource; not relied on Quarkus default log.
- Q: Pinned env-var override names? → A: `QUARKUS_DATASOURCE_JDBC_URL` (native SmallRye mapping) + `BRONZE_ROOT` (custom key `bronze.root`); no custom config-source code.

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
001-FR-03 with these `application-dev.properties` defaults and checking the
startup log, then overriding one value via an environment variable and
confirming the override wins.

**Acceptance Scenarios**:

1. **Given** the app booted from sub-feature 001-FR-03, **When** the developer
   checks the log, **Then** virtual-thread concurrency is enabled.
2. **Given** the app booted with the datasource defaulting to the composed
   host/port, **When** the developer checks the log/startup output, **Then**
   the PostgreSQL 18 data source is configured against PostgreSQL 18 in the compose
   network.
3. **Given** `application-dev.properties` sets `/data/bronze` as the Bronze root
   default, **When** a configuration value is overridden via environment
   variable, **Then** the override applies with no code change.
4. **Given** PostgreSQL 18 is down, **When** the app boots, **Then** the dependency
   failure surfaces at boot (no silent fallback, no fake success).

---

> Configuration validation (invalid values), Consul 2.0 KV binding, and
> fail-fast validation of config values are owned by feature 005 and are NOT in
> scope here. This sub-feature ships the local-dev defaults contract, proves
> runtime flags take effect, and adds a minimal startup DB-reachability probe
> (scenario 4). Fail-fast for unreachable PostgreSQL 18 is implemented here via
> that probe, not via feature 005's config validation.

### Edge Cases

- What happens when the composed PostgreSQL 18 port differs from the default?
  (Must be overridable via env var without editing `application-dev.properties`.)
- What happens when the Bronze root path is wrong or unwritable? (Boot should
  not silently succeed; feature 005 will own fail-fast validation, but the
  default must match the 001-FR-02 mount contract.)
- What happens when an invalid config value is supplied? (On-boarding to
  feature 005's validation; skeleton accepts defaults as documented here.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-006**: Virtual threads MUST be enabled by default in the runtime
  configuration.
- **FR-007**: `application-dev.properties` MUST carry sane local-dev defaults
  (PostgreSQL 18 datasource at `localhost:5432` with the 001-FR-01 dev
  credentials; Bronze root default `/data/bronze`), overridable without code
  change. The compose-network datasource hostname (`df-postgres`) is reachable
  only via env override, for future containerized runs. Pinned env-var override
  channel (no custom config-source code): `QUARKUS_DATASOURCE_JDBC_URL` (native
  SmallRye mapping of `quarkus.datasource.jdbc.url`) and `BRONZE_ROOT` (custom
  key `bronze.root`).
- **FR-007.1**: At boot, the app MUST verify PostgreSQL 18 reachability (e.g.
  validate the datasource connection) and fail startup loudly when unreachable
  — no silent fallback, no fake success. This is a connectivity probe, distinct
  from feature 005's config-value validation.
- **FR-007.2**: At startup, the app MUST emit a log line stating virtual-thread
  status and the resolved datasource location (a small `StartupEvent` observer),
  making SC-005's "explicitly shows" assertion deterministic.

### Key Entities *(include if feature involves data)*

- **runtime config artifact**: `application-dev.properties` (dev-profile
  defaults) + compose env vars + an EMPTY base `application.properties`
  (Quarkus presence marker — VERIFIED required before profile files load;
  holds no values). Not authoritative data, but the contract skeleton
  features 001-004 read; its values must match sub-feature 001-FR-01's compose
  ports/credentials and sub-feature 001-FR-02's mount path. Dev-profile
  placement (per 001-FR-01 clarification) scopes defaults to `quarkus:dev`;
  other profiles must supply their own configuration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-005**: The startup log of a clean `./mvnw quarkus:dev` run explicitly
  shows virtual-thread concurrency enabled and a configured PostgreSQL 18 data
  source.
- **SC-006**: Overriding the datasource port or Bronze root via an environment
  variable (`QUARKUS_DATASOURCE_JDBC_URL`, `BRONZE_ROOT`) changes effective
  configuration with zero code or file edits.
- **SC-007**: With PostgreSQL 18 stopped, a `./mvnw quarkus:dev` boot terminates
  with a visible connection error (no silent success).

## Assumptions

- Datasource default points at the compose service on the host loopback
  (`localhost:5432`), matching sub-feature 001-FR-01's loopback-only binding.
  The compose-network hostname (`df-postgres`) is NOT the default; it is
  reachable only via env override for future containerized app runs.
- Bronze root default is `/data/bronze`, matching sub-feature 001-FR-02's
  container-side mount path and the constitution.
- Consul 2.0 KV sourcing and fail-fast validation of required keys are explicitly
  deferred to feature 005; no Consul 2.0 client dependency is added here.
- Depends on sub-feature 001-FR-03 (bootable skeleton) and 001-FR-01/02
  (compose + mount) for its acceptance scenarios.