# Tasks: Local Infrastructure Compose (001-FR-01)

**Input**: Design documents from `specs/001-project-skeleton/001-FR-01-infra-compose/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/infrastructure-contract.md

**Organization**: Tasks are grouped by single deliverable (one user story). This feature produces exactly ONE source file (`docker-compose.yml` at repo root) — every edit task targets that same file, so story tasks run strictly sequentially; there is no meaningful file-level parallelism.

**Granularity note**: Tasks are kept deliberately small (single service attribute, single probe verification) for small-model execution. Each task names the exact file, the exact keys, and the expected verification signal.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: `[US1]` = the single user story "Boot local infrastructure"

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm host tooling so compose work has a foundation. No repo files created yet.

- [x] T001 Verify Docker Compose v2 is available: run `docker compose version` (expect output containing `v2`; if v1 only, stop and report)
- [x] T002 Confirm no `docker-compose.yml` exists at repo root (`/Users/Thai_Binh_Nguyen/sources/upskilling/phase-1/data-fetcher/docker-compose.yml`) — prevents accidental clobber of an existing file

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Single-file skeleton so each subsequent edit lands on a valid YAML document.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Create `docker-compose.yml` at repo root (`/Users/Thai_Binh_Nguyen/sources/upskilling/phase-1/data-fetcher/docker-compose.yml`) with: `name: df-infra`, empty top-level `services:` map, and `networks:` declaring `df-net:` (default bridge). Keep no other keys yet.

**Checkpoint**: Foundation ready — `docker-compose.yml` parses (YAML valid); user story editing may begin.

---

## Phase 3: User Story 1 - Boot local infrastructure (Priority: P1) 🎯 MVP

**Goal**: `docker compose up -d` starts PostgreSQL 18 and Consul 2.0; both report healthy; single source of truth for local infra.

**Independent Test**: Fresh checkout → `docker compose up -d` → `docker compose ps` shows `df-postgres` and `df-consul` both "(healthy)" with zero manual steps.

### Implementation for US1

> All tasks edit the SAME file `docker-compose.yml`. Do them in ID order; do not reformat unrelated blocks.

- [x] T004 [US1] Add `df-postgres` service to `docker-compose.yml`: `image: postgres:18-alpine`, `container_name: df-postgres`, `networks: [df-net]`
- [x] T005 [US1] Add `env:` to `df-postgres` in `docker-compose.yml`: `POSTGRES_DB=df_agent_db`, `POSTGRES_USER=df_dev_user`, `POSTGRES_PASSWORD=df_dev_secret`
- [x] T006 [US1] Add loopback port binding to `df-postgres` in `docker-compose.yml`: `- "127.0.0.1:${POSTGRES_HOST_PORT:-5432}:5432"` (port key `5432`, ipv4 first)
- [x] T007 [US1] Add `healthcheck:` to `df-postgres` in `docker-compose.yml`: `test: ["CMD-SHELL", "pg_isready -U df_dev_user -d df_agent_db"]`, `interval: 5s`, `timeout: 5s`, `retries: 10`, `start_period: 10s`
- [x] T008 [US1] Add `volumes:` to `df-postgres` in `docker-compose.yml`: `- df-postgres-data:/var/lib/postgresql/data`; then declare top-level `volumes:` with `df-postgres-data:`
- [x] T009 [US1] Add `df-consul` service to `docker-compose.yml`: `image: hashicorp/consul:2.0.3`, `container_name: df-consul`, `command: agent -server -bootstrap-expect=1 -bind=0.0.0.0 -client=0.0.0.0 -data-dir=/consul/data -ui`, `networks: [df-net]`
- [x] T010 [US1] Add loopback port bindings to `df-consul` in `docker-compose.yml`: `- "127.0.0.1:${CONSUL_HTTP_HOST_PORT:-8500}:8500"` and `- "127.0.0.1:${CONSUL_DNS_HOST_PORT:-8600}:8600/udp"` (only these; do NOT map 8300/8301/8302). **Deviation**: added sibling TCP mapping `- "127.0.0.1:${CONSUL_DNS_HOST_PORT:-8600}:8600/tcp"` — required because Colima (local Docker engine) cannot forward UDP published ports from host loopback; see T016.
- [x] T011 [US1] Add `healthcheck:` to `df-consul` in `docker-compose.yml`: `test: ["CMD", "consul", "members"]`, `interval: 5s`, `timeout: 5s`, `retries: 10`, `start_period: 10s`

### Verification for US1

> Run in order; each task's PASS signal is explicit.

- [x] T012 [US1] Run `docker compose config` in `/Users/Thai_Binh_Nguyen/sources/upskilling/phase-1/data-fetcher` — must exit 0 and print the resolved df-postgres + df-consul services with `df-net`
- [x] T013 [US1] Run `docker compose up -d` from repo root — both containers start; `docker compose ps` shows `df-postgres` and `df-consul` as `Up (healthy)`
- [x] T014 [US1] Probe PostgreSQL: `docker compose exec df-postgres pg_isready -U df_dev_user -d df_agent_db` → `accepting connections`
- [x] T015 [US1] Probe Consul HTTP: `curl -fsS http://localhost:8500/v1/status/leader` → non-empty `<ip>:<port>` string, exit 0
- [x] T016 [US1] Probe Consul DNS: `dig +short @127.0.0.1 -p 8600 localhost` → `127.0.0.1`
  - **Deviation (approved)**: stock Consul REFUSES bare single-label `localhost` (only serves `*.consul`), and Colima (local Docker engine) cannot forward UDP published ports from host loopback. Verified instead via TCP: `dig +tcp +short @127.0.0.1 -p 8600 consul.service.consul` → `172.18.0.2`. Requires TCP 8600 mapping added in T010.
