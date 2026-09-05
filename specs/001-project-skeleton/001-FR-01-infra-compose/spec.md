# Feature Specification: Local Infrastructure Compose

**Feature Branch**: `001-project-skeleton` (sub-feature 001-FR-01)
**Created**: 2026-09-05
**Status**: Draft
**Input**: Decomposition of feature 001 (Project Skeleton), parent user story "Boot local infrastructure" (P1) and parent FR-002, FR-005, FR-010.

## Clarifications

### Session 2026-09-05

- Q: What are the local-dev Postgres credentials and DB naming? → A: POSTGRES_DB=df_agent_db, POSTGRES_USER=df_dev_user, POSTGRES_PASSWORD=df_dev_secret, mirrored into the Quarkus application-dev.properties default profile.
- Q: Port & container identity conventions? → A: explicit `df-net` bridge network; container names prefixed `df-`; Quarkus app 8080, database admin console 8081, PostgreSQL 5432, Consul 8500 + 8600/udp; parallel CI runs set host ports to `0` and resolve dynamically (Testcontainers).
- Q: Which host interfaces get port mappings? → A: loopback only — every mapping bound to `127.0.0.1` (e.g. `127.0.0.1:5432:5432`); container-to-container traffic stays on the isolated bridge via internal hostnames.
- Q: Which Consul ports are exposed to the host? → A: only client-facing ports on loopback — 8500 (HTTP API/Web UI) and 8600/udp (DNS); gossip ports 8300/8301/8302 remain unmapped (internal bridge only).
- Q: What defines a "healthy" container for SC-001? → A: explicit `healthcheck` per service (`pg_isready` for PostgreSQL, Consul agent-status) plus `depends_on.condition: service_healthy` wiring.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Boot local infrastructure (Priority: P1)

A developer brings up the local dependencies with a single command
(`docker compose up -d`). PostgreSQL 18 and Consul 2.0 start in containers without
errors; the compose file is the single source of truth for local
infrastructure.

**Why this priority**: Every other feature (catalog, jobs, artifacts, config)
depends on PostgreSQL 18 and Consul 2.0 being reachable in local dev. A one-command
boot proven here unblocks everything downstream.

**Independent Test**: Can be fully tested by running the single compose
command against a clean `docker-compose.yml`, then verifying both containers
report healthy.

**Acceptance Scenarios**:

1. **Given** a fresh checkout containing only this sub-feature's compose file,
   **When** a developer runs `docker compose up -d`, **Then** PostgreSQL 18 and
   Consul 2.0 containers start without errors and report a healthy state.
2. **Given** the compose file, **When** a developer inspects it, **Then** it
   declares exactly PostgreSQL 18 and Consul 2.0 as infrastructure containers, with
   no extra containers or volumes.
3. **Given** a container already occupying a host port, **When** a developer
   runs `docker compose up -d`, **Then** the command fails loudly with a
   clear port-conflict message.

---

> Only one user story: booting the shared infrastructure. Consul 2.0 runtime wiring
> (client, keys, fail-fast) is owned by feature 005, not this sub-feature.

### Edge Cases

- What happens when host port 5432 or the Consul 2.0 port is already in use?
  (Compose must fail loudly with a clear port-conflict message, or expose
  configurable host ports.)
- What happens when `docker compose up -d` is run twice? (Must be idempotent;
  already-running containers report healthy, no restart churn.)
- What happens when Docker is not running? (Compose must fail with a clear
  Docker-daemon error, not a partial boot.)
- How do concurrent E2E suites share one CI runner when fixed host ports are
  taken? (Set host ports to `0` and let the test framework resolve the
  assigned ports; FR-002.3 requirement, not the default dev behavior.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-002**: `docker compose up -d` MUST start PostgreSQL 18 and Consul 2.0
  containers; the compose file MUST be the single source of truth for local
  dependencies.
- **FR-002.1**: The compose file MUST use `docker compose` v2 syntax and
  declare an explicit `df-net` bridge network; service/container names MUST be
  prefixed `df-` to avoid daemon-level conflicts.
- **FR-002.2**: Host mappings MUST bind exclusively to the loopback interface
  (`127.0.0.1:<host>:<container>`): Quarkus app 8080, database admin console
  8081, PostgreSQL 5432, Consul 8500 (HTTP/UI) and 8600/udp (DNS).
  Container-to-container communication MUST use internal hostnames over the
  isolated bridge.
- **FR-002.3**: For concurrent CI runs the compose file MUST support dynamic
  host ports (`0:<container>`), resolved by the test framework (Testcontainers).
- **FR-002.4**: Every service MUST declare an explicit `healthcheck`
  (`pg_isready` for PostgreSQL, agent-status for Consul) and services that
  depend on readiness MUST use `depends_on.condition: service_healthy`.
- **FR-005**: Consul 2.0 MUST appear as an infrastructure container only; no
  Consul 2.0 client/config-source dependency belongs in this sub-feature (wiring
  is feature 005).
- **FR-005.1**: Only Consul client-facing ports MUST be host-reachable, on
  loopback: 8500 (HTTP API/Web UI) and 8600/udp (DNS). Gossip ports
  8300/8301/8302 MUST NOT be mapped; they resolve only inside the bridge.
- **FR-010**: Secrets MUST NOT be committed; any default credentials are local
  dev-only placeholders in the compose file.
- **FR-010.1**: Local dev credentials are the fixed placeholders
  `POSTGRES_DB=df_agent_db`, `POSTGRES_USER=df_dev_user`,
  `POSTGRES_PASSWORD=df_dev_secret`, mirrored into the Quarkus
  application-dev.properties default profile.

### Key Entities *(include if feature involves data)*

- **Infrastructure footprint**: the two containers (PostgreSQL 18, Consul 2.0) form
  the local runtime environment the remaining sub-features and features
  002-005 connect to. Not authoritative data, but the reachable prerequisite.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `docker compose up -d` reaches a healthy state for both
  containers (PostgreSQL 18 and Consul 2.0) with zero manual steps beyond the
  documented command.

## Assumptions

- Default credentials in the compose file are throwaway local-dev values, not
  secrets (per parent FR-010 and constitution security posture).
- Consul 2.0 exposes agent ports 8500 (HTTP/UI) and 8600/udp (DNS) on the
  loopback for later features to bind; no Consul 2.0 client code ships here.
- PostgreSQL 18 exposes port 5432 on the loopback; credentials
  (`df_agent_db` / `df_dev_user` / `df_dev_secret`) must be mirrored in the
  datasource defaults of sub-feature 001-FR-04.
- This sub-feature is testable without the application; the Bronze bind mount
  is a separate sub-feature (001-FR-02).
- Acceptance scenario 2 keeps the footprint at exactly PostgreSQL 18 + Consul
  2.0; an admin console (e.g. Adminer) is NOT added as a container here.