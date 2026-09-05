# Implementation Plan: Local Infrastructure Compose

**Branch**: `001-project-skeleton` (sub-feature 001-FR-01) | **Date**: 2026-09-05 | **Spec**: `spec.md`
**Input**: Feature specification from `specs/001-project-skeleton/001-FR-01-infra-compose/spec.md`

## Summary

Bring up the local dependencies with one command: `docker compose up -d`.
The compose file is the single source of truth for local infrastructure —
PostgreSQL 18 and Consul 2.0 — pinned to exact images, isolated on the
explicit `df-net` user-defined bridge, exposed to the host on loopback only
(`127.0.0.1::`), with readiness healthchecks wired for deterministic
verification (SC-001) and a dynamic-port escape hatch for parallel CI runs.
No application code, no Consul client wiring (feature 005), no Bronze mount
(feature 001-FR-02) in this slice.

## Technical Context

**Language/Version**: N/A — no application code in this sub-feature (app stack: Quarkus/Java 21 handled in 001-FR-03)  
**Primary Dependencies**: Docker Compose v2 | images `postgres:18-alpine`, `hashicorp/consul:2.0.3`  
**Storage**: N/A (no app persistence managed here; the PostgreSQL container IS the store)  
**Testing**: `docker compose config` (validate) + `docker compose up -d` + health probes (`pg_isready`, `consul members`, HTTP/DNS checks)  
**Target Platform**: local Docker host (macOS/Linux dev); CI runner with Docker for E2E  
**Project Type**: infrastructure / docker-compose declaration (not an application project)  
**Performance Goals**: N/A — infra boot only; no app traffic in this slice  
**Constraints**: loopback-only host bindings; `df-net` bridge; `df-` container names; exact image tags (reproducibility); zero secrets committed; compose v2 syntax  
**Scale/Scope**: single replica, local-dev-first, 2 containers

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Note |
|-----------|---------|------|
| I. Spec-Kit Documentation First | PASS | plan/research/data-model/contracts/quickstart generated under the spec dir |
| II. Asynchronous Job Execution | n/a | No job code in this slice (features 002-003) |
| III. Atomicity & Data Integrity | n/a | No artifact writes here (Bronze in 001-FR-02 / 004) |
| IV. Fail-Fast Runtime Config | PASS | Consul is container-only; client wiring + fail-fast deferred to 005 by design — no Consul dependency introduced |
| V. Resilience / single replica | PASS | Local-dev-first, single-replica local runtime |
| Bronze layout / atomicity contract | n/a | Mount + layout live in 001-FR-02 / feature 004 |
| Runtime & Resilience (virtual threads) | n/a | Quarkus runtime in 001-FR-03/04 |
| Security Requirements | PASS | No secrets committed; all host bindings loopback-only (`127.0.0.1::`) per spec FR-002.2 |
| Performance Expectations | n/a | Concurrency knobs are Consul-seeded in feature 005 |
| Coding Conventions | n/a | No application code in this slice |
| Compliance | PASS | No feeds bundled; internal data-governance only |

Re-checked post-design: no new violations. No deviations requiring the
Complexity Tracking table.

## Project Structure

### Documentation (this feature)

```text
specs/001-project-skeleton/001-FR-01-infra-compose/
├── plan.md                      # This file
├── spec.md                      # Clarified feature spec
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output
├── quickstart.md                # Phase 1 output
├── contracts/
│   └── infrastructure-contract.md  # Phase 1 output: host-facing endpoint contract
└── tasks.md                     # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
# Option 1: Single project (infra-only deliverable for this sub-feature)
docker-compose.yml              # Postgres 18 + Consul 2.0, df-net, loopback ports
scripts/consul-seed.sh          # PRE-EXISTING (feature 005 scope) — untouched here
```

The sub-feature delivers exactly one source artifact: `docker-compose.yml` at
the repo root. No other source files. Verification happens via `docker
compose` commands and a documented health-probe sequence (see
`quickstart.md`).

**Structure Decision**: Root-level compose file (single source of truth, per
spec FR-002). No `docker-compose.ci.yml` override file — CI dynamic ports are
handled by env-var templating of host ports in the same file (see
`contracts/infrastructure-contract.md` §CI). Keeps one file, one source of
truth.

## Complexity Tracking

> Not needed — Constitution Check passed with no violations. Applied file is
> `docker-compose.yml`; no abstractions or extra projects introduced.