# Research: Runtime Configuration & Dev Boot (001-FR-04)

Phase 0 output — decisions, rationale, alternatives. All five spec ambiguities
were resolved in `/speckit.clarify` (recorded in `spec.md` §Clarifications), so
no `NEEDS CLARIFICATION` items remain.

## D1 — Config artifact placement: dev profile

- **Decision**: Defaults live in `src/main/resources/application-dev.properties`
  (dev profile), not base `application.properties`.
- **Rationale**: Spec clarification Q2 (2026-09-05) plus 001-FR-01 FR-010.1
  ("credentials mirrored into the Quarkus application-dev.properties default
  profile"). `quarkus:dev` activates the dev profile, so SC-005/006/007 all
  exercise these defaults; the test profile (`AppSmokeTest`) and any future
  packaged/prod profile stay free of local-dev placeholders.
- **Alternatives considered**: base `application.properties` (rejected as the
  *host* of defaults — leaks dev placeholders into test/packaged runs and
  contradicts the FR-01 mirroring note; BUT an EMPTY base file is kept as a
  Quarkus presence marker, see amendment below); `application-development.properties`
  (nonstandard — Quarkus uses the `-dev` convention).
- **Verified amendment (implementation, 2026-09-05)**: Quarkus refuses to load
  `application-dev.properties` unless a base `application.properties` exists in
  the same directory (log: "profiled configuration file ... is ignored; a main
  application.properties configuration file must exist"). An EMPTY base file is
  therefore REQUIRED. This does not change the decision's substance — defaults
  still live in the dev profile only and the empty base leaks nothing.

## D2 — Virtual threads

- **Decision**: `quarkus.thread.virtual.enabled=true` in the dev profile
  (FR-006). Default when absent is `false`, so the explicit `true` is required.
- **Rationale**: The property is the Quarkus-native virtual-thread switch; it
  routes CDI/request executors to virtual threads. No extension needed.
- **Alternatives considered**: relying on any implicit default (there is none —
  must be explicit); per-feature `@WithVirtualThreads` (feature 002+ concern,
  not a runtime default).

## D3 — Datasource defaults (from infrastructure-contract)

- **Decision**: Mirror the 001-FR-01 credential tuple exactly:
  `quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/df_agent_db`,
  `quarkus.datasource.username=df_dev_user`,
  `quarkus.datasource.password=df_dev_secret`. `db-kind` is inferred from the
  JDBC URL (`postgresql` → PostgreSQL 18).
- **Rationale**: Spec clarification Q1 pins `localhost:5432` — the compose
  binding is loopback-only (`127.0.0.1:5432`), and FR-04 acceptance boots the
  app on the host, so the host URL is the only reachable default. The
  in-net hostname (`df-postgres`) is an override path only. `infrastructure-contract.md`
  demands these exact values mirror here.
- **Alternatives considered**: in-net default `jdbc:postgresql://df-postgres:5432/...`
  (rejected by clarification Q1 — unreachable from a host-launched JVM);
  explicit `quarkus.datasource.db-kind=postgresql` (redundant — URL-scheme
  inference).

## D4 — Bronze root key

- **Decision**: Custom property `bronze.root=/data/bronze` (SmallRye Config),
  env override `BRONZE_ROOT`.
- **Rationale**: Spec clarification Q5 pins `bronze.root`/`BRONZE_ROOT` as the
  override channel; the default matches the 001-FR-02 container-side mount and
  the constitution's default Bronze root. No consumer code reads it this slice —
  bust defaults contract only (edge case: path validation stays feature 005).
- **Alternatives considered**: namespaced `datafetcher.bronze.root` (longer,
  not clarified); Consul-style key (feature 005 owns Consul; adding a config
  source here is forbidden).

## D5 — Startup probe + observer (StartupRuntimeProbe)

- **Decision**: One `@ApplicationScoped` CDI bean observing `StartupEvent`:
  1. Log effective runtime state — virtual-threads flag, resolved
     `quarkus.datasource.jdbc.url`, resolved `bronze.root` (FR-007.2; the
     Bronze line makes SC-006 fully assertable).
  2. If an `AgroalDataSource` is configured (`Instance<AgroalDataSource>`),
     take + close a connection; on `SQLException` throw — boot fails loudly
(FR-007.1, SC-007). If no datasource is configured (packaged/prod
   profiles — the test profile gets a Dev Services PostgreSQL and logs
   `reachable: ok`), log "no datasource configured — probe skipped" and proceed.
- **Rationale**: Zero new dependencies — `AgroalDataSource` and Config are
  already on the FR-03 classpath; `Instance<>` is the CDI-idiomatic way to keep
  the bean valid when the datasource is absent. It honors both the "fail fast at
  boot" spirit (Point 1) and the clarified non-goal (no Consul/config-value
  validation — feature 005).
- **Alternatives considered**: raw `DriverManager` probe (manual URL/credential
  parsing — more string handling, less idiomatic than the pooled bean);
  `quarkus-smallrye-health` readiness check (does NOT fail boot and adds an
  unapproved extension); feature-005 fail-fast (explicitly out of scope here).

## D6 — PG-down posture reconciliation with 001-FR-03

- **Decision**: The dev profile now TERMINATES when PostgreSQL 18 is
  unreachable (probe). Non-dev profiles keep the FR-03 lazy posture (no
  datasource configured → nothing to fail). Sibling contract lines in
  `build-contract.md` and FR-03 `data-model.md` that assert the old "boot
  succeeds with PG down" wording are corrected in this slice.
- **Rationale**: Spec clarification Q3 (keep scenario 4) explicitly overrides
  the FR-03 clarified posture for this slice; scenario 4 and SC-007 require the
  loud failure. `AppSmokeTest` is unaffected (test profile uses a Dev Services
  datasource — probe logs `reachable: ok`).
- **Alternatives considered**: deferring the probe to feature 005 (rejected in
  clarify Q3 — scenario 4 would then be untestable at FR-04 scope).

## D7 — Measured boot baseline

- **Decision**: Record the actual dev-boot startup time + observer output in
  `quickstart.md` during implementation (warm toolchain), matching SC-003/SC-005
  evidence style.
- **Rationale**: SC-005 asserts the log "explicitly shows" state; the observer
  line is the deterministic assertion point, and a recorded sample gives the
  implementer an expected-shape reference.
- **Alternatives considered**: no recorded sample (weaker verification contract).