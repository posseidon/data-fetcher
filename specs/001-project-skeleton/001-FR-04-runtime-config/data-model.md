# Data Model: Runtime Configuration & Dev Boot (001-FR-04)

Phase 1 output. This feature produces a *configuration contract*, not
authoritative data — the "entity" is the effective runtime config and the
observation points later features (001-004) and feature 005 bind against.

## Entities

### Runtime config artifact (`application-dev.properties`)

| Property key | Default | Env override | Purpose | Source |
|--------------|---------|--------------|---------|--------|
| `quarkus.thread.virtual.enabled` | `true` | `QUARKUS_THREAD_VIRTUAL_ENABLED` | virtual-thread concurrency (FR-006) | clarify D2 |
| `quarkus.datasource.jdbc.url` | `jdbc:postgresql://localhost:5432/df_agent_db` | `QUARKUS_DATASOURCE_JDBC_URL` | PostgreSQL 18 host URL (FR-007) | clarify Q1/D3, infra-contract |
| `quarkus.datasource.username` | `df_dev_user` | `QUARKUS_DATASOURCE_USERNAME` | dev DB user | infra-contract FR-010.1 |
| `quarkus.datasource.password` | `df_dev_secret` | `QUARKUS_DATASOURCE_PASSWORD` | dev DB password (placeholder, not a secret) | infra-contract FR-010.1 |
| `bronze.root` | `/data/bronze` | `BRONZE_ROOT` | Bronze root default (FR-007/SC-006) | clarify Q5/D4, bronze-mount-contract |

`db-kind` is NOT set explicitly — inferred as `postgresql` from the JDBC URL
scheme (research D3).

### Boot probe observer (`StartupRuntimeProbe`)

| Attribute | Value | Source |
|-----------|-------|--------|
| type | `@ApplicationScoped` CDI bean, `@Observes StartupEvent` | research D5 |
| logs | virtual-threads flag, resolved datasource URL, resolved `bronze.root` | FR-007.2 / research D5 |
| probes | `Instance<AgroalDataSource>` → open+close connection when present | FR-007.1 / research D5 |
| failure | unhandled `SQLException` → boot terminates loudly | SC-007 |
| location | `src/main/java/dev/datafetcher/config/StartupRuntimeProbe.java` | plan structure |

## Lifecycle / state transitions

| State | Trigger | Signal |
|-------|---------|--------|
| dev boot, PG up | `./mvnw quarkus:dev` + infra running | observer logs defaults + probe passes → normal boot (SC-005) |
| dev boot, PG down | `./mvnw quarkus:dev`, PG unreachable | probe `SQLException` → boot terminates with connection error (SC-007) |
| env override applied | `QUARKUS_DATASOURCE_JDBC_URL` / `BRONZE_ROOT` set | observer logs overridden values, zero file edits (SC-006) |
| non-dev profile boot | `./mvnw verify` / packaged | test: Dev Services PostgreSQL auto-started → probe logs its URL + reachable: ok, smoke test stays green; packaged/prod: no datasource → "probe skipped" |
| default path invalid | Bronze root unwritable (host `/data/bronze` absent) | NOT validated here — feature 005 fails fast (spec edge case, deliberate) |

## Constraints & validation rules

- Base `application.properties` MUST exist but stay EMPTY (Quarkus presence
  marker — required before any profile file loads); all default VALUES must
  stay in `application-dev.properties` and leak into no other profile
  (research D1; the "must not exist" constraint was inverted by a VERIFIED
  Quarkus requirement during implementation).
- Default datasource MUST stay `localhost:5432` + the 001-FR-01 credential
  tuple; `df-postgres` is an override value only (infrastructure-contract change
  policy).
- Default `bronze.root` MUST stay `/data/bronze` (010-FR-02 + constitution
  contract) unless amended.
- Zero new pom dependencies (Agroal/Config/JDBC already on classpath).
- No Consul client/config source, no config-value validation (feature 005).

## Derived constants (the contract later features read)

- Pinned override channel: `QUARKUS_DATASOURCE_JDBC_URL`, `BRONZE_ROOT`,
  `QUARKUS_DATASOURCE_USERNAME`, `QUARKUS_DATASOURCE_PASSWORD`
  (SmallRye env mapping; no custom config-source code).
- Observer log line shape: runtime-defaults echo + probe outcome (defined in
  `contracts/runtime-config-contract.md` §Verification).
- Dev boot command: `./mvnw quarkus:dev`; SC targets: 005 (log shows), 006
  (override wins), 007 (PG-down terminates).