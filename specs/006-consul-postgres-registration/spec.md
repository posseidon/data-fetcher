# Feature Specification: PostgreSQL Consul Registration

**Feature Branch**: `006-consul-postgres-registration`
**Created**: 2026-09-05
**Status**: Draft
**Input**: Add new functional requirement to docker-compose.yml to register PostgreSQL into Consul.

## Clarifications

### Session 2026-09-05

- Q: What address/port does `df-postgres` advertise in the Consul registration? → A: The container's IP on the `df-net` bridge network, port 5432.
- Q: Should PostgreSQL wait for Consul readiness before starting? → A: Yes; PostgreSQL depends on Consul being healthy (`depends_on.condition: service_healthy`).
- Q: What health check schedule and recovery behavior? → A: `pg_isready` interval 10s, timeout 5s, start_period 30s; status auto-recovers to "passing" once checks pass.
- Q: When should `df-postgres` be removed from the catalog? → A: Auto-deregister when health stays critical ~1 minute (`deregister_critical_service_after`), covering both clean stop and crash.
- Q: (planning amendment, FR-005.3) Compose has no native Consul registration element — how is registration declared? → A: Declarative service-definition JSON named `df-postgres.json`, part of the compose project, bind-mounted into the Consul agent config-dir; `df-postgres` gets a static `df-net` IP so the advertised address is known at agent start.
- Q: (planning amendment, FR-005.4) `pg_isready` cannot run inside the Consul container — what check replaces it? → A: Consul built-in TCP check on `df-postgres:5432` (readiness-equivalent), keeping interval 10s, timeout 5s, `deregister_critical_service_after` 1m, and auto-recovery. Consul has no `start_period` check option; check starts critical until first pass.
- Q: (implementation, FR-005.4) After auto-deregistration, how does a recovered PostgreSQL return to the catalog? → A: `consul reload` (agent re-reads config-dir) or consul container restart; verified during implementation.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - PostgreSQL auto-registers in Consul (Priority: P1)

A developer runs `docker compose up -d` and PostgreSQL automatically registers
itself as a service in Consul with health checks. The service appears in
Consul's catalog immediately after both containers are healthy.

**Why this priority**: Service discovery is foundational for all downstream
features. Without Consul registration, no component can discover PostgreSQL
dynamically.

**Independent Test**: Can be fully tested by running `docker compose up -d`,
then querying Consul catalog to verify PostgreSQL service exists with healthy
status.

**Acceptance Scenarios**:

1. **Given** a fresh checkout containing the compose file,
   **When** a developer runs `docker compose up -d`,
   **Then** PostgreSQL registers as `df-postgres` in Consul with tags `[db, postgres]`
   and reports healthy status.

2. **Given** PostgreSQL is registered in Consul,
   **When** a developer queries Consul catalog (`curl http://127.0.0.1:8500/v1/catalog/service/df-postgres`),
   **Then** the response contains the service with health status "passing".

3. **Given** PostgreSQL container is unhealthy,
   **When** Consul performs health check,
   **Then** the service status in Consul reflects "critical" or "failing".

---

### Edge Cases

- What if Consul is already running from a previous boot? (Registration must be idempotent — no duplicate `df-postgres` entries.)
- What happens when PostgreSQL container restarts? (Service should re-register automatically.)
- What happens when health check fails? (Consul should mark service as critical.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-005.2**: The compose file MUST include a Consul service definition that
  registers PostgreSQL as `df-postgres` with tags `[db, postgres]`, advertising
  the container's `df-net` bridge IP on port 5432.

- **FR-005.3**: Registration MUST be declared via a service-definition file
  (`df-postgres.json`) shipped with the compose project and bind-mounted into
  the Consul agent's config directory. `df-postgres` MUST hold a static IP on
  `df-net` so the advertised address is fixed. No init container or
  registration-side script on the PostgreSQL service itself.

- **FR-005.4**: Consul MUST use its built-in TCP check on
  `df-postgres:5432` to monitor PostgreSQL readiness — interval 10s, timeout
  5s, `deregister_critical_service_after` 1m. Health status MUST auto-recover
  to "passing" once checks pass after a failure; if the service was
  auto-deregistered, recovery requires a Consul config reload
  (`consul reload`).

- **FR-005.5**: PostgreSQL service MUST appear in Consul catalog with health
  status "passing" when both containers are healthy. PostgreSQL MUST start only
  after Consul reports healthy (`depends_on.condition: service_healthy`).

### Key Entities *(include if feature involves data)*

- **Consul service registration**: the mechanism by which PostgreSQL declares
  itself to Consul for service discovery. Includes service name, tags, and
  health check configuration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: PostgreSQL service appears in Consul catalog within 30 seconds
  of `docker compose up -d` completing.
- **SC-002**: Health check status accurately reflects PostgreSQL readiness
  (passing when accepting connections, critical when down).

## Assumptions

- Consul service registration method is a declarative `df-postgres.json` service definition bind-mounted into the agent config-dir.
- `df-postgres` holds a static `df-net` address; the port is 5432.
- Service name `df-postgres` and tags `[db, postgres]` are acceptable.
- Consul health check is a TCP check on `df-postgres:5432`: interval 10s, timeout 5s, auto-recovery enabled.
