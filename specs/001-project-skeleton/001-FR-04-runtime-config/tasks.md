# Tasks: Runtime Configuration & Dev Boot (001-FR-04)

**Input**: Design documents from `specs/001-project-skeleton/001-FR-04-runtime-config/`
**Prerequisites**: plan.md, spec.md (1 user story, P1), research.md, data-model.md, contracts/runtime-config-contract.md

**Tests**: No new tests REQUIRED — the clarified spec's Independent Test is boot
observability (`./mvnw quarkus:dev` log). The existing FR-03 `AppSmokeTest`
must keep passing (test profile has no datasource → probe skipped).

**Organization**: Tasks grouped by user story enabling independent
implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: run in parallel (different files, no dependencies)
- **[Story]**: US1 (maps to spec user stories)
- Exact file paths in every description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the FR-03 baseline is green before any config lands.

- [ ] T001 Validate FR-03 baseline: `./mvnw verify` from repo root → BUILD SUCCESS
  with `AppSmokeTest` passing, and `docker compose ps` shows `df-postgres`
  healthy (001-FR-01 infra up). Gate task — abort if either fails.

**Checkpoint**: Baseline proven; FR-04 files can be added safely.

---

## Phase 2: Foundational (Blocking Prerequisites)

> **Intentionally skipped**: this slice has no cross-story blocking layer — it
> builds directly on the FR-03 root module (already in place). Both story tasks
> depend only on Phase 1.

---

## Phase 3: User Story 1 - Runtime defaults and virtual threads (Priority: P1) 🎯 MVP

**Goal**: Dev-mode runtime defaults take effect: virtual threads enabled,
PostgreSQL 18 datasource at `localhost:5432` with the 001-FR-01 credentials,
Bronze root `/data/bronze` — all overridable via env vars — plus a startup
observer that logs the effective state and fails boot loudly when PG 18 is
unreachable (spec US1, FR-006/007/007.1/007.2).

**Independent Test**: Boot `./mvnw quarkus:dev`, observe the observer log line
containing virtual-threads + datasource + bronze.root (SC-005); re-boot with
env overrides and see them win (SC-006); stop `df-postgres` and confirm boot
fails loudly with a connection error (SC-007; dev runner persists by design,
strict exit-non-zero proven via packaged run); `./mvnw verify` still green.

### Implementation for User Story 1

