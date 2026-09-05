# Tasks: Quarkus Application Skeleton (001-FR-03)

**Input**: Design documents from `specs/001-project-skeleton/001-FR-03-quarkus-skeleton/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: One boot smoke test is REQUIRED — mandated by the clarified spec
(2026-09-05 Q2: `quarkus-junit` test scope + one smoke test so `./mvnw verify`
is non-trivial). No other tests.

**Organization**: Tasks grouped by user story (US1 P1 build+run, US2 P2 build
integrity) enabling independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: run in parallel (different files, no dependencies)
- **[Story]**: US1/US2 (maps to spec user stories)
- Exact file paths in every description

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization — the single root Maven/Quarkus module plus
wrapper and ignore rules that both user stories boot against.

- [ ] T001 [P] Create `.gitignore` at repo root: ignore `target/`, IDE files
  (`.idea/`, `*.iml`, `.vscode/`, `.DS_Store`), and `*.log`; MUST NOT ignore
  `mvnw`, `mvnw.cmd`, or `.mvn/` (committed wrapper, spec edge case).
- [ ] T002 [P] Create root `pom.xml`:
  - coordinates `dev.datafetcher:data-fetcher`, `packaging=jar`
  - `quarkus.platform.version=3.33.3.1` + `<dependencyManagement>` import of
    `io.quarkus.platform:quarkus-bom` (research D1)
  - `<maven.compiler.release>21</maven.compiler.release>` + maven-enforcer
    `requireJavaVersion` rule with range `[21,22)` (research D5)
  - deps: `quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`,
    `quarkus-jdbc-postgresql`, test-scope `quarkus-junit` (FR-004 + clarify Q2)
  - `quarkus-maven-plugin` 3.33.3.1 (`quarkus:dev` goal)
  - NO WebSocket/MCP/Consul-client artifacts (FR-004)
  - create empty trees `src/main/java/`, `src/test/java/` (runtime config
    `src/main/resources/application.properties` is 001-FR-04 scope — do NOT
    create it)
- [ ] T003 [P] Add committed Maven wrapper 3.9.9: generate (local `mvn`) or copy
  known-good wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.jar`,
  `.mvn/wrapper/maven-wrapper.properties` pinned `distributionUrl` to
  apache-maven-3.9.9-bin.zip + `wrapperUrl` to maven-wrapper-3.3.2.jar);
  verify `./mvnw -v` prints 3.9.9 (research D4, FR-001).

**Checkpoint**: `./mvnw -v` works with no pre-installed Maven; ground ready for
both stories.

> **Foundational (Phase 2)**: intentionally skipped — this single-module
> skeleton has no cross-story blocking layer beyond Setup. Both user stories
> depend only on Phase 1.

---

## Phase 2: User Story 1 - Build and run the app (Priority: P1) 🎯 MVP

**Goal**: `./mvnw quarkus:dev` boots the empty Quarkus app on Java 21, reports
its HTTP port, serves no feature endpoints, and boots successfully even with
PostgreSQL 18 down (clarified Q1).

**Independent Test**: Run `./mvnw quarkus:dev` on a Java 21 toolchain, observe a
successful startup log (`Listening on: http://localhost:8080`, `Quarkus
3.33.3.1 ... started`, no ERROR lines), confirm no endpoints render
(`curl -i localhost:8080/` → no feature route / 404), then Ctrl-C.

### Implementation for User Story 1

- [ ] T004 [US1] Run dev boot verification (`./mvnw quarkus:dev`):
  - startup log reports `http://localhost:8080` (SC-003) with zero ERROR lines,
    Quarkus 3.33.3.1 on Java 21 (US1 acceptance 1, FR-008)
  - `curl -i localhost:8080/` serves no feature endpoints (US1 acceptance 2)
  - Ctrl-C stops dev mode cleanly
- [ ] T005 [US1] Verify PG-down posture (clarify Q1): with infra down
  (`docker compose ps` empty), `./mvnw quarkus:dev` still boots successfully —
  no boot-time datasource failure, no silent fallback; DB errors surface at
  first use only. Note: no datasource is configured in this slice (001-FR-04
  owns it), so the expected result is a normal boot.

**Checkpoint**: User Story 1 functional and testable independently — MVP
complete.

---

## Phase 3: User Story 2 - Verify build integrity (Priority: P2)

**Goal**: `./mvnw package` and `./mvnw verify` succeed on a clean checkout; the
mandated dependency set resolves/compiles and the boot smoke test executes
(SC-004, clarified US2).

**Independent Test**: On a clean checkout (no `target/`), `./mvnw package` →
BUILD SUCCESS and `./mvnw verify` → 1 smoke test passed; a wrong-JDK build
fails with the enforcer's clear message.

### Tests for User Story 2 (REQUIRED — clarified 2026-09-05) ⚠️

> Note: this is a smoke guard, not a red-green behavior test — it passes once
> infra is wired and turns red only when the dependency set or boot breaks.

- [ ] T006 [P] [US2] Write boot smoke test
  `src/test/java/dev/datafetcher/AppSmokeTest.java`: `@QuarkusTest` asserting
  `quarkus.http.port` resolves to a numeric port (boots full Quarkus context + exercises
  SmallRye Config; research D7). Uses only `quarkus-junit` (no rest-assured).