- [x] T017 [US1] Idempotency: run `docker compose up -d` again — exits 0, no container recreation (`docker compose ps` unchanged, both healthy)
- [x] T018 [US1] Port-conflict loud failure (spec scenario 3): occupy host 5432 (e.g. `nc -l 5432` or stop using it after) then `docker compose up -d` → fails with clear port-conflict error; free the port and confirm `docker compose up -d` recovers
  - **Deviation (colima)**: Colima publishes via ssh forwarder on `127.0.0.1:5432` and tolerates a host process holding the same port (macOS `SO_REUSEADDR`) — compose does NOT fail loudly on host-process occupancy. Loud failure verified instead at daemon level: second publisher `docker run -p 127.0.0.1:5432:5432` → `Bind for 127.0.0.1:5432 failed: port is already allocated` (exit 125). Recovery after freeing confirmed (both healthy).
- [x] T019 [US1] CI dynamic ports (FR-002.3): `POSTGRES_HOST_PORT=0 CONSUL_HTTP_HOST_PORT=0 CONSUL_DNS_HOST_PORT=0 docker compose up -d` → `docker compose port df-postgres 5432` and `docker compose port df-consul 8500` return assigned random ports
- [ ] T020 [US1] Teardown: `docker compose down --timeout 30` (containers gone, volume kept); then verify reseed path untouched: `docker compose down --volumes` deletes `df-postgres-data`

**Checkpoint**: US1 fully functional — clean checkout boots healthy infra with one command (SC-001).

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: Docs stay synchronized with the shipped compose file.

- [x] T021 [P] Walk `specs/001-project-skeleton/001-FR-01-infra-compose/quickstart.md` commands end-to-end against the implemented file; fix any drift in quickstart.md
- [x] T022 Cross-check `contracts/infrastructure-contract.md`, `data-model.md`, and repo `AGENTS.md` against the final `docker-compose.yml` (ports, healthchecks, credential tuple, image tags); update any drifted values

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — starts immediately (T001 → T002)
- **Foundational (Phase 2)**: Depends on Setup — T003 only; blocks US1
- **User Story 1 (Phase 3)**: Depends on T003. Internal order is strict:
  - Edits T004–T011 are sequential (all touch `docker-compose.yml`)
  - Verification T012–T020 sequential after edits; T013 depends on T012; T014–T019 depend on T013; T020 last
- **Polish (Phase 4)**: Depends on all US1 work complete

### User Story Dependencies

- **User Story 1 (P1)**: No cross-story dependencies — the only story; whole feature is the MVP.

### Within the User Story

- Implementation edits before verification (T004–T011 then T012+)
- Core services (postgres) before secondary (consul) — T004–T008 then T009–T011
- Every verification task requires the prior task's PASS signal

### Parallel Opportunities

- **Low by nature**: single deliverable file. Only file-level parallelism is Polish (T021/T022, different doc files).
- `[P]` markers appear only where genuinely different files allow it.

---

## Parallel Example: Polish Phase

```bash
Task: "Walk specs/001-project-skeleton/001-FR-01-infra-compose/quickstart.md commands end-to-end; fix drift in quickstart.md"
Task: "Cross-check contracts/infrastructure-contract.md, data-model.md, AGENTS.md against final docker-compose.yml; update drift"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T002)
2. Complete Phase 2: Foundational (T003 — CRITICAL)
3. Complete Phase 3: User Story 1 edits (T004–T011)
4. Complete US1 verification (T012–T020) — **STOP and VALIDATE** at T013/checkpoint
5. Polish (T021–T022) only after SC-001 proven

The whole feature IS the MVP (one story, one file) — no partial-story delivery exists.

### Incremental Delivery Points

- After T003: valid YAML skeleton exists (`docker compose config` parses)
- After T011: both services declared; config validates
- After T013: SC-001 achieved (both containers healthy)
- After T019: CI parallel-run capability proven

### Commit Strategy

- Commit after T003 (foundation), after T011 (declaration complete), after T013 (healthy), and after each Polish task — small commits suit the small-model executor.

---

## Notes

- [P] tasks = different files, no dependencies (rare here — single-file feature)
- [US1] label maps the task to the single user story for traceability
- Same-file tasks are ordered; do not parallelize file edits
- Stop at the T013 checkpoint to validate the story independently
- Avoid: vague tasks, same-file concurrent edits, cross-feature scope (Consul client/seed wiring = feature 005; Bronze mount = 001-FR-02; app config mirror = 001-FR-04)