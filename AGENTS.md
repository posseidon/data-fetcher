# data-fetcher Development Guidelines

Auto-generated from feature plans. Last updated: 2026-09-05

## Active Technologies
- Java 21 (enforced via `maven.compiler.release=21` + maven-enforcer `requireJavaVersion [21,22)`) + Quarkus LTS 3.33.3.1 (`io.quarkus.platform:quarkus-bom`) — RESTEasy Reactive + Jackson (`quarkus-rest-jackson`), JPA Panache, PostgreSQL 18 JDBC; SmallRye Config (baked into Quarkus core); test-scope `quarkus-junit` (001-project-skeleton)
- Maven 3.9.9 via committed wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.jar`) — clone is self-contained offline (001-FR-03)
- N/A — no authoritative data in this sub-feature (Bronze/PostgreSQL consumers arrive with features 002-004) — `application.properties` deferred to 001-FR-04 (001-project-skeleton)
- Host path `~/data/bronze` → container `/data/bronze` (bind mount, not a named volume) (001-FR-02)
- Docker Compose v2 + PostgreSQL 18 + Consul 2.0 (001-project-skeleton)
- PostgreSQL 18 (001-project-skeleton)
- Dev-profile runtime defaults (`application-dev.properties`): virtual threads, PostgreSQL 18 datasource at `localhost:5432`, Bronze root `/data/bronze`, pinned env overrides (`QUARKUS_DATASOURCE_JDBC_URL`, `BRONZE_ROOT`); zero new deps (001-FR-04)

## Project Structure

```text
docker-compose.yml
specs/001-project-skeleton/001-FR-01-infra-compose/
  ├── spec.md        # clarified spec
  ├── plan.md        # implementation plan
  ├── research.md    # decisions + rationale
  ├── data-model.md  # infra footprint model
  ├── quickstart.md  # up/verify/teardown
  └── contracts/
      └── infrastructure-contract.md   # host-facing endpoint contract
specs/001-project-skeleton/001-FR-04-runtime-config/
  ├── spec.md        # clarified spec
  ├── plan.md        # implementation plan
  ├── research.md    # decisions + rationale
  ├── data-model.md  # runtime config keys + boot states
  ├── quickstart.md  # boot/override/pg-down checks
  └── contracts/
      └── runtime-config-contract.md   # effective-config + boot behavior
scripts/consul-seed.sh        # feature 005 scope, pre-existing
```

## Commands

- `docker compose up -d` / `docker compose ps` / `docker compose logs <svc>`
- `docker compose port <service> <port>`
- Health probes: `pg_isready -U df_dev_user -d df_agent_db`, `curl http://localhost:8500/v1/status/leader`, `dig +tcp +short @127.0.0.1 -p 8600 consul.service.consul`

## Code Style

No application code in this sub-feature (infrastructure declaration).
App conventions (Quarkus/Java 21) tracked from 001-FR-03 onward.

## Recent Changes
- 001-project-skeleton: Added dev-profile runtime config (001-FR-04) — `application-dev.properties` defaults + `StartupRuntimeProbe` boot observer (logs VT/datasource/Bronze, probes Postgres reachability)
- 001-project-skeleton: Added Java 21 (enforced via `maven.compiler.release=21` + maven-enforcer `requireJavaVersion [21,22)`) + Quarkus LTS 3.33.3.1 (`io.quarkus.platform:quarkus-bom`) — RESTEasy Reactive + Jackson, JPA Panache, PostgreSQL 18 JDBC; SmallRye Config (baked into Quarkus core); test-scope `quarkus-junit` (001-FR-03)
- 001-project-skeleton: Added Docker Compose v2 infra — PostgreSQL 18 + Consul 2.0 on `df-net` bridge, loopback-only bindings, readiness healthchecks, dynamic-port CI escape hatch.

<!-- MANUAL ADDITIONS START -->
- Implemented 001-FR-04 (2026-09-05): `src/main/resources/application.properties` exists but is EMPTY — Quarkus ignores `application-{profile}.properties` unless a base file sits in the same location (VERIFIED during implementation); all dev defaults live in `application-dev.properties` only. Dev-boot PG-down now FAILS startup loudly (`StartupRuntimeProbe`); Quarkus dev-RUNNER persists after a failed start by design — strict non-zero process exit is proven with the packaged `quarkus-run.jar` (`java -Dquarkus.profile=dev -jar target/quarkus-app/quarkus-run.jar`). Test profile auto-starts a Dev Services PostgreSQL → probe logs `reachable: ok` there, NOT "probe skipped" (that is packaged/prod-only).
<!-- MANUAL ADDITIONS END -->