### Implementation for User Story 2

- [ ] T007 [US2] Run `./mvnw package` on a clean checkout (fresh clone or
  `rm -rf target/`) → BUILD SUCCESS (US2 acceptance 1, SC-004).
- [ ] T008 [US2] Run `./mvnw verify` → `AppSmokeTest` executes and passes (US2
  acceptance 3, clarified).
- [ ] T009 [US2] Run wrong-toolchain gate: build with a non-21 JDK
  (`JAVA_HOME` set to a 17 or 22 JDK, reusing existing `target/` or a fresh
  one) → maven-enforcer fails with the clear "JDK version not in allowed range
  [21,22)" message, not a cryptic class-version error (spec edge case).

**Checkpoint**: User Stories 1 AND 2 both work independently.

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Verify the declared contract and docs match reality.

- [ ] T010 [P] Dependency-lock check: run `./mvnw dependency:list` and verify
  the runtime set contains exactly RESTEasy Reactive + Jackson, Hibernate ORM
  Panache, PostgreSQL JDBC, and test-scope `quarkus-junit`; assert NO
  WebSocket/MCP/Consul-client artifacts (contracts/build-contract.md lock,
  FR-004).
- [ ] T011 [P] Quickstart walkthrough: execute every command in
  `specs/001-project-skeleton/001-FR-03-quarkus-skeleton/quickstart.md`
  (toolchain check, dev boot, package, verify, troubleshooting) and fix any
  drift between docs and actual behavior.
- [ ] T012 [P] Measure SC-003 on a warmed toolchain: record cold-start dev-boot
  time (`quarkus:dev` to "started" log) — confirm ~2 min / zero build errors
  target; record the number in quickstart or research as a measured baseline.
- [ ] T013 Refresh agent context: run
  `bash .specify/scripts/bash/update-agent-context.sh opencode` to sync
  AGENTS.md with the final module structure.

**Checkpoint**: Contract verified, docs accurate, context synced — feature
complete.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately
- **US1 + US2 (Phases 2-3)**: both depend ONLY on Phase 1; independent of each
  other (US1 needs no test code; US2 needs no main code)
- **Polish (Phase 4)**: depends on US1 + US2

### User Story Dependencies

```text
Phase 1 (Setup: .gitignore, pom.xml, wrapper)
        │
        ├──→ US1  (boot verification)   ──┐
        └──→ US2  (smoke test + verify) ──┴──→ Phase 4 (Polish)
```

- **User Story 1 (P1)**: after Setup — no dependency on US2
- **User Story 2 (P2)**: after Setup — no dependency on US1
- **Polish**: after both stories

### Within Each User Story

- US1: sequential verification steps (one app, one boot session).
- US2: smoke test (T006) before build/verify tasks (T007-T008 run against the
  same `target/`, so sequential); T009 independent build env but reuses/creates
  `target/` — run after T007-T008, not concurrently.

### Parallel Opportunities

- Phase 1: T001/T002/T003 run in parallel (different files).
- US2: T006 writes a file (parallel-capable at start).
- Phase 4: T010/T011/T012 run in parallel; T013 anytime after code final.
- US1 and US2 can be worked in parallel by two devs after Phase 1.

---

## Parallel Example

```bash
# Phase 1 — all at once (different files):
Task: "Create .gitignore at repo root"
Task: "Create root pom.xml ... + empty src/main/java, src/test/java"
Task: "Add committed Maven wrapper 3.9.9, verify ./mvnw -v"

# US1 + US2 in parallel (after Setup):
Task: "Run dev boot verification (US1)"
Task: "Write boot smoke test src/test/java/dev/datafetcher/AppSmokeTest.java (US2)"

# Polish — all at once:
Task: "Dependency-lock check via ./mvnw dependency:list"
Task: "Quickstart walkthrough"
Task: "Measure SC-003 boot time"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (three tasks).
2. Phase 2: US1 dev-boot verification.
3. **STOP and VALIDATE**: US1 independently (boot + port + no endpoints + PG
   down). This is the deliverable MVP: a bootable skeleton on the locked stack.

### Incremental Delivery

1. Setup → foundation ready.
2. US1 → boot verified → MVP.
3. US2 → clean-checkout build + smoke test verified → second incremental
   deliverable.
4. Polish → contract lock + docs validated against reality.

## Notes

- [P] tasks = different files, no dependencies; avoid same-file concurrency.
- Commit after each logical group (wrapper, pom, each verification).
- Do NOT create `src/main/resources/application.properties` — 001-FR-04 owns
  runtime config (virtual threads, datasource defaults).
- Do NOT touch `docker-compose.yml` / `scripts/` — infra ownership is
  001-FR-01/02; `df-bronze-mount` carrier stays until the app service wires in.
- Boot must succeed with PostgreSQL 18 down; hard fail-fast validation is
  feature 005 (spec Clarifications). **Superseded for the dev profile** by
  001-FR-04 SC-007: `quarkus:dev` with PG down now fails startup loudly via
  the dev-profile reachability probe.