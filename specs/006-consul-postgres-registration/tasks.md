# Tasks: PostgreSQL Consul Registration

**Input**: Design documents from `/specs/006-consul-postgres-registration/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md

**Tests**: None requested in spec — manual verification tasks per acceptance scenarios.

**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1)
- Include exact file paths in descriptions

## Path Conventions

- Infra-only feature: `docker-compose.yml` + `consul/config/` at repository root.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create `consul/config/` directory at repository root

---

## Phase 3: User Story 1 - PostgreSQL auto-registers in Consul (Priority: P1) 🎯 MVP

**Goal**: `docker compose up -d` brings up PostgreSQL and Consul; PostgreSQL appears
as `df-postgres` in the Consul catalog (static `df-net` IP, port 5432) with a TCP
health check and passing status.

**Independent Test**: `docker compose up -d` then
`curl -s 'http://127.0.0.1:8500/v1/health/service/df-postgres?passing'` returns the
service with `Address 172.20.0.10`, `Port 5432`, and health status `passing`.

### Implementation for User Story 1

- [x] T002 [P] [US1] Create `consul/config/df-postgres.json` — name `df-postgres`, id `df-postgres`, tags `[db, postgres]`, address `172.20.0.10`, port 5432; check `tcp 172.20.0.10:5432`, interval 10s, timeout 5s, `deregister_critical_service_after` 1m (no `start_period` — unsupported by Consul checks)
- [x] T003 [US1] Declare `df-net` subnet `172.20.0.0/24` in `docker-compose.yml` (`networks.df-net.ipam.config[0].subnet`)
- [x] T004 [US1] Assign static IP `172.20.0.10` to `df-postgres` in `docker-compose.yml` (`services.df-postgres.networks.df-net.ipv4_address`)
- [x] T005 [US1] Add `depends_on: df-consul: {condition: service_healthy}` to `df-postgres` in `docker-compose.yml`
- [x] T006 [US1] Mount repo config dir into Consul agent in `docker-compose.yml`: `services.df-consul.volumes: ./consul/config:/consul/config` (rw — image entrypoint chowns the dir; `:ro` aborts boot)
- [x] T007 [US1] Verify: `docker compose up -d`, run `curl 'http://127.0.0.1:8500/v1/health/service/df-postgres?passing'`, assert Address 172.20.0.10, Port 5432, status passing (acceptance scenarios 1-2)

**Checkpoint**: User Story 1 fully functional — `df-postgres` registered and healthy in Consul.

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Robustness and lifecycle verification

- [x] T008 [US1] Verify unhealthy drift: stop `df-postgres`, confirm Consul check flips critical, then service auto-deregisters within ~1m (verified: passing → critical → gone ~80s) (acceptance scenario 3)
- [x] T009 [US1] Verify idempotency and restart: `docker compose up -d` twice + restart `df-postgres` container, confirm single `df-postgres` catalog entry with passing status (note: after auto-deregistration, re-register via `docker exec df-consul consul reload`; restarting PG alone does not re-add the entry)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **User Story 1 (Phase 3)**: T002 is file-parallel with T003-T006; T003→T004→T005→T006 must run in order (same file `docker-compose.yml`)
- **Polish (Final Phase)**: Depends on User Story 1 complete

### User Story Dependencies

- **User Story 1 (P1)**: Sole story — no cross-story dependencies

### Within User Story 1

- Service definition file before mount wiring
- Wiring tasks in file-edit order (T003 → T006)
- Verification after all wiring complete

### Parallel Opportunities

- T002 [P]: `consul/config/df-postgres.json` — independent file
- T003-T006: same file `docker-compose.yml` — sequential, NOT parallel
- T008, T009: lifecycle checks, sequential after T007

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 3: User Story 1 (T002-T007) — this IS the MVP, sole story
3. **STOP and VALIDATE**: run independent test; confirm SC-001 (visible ≤30s) and SC-002 (accurate health)
4. Polish: T008-T009 lifecycle verification

### Incremental Delivery

1. T001 + T002-T006 → config on disk
2. T007 → registration verified (MVP complete)
3. T008-T009 → robustness proven

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to user story for traceability
- Same-file tasks (docker-compose.yml) sequential — one editor at a time
- Commit after each task or logical group
- Stop at checkpoint to validate story independently