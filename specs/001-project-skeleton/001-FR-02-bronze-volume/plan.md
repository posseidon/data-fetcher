# Implementation Plan: Bronze Volume Bind Mount

**Branch**: `001-project-skeleton` (sub-feature 001-FR-02) | **Date**: 2026-09-05 | **Spec**: `spec.md`
**Input**: Feature specification from `specs/001-project-skeleton/001-FR-02-bronze-volume/spec.md`

## Summary

Wire the Bronze staging area as a bind mount: host `~/data/bronze` maps to
container `/data/bronze` in the existing root `docker-compose.yml` (delivered
by 001-FR-01), bidirectionally. The host directory is pre-created with an
explicit `mkdir -p ~/data/bronze` before `docker compose up` (clarified in
spec §Clarifications). This sub-feature delivers no application code — only
the mount contract in the compose file, proven by a bidirectional file probe
(SC-002). Bronze layout and atomic-write semantics are exercised by feature
004, not here.

## Technical Context

**Language/Version**: N/A — no application code in this sub-feature (app stack: Quarkus/Java 21 handled in 001-FR-03)  
**Primary Dependencies**: Docker Compose v2 (existing `docker-compose.yml` from 001-FR-01) | host filesystem bind mount  
**Storage**: Host path `~/data/bronze` → container `/data/bronze` (bind mount, not a named volume)  
**Testing**: `docker compose config` (validate) + bidirectional file probe (write inside container → verify on host, and vice versa)  
**Target Platform**: local Docker host (macOS/Linux dev); CI runner with Docker for E2E  
**Project Type**: infrastructure / docker-compose declaration (not an application project)  
**Performance Goals**: N/A — mount contract only; no app traffic  
**Constraints**: pre-create host dir before `compose up`; no silent bind to a wrong path; fail loudly on file-not-dir; no app writes to Bronze in this slice  
**Scale/Scope**: single replica, local-dev-first, one bind mount

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Note |
|-----------|---------|------|
| I. Spec-Kit Documentation First | PASS | plan/research/data-model/contracts/quickstart generated under the spec dir |
| II. Asynchronous Job Execution | n/a | No job code in this slice (features 002-003) |
| III. Atomicity & Data Integrity | n/a | No artifact writes here (layout + atomic-write in feature 004) |
| IV. Fail-Fast Runtime Config | n/a | No app config; only the mount is declared |
| V. Resilience / single replica | PASS | Local-dev-first, single-replica local runtime |
| Bronze layout / atomicity contract | PASS | Mount exposes the Bronze root path; the layout the writer fills is unchanged (feature 004). Host path matches constitution default `/data/bronze` |
| Runtime & Resilience (virtual threads) | n/a | Quarkus runtime in 001-FR-03/04 |
| Security Requirements | PASS | No secrets committed; bind path is fixed host-local `~/data/bronze` |
| Performance Expectations | n/a | Concurrency knobs are feature 005 scope |
| Coding Conventions | n/a | No application code in this slice |
| Compliance | PASS | No feeds bundled; internal data-governance only; Bronze is a staging mount, no mutation |

Re-checked post-design: no new violations. No deviations requiring the
Complexity Tracking table.

## Project Structure

### Documentation (this feature)

```text
specs/001-project-skeleton/001-FR-02-bronze-volume/
├── plan.md                      # This file
├── spec.md                      # Clarified feature spec
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output
├── quickstart.md                # Phase 1 output
├── contracts/
│   └── bronze-mount-contract.md # Phase 1 output: bind-mount contract
└── tasks.md                     # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
# Option 1: Infra-only deliverable (this sub-feature)
docker-compose.yml              # EXISTING (001-FR-01) — THIS file gains the bronze bind mount
scripts/precreate-bronze.sh     # NEW — mkdir -p ~/data/bronze before compose up
```

The sub-feature delivers exactly one source change: the Bronze bind mount
added to the existing root `docker-compose.yml`, plus one small pre-create
script so the mount contract is reproducible. No other source files.

**Structure Decision**: Reuse the existing single root `docker-compose.yml`
(single source of truth, per FR-01 research D1/D8). The Bronze bind mount is
declared in that file. Because the Quarkus app service that will own Bronze
does not land until 001-FR-03/04, this sub-feature introduces a minimal
long-running `bronze-mount` service in the compose file whose sole job is to
carry the bind mount and be the probe target — proving SC-002 now, with the
exact `container:/data/bronze` path the app service will later reuse (see
`research.md` D3 for why this is not speculative abstraction). The app service
in FR-03/04 reuses the same declared mount path. A separate
`scripts/precreate-bronze.sh` keeps the clarified first-boot contract
explicit.

## Complexity Tracking

> Not needed — Constitution Check passed with no violations. The deliverable
> is one bind mount + one pre-create script; no abstractions or extra
> projects introduced.
