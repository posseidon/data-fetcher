# Tasks: Bronze Volume Bind Mount (001-FR-02)

**Input**: Design documents from `specs/001-project-skeleton/001-FR-02-bronze-volume/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: No automated test tasks — the feature spec's "Independent Test" is a manual bidirectional probe (SC-002), and this is an infrastructure declaration with no application code. Verification is via `docker compose config` + the probe sequence in `quickstart.md`.

**Organization**: Tasks are grouped by user story. This sub-feature has exactly one user story, US1 (P1).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1)
- Include exact file paths in descriptions

## Path Conventions

- Repo-root `docker-compose.yml` (extends the FR-01 infra compose — single source of truth)
- New `scripts/precreate-bronze.sh`
- Docs live in `specs/001-project-skeleton/001-FR-02-bronze-volume/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the baseline compose file the mount extends is valid, so the bind-mount change starts from a known-good state.

- [x] T001 Verify FR-01 compose baseline: run `docker compose config` against the existing root `docker-compose.yml` and confirm it passes with services `df-postgres` and `df-consul` on network `df-net`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Host-side Bronze directory must exist before `compose up` — the clarified first-boot contract (spec FR-003a). This BLOCKS US1.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T002 Create `scripts/precreate-bronze.sh` — single script wrapping `mkdir -p "$HOME/data/bronze"`, executable via `chmod +x`; the host dir MUST exist before `docker compose up` (compose will not auto-create it)

**Checkpoint**: `scripts/precreate-bronze.sh` exists, executable, and `~/data/bronze` is created when run.

---

## Phase 3: User Story 1 - Bronze reachable on host and in container (Priority: P1) 🎯 MVP

**Goal**: A bind mount exposes the Bronze staging area at `~/data/bronze` (host) and `/data/bronze` (container) pointing to the same files bidirectionally, carried by a long-running carrier service so the mount contract is provable now.

**Independent Test** (from spec): run the composed mount, write a file inside the container's Bronze path and confirm it appears at `~/data/bronze` on the host (and vice versa) — the SC-002 bidirectional probe.

### Implementation for User Story 1

- [x] T003 [US1] Add `bronze-mount` service to root `docker-compose.yml`: `image: busybox`, `command: sleep infinity`, `container_name: df-bronze-mount`, network `df-net`, volumes bind mount `${HOME}/data/bronze:/data/bronze` (bind type, per research D1/D3)
- [x] T004 [US1] Validate mount declaration: run `docker compose config` and confirm the `bronze-mount` service renders with the bind mount and `df-net` network attached (no compose syntax errors)

**Checkpoint**: At this point, US1 is functional — `docker compose up -d` brings up `df-bronze-mount` and the bidirectional probe passes.

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Verify the full SC-002 contract end-to-end and confirm the edge-case behavior is documented and reproducible.

- [x] T005 Run the SC-002 bidirectional probe per `quickstart.md`: write `probe.txt` inside `df-bronze-mount` at `/data/bronze`, confirm it appears at `~/data/bronze/probe.txt` on the host; then write on the host and confirm it appears at `/data/bronze` in the container
- [x] T006 Confirm first-boot + edge-case flows against `contracts/bronze-mount-contract.md`: fresh host (no `~/data/bronze`) → `scripts/precreate-bronze.sh` then `compose up` succeeds; `~/data/bronze` as a file instead of a dir → `compose up` fails loudly with a clear message (no silent wrong-path bind)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — confirm baseline compose first
- **Foundational (Phase 2)**: Depends on Setup; BLOCKS US1 (host dir must exist before `compose up`)
- **User Story 1 (Phase 3)**: Depends on Foundational completion
- **Polish (Final Phase)**: Depends on US1 complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational. Only user story — no cross-story deps.

### Within the User Story

- Foundational pre-create before compose declaration
- Compose declaration before compose validation
- Validation before end-to-end probe

### Parallel Opportunities

- None within US1 — T003 and T004 touch the same file (`docker-compose.yml`) serially. T002 (script) is independent of T003/T004 but ordering irrelevant to correctness of the eventual probe.

---

## Parallel Example: N/A (single-file serial change)

`docker-compose.yml` is a single file; T003 → T004 must be sequential. No parallel task sets exist in this sub-feature.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (baseline compose config passes)
2. Complete Phase 2: Foundational (`scripts/precreate-bronze.sh`)
3. Complete Phase 3: User Story 1 (`bronze-mount` service + bind mount)
4. **STOP and VALIDATE**: run the bidirectional probe (T005)
5. Confirm edge cases (T006) — MVP is the full deliverable of this sub-feature

### Incremental Delivery

Single story — the whole slice IS the MVP. Nothing to stage beyond US1.

---

## Notes

- No [P] tasks here — the deliverable is one compose-file change + one script.
- [Story] label `[US1]` maps to spec User Story 1 (P1).
- No test-code tasks: verification is the manual/repeatable probe per spec + quickstart.
- Commit after each task or logical group; stop at checkpoints to validate.