- [ ] T002 [P] [US1] Create `src/main/resources/application-dev.properties`
  with EXACTLY these dev-profile defaults (research D2/D3/D4):
  `quarkus.thread.virtual.enabled=true`,
  `quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/df_agent_db`,
  `quarkus.datasource.username=df_dev_user`,
  `quarkus.datasource.password=df_dev_secret`, `bronze.root=/data/bronze`.
  ALSO create an EMPTY base `application.properties` — VERIFIED constraint:
  Quarkus ignores `application-{profile}.properties` unless a base
  `application.properties` exists in the same location ("profiled configuration
  file ... is ignored; a main application.properties configuration file must
  exist"). Defaults stay dev-profile-only; the base file is a presence marker
  only and leaks nothing. No pom change.
- [ ] T003 [P] [US1] Create
  `src/main/java/dev/datafetcher/config/StartupRuntimeProbe.java`
  (research D5): `@ApplicationScoped` CDI bean observing `StartupEvent`;
  inject `org.eclipse.microprofile.config.Config` and
  `jakarta.enterprise.inject.Instance<io.agroal.api.AgroalDataSource>`. In the
  observer: (1) log one line
  `runtime defaults: virtual threads=<effective> | datasource=<resolved jdbc
  url> | bronze.root=<resolved path>` (FR-007.2; datasource/bronze only when
  configured); (2) if `dataSource.isResolvable()` (CDI 4 `Instance` API — not
  `isPresent()`), open+close a connection — on
  `SQLException` throw, terminating boot loudly (FR-007.1); else log
  `no datasource configured — probe skipped`. No new dependencies.
- [ ] T004 [US1] Verify SC-005 (baseline boot): with `df-postgres` up, run
  `./mvnw quarkus:dev` — startup log MUST contain the observer line showing
  `virtual threads=true`, `datasource=jdbc:postgresql://localhost:5432/df_agent_db`,
  `bronze.root=/data/bronze`; no ERROR lines. Ctrl-C.
- [ ] T005 [US1] Verify SC-006 (env override wins):
  `QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5433/df_agent_db BRONZE_ROOT=/tmp/df-bronze ./mvnw quarkus:dev`
  — observer line reports `:5433` and `/tmp/df-bronze`, zero file edits.
  Ctrl-C. Confirm `src/main/resources/application-dev.properties` unchanged.
- [ ] T006 [US1] Verify SC-007 (PG-down): `docker compose stop df-postgres`,
  then dev boot — startup MUST fail loudly with the probe's connection error
  (no `Listening on` / "started in" line). Two granularities VERIFIED: (a)
  `./mvnw quarkus:dev` — startup aborts but the dev RUNNER persists by design
  (Quarkus keeps the JVM alive awaiting changes); (b) strict process exit
  (non-zero) via packaged run:
  `./mvnw package -DskipTests && java -Dquarkus.profile=dev -jar target/quarkus-app/quarkus-run.jar`
  → exit code 1. Then `docker compose start df-postgres` and confirm recovery
  returns to a healthy boot.
- [ ] T007 [US1] Verify no regression: `./mvnw verify` still passes
  `AppSmokeTest`. NOTE: the test profile auto-starts a Dev Services PostgreSQL
  (no `application-test.properties` exists) → the probe logs the Dev Services
  URL + `reachable: ok`, NOT "probe skipped"; only packaged/prod profiles (no
  datasource) log "probe skipped".

**Checkpoint**: User Story 1 functional and testable independently — MVP
complete.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Verify the contracts/docs match reality and no prior docs were left
contradictory.

- [ ] T008 [P] Dependency-lock check: `./mvnw dependency:list` — confirm NO new
  dependencies vs the FR-03 set (probe uses `io.agroal`, Config, JDBC already
  on classpath); certify `contracts/runtime-config-contract.md` §Effective
  config matches actual defaults.
- [ ] T009 [P] Quickstart walkthrough: execute every command in
  `specs/001-project-skeleton/001-FR-04-runtime-config/quickstart.md`
  (defaults boot, env override, PG-down, verify); fix drift; replace the
  placeholder observer line with the ACTUAL log output observed in T004; record
  the measured `quarkus:dev` boot time (research D7, SC-003 style baseline).
- [ ] T010 [P] Cross-feature contract sync: confirm the 001-FR-04 coordination
  edits are present and consistent — FR-03 `build-contract.md` (dev-profile
  PG-down posture supersession + `application-dev.properties` extension point),
  FR-03 `data-model.md` (PG-down state rows + config-home path), FR-02
  `bronze-mount-contract.md` (default path owner name). Fix any drift.
- [ ] T011 [P] Terminology scan: `rg -n "application\.properties" specs/` —
  any remaining literal default-artifact references MUST resolve to
  `application-dev.properties` (or be a deliberate base-file note); no
  contradictory "boot succeeds with PG down" text may remain for the dev
  profile.

**Checkpoint**: Contract verified, docs accurate, no stale references — feature
complete.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — baseline gate first
- **Foundational (Phase 2)**: skipped by design (no shared blocking layer)
- **US1 (Phase 3)**: depends ONLY on Phase 1
- **Polish (Phase 4)**: depends on US1

### User Story Dependencies

```text
Phase 1 (baseline verify)
        │
        └──→ US1 (properties + observer + 4 boot verifications)
                 └──→ Phase 4 (Polish)
```

- **User Story 1 (P1)**: after Phase 1 — no other story dependencies (only US1
  exists in this spec).

### Within Each User Story

- T002/T003 (file creation) before any boot verification (T004-T007).
- T004 → T005 → T006 run sequentially? — each is an independent dev-mode boot
  session; they share no state. Can run in sequence on one workstation; do NOT
  run overlapping `quarkus:dev` sessions (port 8080 contention).

### Parallel Opportunities

- Phase 1: T001 alone (gate).
- US1: T002 and T003 run in parallel (different files: properties vs Java).
- Phase 4: T008/T009/T010/T011 all parallel (different files/commands).

---

## Parallel Example

```bash
# US1 — both files at once:
Task: "Create src/main/resources/application-dev.properties with dev defaults (T002)"
Task: "Create src/main/java/dev/datafetcher/config/StartupRuntimeProbe.java observer (T003)"

# Polish — all at once:
Task: "Dependency-lock check (T008)"
Task: "Quickstart walkthrough + baseline record (T009)"
Task: "Cross-feature contract sync check (T010)"
Task: "Terminology scan for application.properties references (T011)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: baseline gate.
2. Phase 3: US1 → properties + observer + four boot verifications.
3. **STOP and VALIDATE**: SC-005 (defaults log), SC-006 (overrides win),
   SC-007 (PG-down fails), verify green. This is the deliverable MVP.

### Incremental Delivery

1. Phase 1 → baseline proven.
2. US1 → defaults + observer verified → MVP.
3. Polish → contract/lock/docs validated, baseline recorded.

## Notes

- [P] tasks = different files/commands, no dependencies; avoid same-file
  concurrency and overlapping `quarkus:dev` sessions.
- Commit after each logical group (properties file, observer, each boot
  verification).
- Base `application.properties` exists but stays EMPTY (Quarkus presence marker
  — required before profile files load); dev defaults must NOT be placed there.
  No Consul client / config-value validation (feature 005). Do NOT modify
  `pom.xml` or `docker-compose.yml`.
- The PG-down fail-fast behavior SCOPE: dev profile only (SC-007), implemented
  by the T003 probe; this supersedes the FR-03 sub-feature's lazy posture for
  dev mode (spec clarification Q3).