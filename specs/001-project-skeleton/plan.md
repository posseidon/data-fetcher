# Implementation Plan: Runtime Configuration & Dev Boot

**Branch**: `001-project-skeleton` (sub-feature 001-FR-04) | **Date**: 2026-09-05 | **Spec**: `spec.md`
**Input**: Feature specification from `specs/001-project-skeleton/001-FR-04-runtime-config/spec.md`

## Summary

Ship the local-dev runtime defaults contract on the 001-FR-03 root module:
`src/main/resources/application-dev.properties` (dev profile only) carrying
virtual threads enabled, the PostgreSQL 18 datasource defaulting to
`localhost:5432` with the 001-FR-01 credential tuple, and Bronze root default
`/data/bronze` — plus one small CDI `StartupEvent` observer
(`StartupRuntimeProbe`) that logs the resolved runtime state and probes
PostgreSQL 18 reachability, failing boot loudly when unreachable (FR-007.1).
All knobs overridable via pinned env vars (`QUARKUS_DATASOURCE_JDBC_URL`,
`BRONZE_ROOT`, …) with zero file edits. No new dependencies. Supersedes the
001-FR-03 "boot succeeds with PG down" posture for the dev profile.

## Technical Context

**Language/Version**: Java 21 + Quarkus 3.33.3.1 LTS (from 001-FR-03)  
**Primary Dependencies**: none NEW — SmallRye Config (Quarkus core), `quarkus-jdbc-postgresql` (already on the FR-03 classpath for the probe), `quarkus-hibernate-orm-panache` (already present; stays inactive in profiles without a configured datasource)  
**Storage**: N/A — configuration artifact, not authoritative data  
**Testing**: existing `AppSmokeTest` (test profile, no datasource → stays green); FR-04 verification is boot-log observation (`./mvnw quarkus:dev`) + env-var override checks (SC-005/006/007)  
**Target Platform**: local dev workstation; Java 21 toolchain, `./mvnw` from repo root  
**Project Type**: web-service runtime-config slice on the existing root Quarkus module  
**Performance Goals**: N/A — no app traffic; observer adds negligible startup cost  
**Constraints**: dev-profile-only config; pinned env-override names; loopback-only datasource default; no new deps; no Consul client; ensure `quarkus:dev` boot terminates loudly when PG 18 unreachable (SC-007)  
**Scale/Scope**: one properties file + one observer class + sibling-contract reference fixes

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Note |
|-----------|---------|------|
| I. Spec-Kit Documentation First | PASS | plan/research/data-model/contracts/quickstart generated under `specs/001-project-skeleton/001-FR-04-runtime-config/` |
| II. Asynchronous Job Execution | n/a | No job code in this slice (features 002-003) |
| III. Atomicity & Data Integrity | n/a | No artifact writes here (feature 004) |
| IV. Fail-Fast Runtime Config | PASS | Consul KV key validation remains feature 005; the dev-profile DB reachability probe (FR-007.1) aligns with fail-at-boot spirit without pre-empting 005 |
| V. Resilience / single replica | PASS | Single-replica local-dev-first; defaults unchanged by this slice |
| Bronze layout / atomicity contract | PASS | Default `/data/bronze` matches 001-FR-02/`bronze-mount-contract`; no writes occur here |
| Runtime & Resilience (virtual threads) | PASS | This slice enables `quarkus.thread.virtual.enabled` per FR-006 |
| Technology Standard | PASS | Locked Quarkus/JVM/Java 21 stack; ZERO new dependencies added |
| Security Requirements | PASS | Dev-only placeholder creds mirrored per FR-010.1 — not real secrets; no new exposure |
| Performance Expectations | n/a | Concurrency knobs are feature 005 scope |
| Coding Conventions | PASS | One idiomatic CDI `StartupEvent` observer; no bespoke patterns |
| Compliance | PASS | No feeds bundled; internal data-governance only |

Re-checked post-design (Phase 1): no new violations. No deviations requiring
the Complexity Tracking table.

## Project Structure

### Documentation (this feature)

```text
specs/001-project-skeleton/001-FR-04-runtime-config/
├── plan.md                      # This file
├── spec.md                      # Clarified feature spec (5 clarified decisions)
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output
├── quickstart.md                # Phase 1 output
├── contracts/
│   └── runtime-config-contract.md  # Phase 1 output: effective-config contract features 001-004 read
└── tasks.md                     # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root, on the 001-FR-03 module)

```text
# Option 1: Single project at repository root (app == repo root)
src/main/resources/application-dev.properties                        # NEW — dev-profile defaults (FR-007/FR-007.2 contract)
src/main/java/dev/datafetcher/config/StartupRuntimeProbe.java        # NEW — StartupEvent observer: log + DB probe (FR-007.1/FR-007.2)
```

**No pom change**: every dependency the probe needs (`AgroalDataSource`,
SmallRye Config, Postgres JDBC) is already on the FR-03 classpath. The
observer reads effective config via `@ConfigProperty`/`Config`, logs it, and
probes reachability through an `Instance<AgroalDataSource>` (empty → skip) so
profiles without a datasource (testCI-packaged) never fail.

**Structure Decision**: The base config file is created EMPTY as a Quarkus
presence marker (VERIFIED: profile files are ignored without it). ALL values
live in the dev profile file (`application-dev.properties`), per clarification
Q2 and the 001-FR-01 credential-mirroring note — dev defaults must not leak
into `quarkus:test`, packaged runs, or a future prod profile (the empty base
leaks nothing).

## Coordination notes (cross-sub-feature)

- **001-FR-03 posture superseded**: FR-03 `build-contract.md` and
  `data-model.md` documented "boot succeeds with PostgreSQL 18 down". Under the
  dev profile, 001-FR-04's probe flips this: PG-down `quarkus:dev` boot now
  TERMINATES (SC-007). Non-dev profiles keep the lazy posture. The referenced
  contract lines are updated as part of this slice.
- **Artifact rename**: sibling contracts referenced `application.properties`;
  the clarified artifact is `application-dev.properties`. Forward-reference
  lines in `build-contract.md` and `bronze-mount-contract.md` corrected here.

## Complexity Tracking

> Not needed — Constitution Check passed with no violations. Deliverable is one
> `application-dev.properties` + one observer class + contract reference fixes;
> no abstractions or extra projects introduced.