# Implementation Plan: Quarkus Application Skeleton

**Branch**: `001-project-skeleton` (sub-feature 001-FR-03) | **Date**: 2026-09-05 | **Spec**: `spec.md`
**Input**: Feature specification from `specs/001-project-skeleton/001-FR-03-quarkus-skeleton/spec.md`

## Summary

Deliver the Maven (Java 21) Quarkus application skeleton at the repository
root: a committed Maven wrapper, a `pom.xml` declaring exactly the
constitution-mandated dependency set (RESTEasy Reactive + Jackson, JPA Panache
+ PostgreSQL 18 JDBC, SmallRye Config — plus the single approved test-scope
`quarkus-junit`), and one minimal boot smoke test. No application feature
code. The app boots in dev mode with no endpoints, reports its HTTP port
(default 8080), and boots successfully even when PostgreSQL 18 is down
(clarified posture: lazy datasource, DB errors surface at first use, hard
fail-fast is feature 005). Runtime configuration (virtual threads, datasource
defaults) is owned by 001-FR-04 and is not added here.

## Technical Context

**Language/Version**: Java 21 (enforced via `maven.compiler.release=21` + maven-enforcer `requireJavaVersion [21,22)`)  
**Primary Dependencies**: Quarkus LTS 3.33.3.1 (`io.quarkus.platform:quarkus-bom`) — RESTEasy Reactive + Jackson, JPA Panache, PostgreSQL 18 JDBC; SmallRye Config (baked into Quarkus core); test-scope `quarkus-junit`  
**Storage**: N/A — no authoritative data in this sub-feature (Bronze/PostgreSQL consumers arrive with features 002-004) — `application.properties` deferred to 001-FR-04  
**Testing**: `quarkus-junit` boot smoke test (asserts `quarkus.http.port` resolves to a numeric port); `./mvnw verify`  
**Target Platform**: local dev workstation (macOS/Linux); Java 21 toolchain, Maven provided via wrapper  
**Project Type**: web-service skeleton (REST-first Quarkus app, no endpoints yet)  
**Performance Goals**: N/A — no app traffic; boot target `~2 min` on warmed toolchain (SC-003)  
**Constraints**: dependency set exactly the constitution-mandated minimum + `quarkus-junit` (spec clarification); WebSockets/MCP SDKs MUST NOT be added; single-replica local-dev-first; runtime config deferred to 001-FR-04  
**Scale/Scope**: one root `pom.xml`; zero application feature code

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Note |
|-----------|---------|------|
| I. Spec-Kit Documentation First | PASS | plan/research/data-model/contracts/quickstart generated under `specs/001-project-skeleton/001-FR-03-quarkus-skeleton/` |
| II. Asynchronous Job Execution | n/a | No job code in this slice (features 002-003) |
| III. Atomicity & Data Integrity | n/a | No artifact writes here; MD5/atomic-write in feature 004 |
| IV. Fail-Fast Runtime Config | n/a* | No runtime config loads in this slice (owned by 001-FR-04/005). Clarified: skeleton boots with PG down, DB errors surface at first use; hard fail-fast enforced when config lands (005). *Planned sequencing, not a deviation.* |
| V. Resilience / single replica | PASS | Single-replica, local-dev-first bootable Quarkus JVM app |
| Bronze storage & integrity contract | n/a | No Bronze writes here; `/data/bronze` consumption arrives with features 002-004 |
| Runtime & Resilience (virtual threads) | n/a | Virtual-thread runtime config is 001-FR-04; deps in this pom are the enabling base |
| Technology Standard | PASS | Quarkus JVM + Java 21 + Maven; REST-reactive surface; WebSockets NOT added; deps exactly the mandated set |
| Security Requirements | PASS | No secrets committed; single-replica local-dev-first; no authN in scope per constitution |
| Performance Expectations | n/a | Concurrency knobs are feature 005 scope |
| Coding Conventions | PASS | Idiomatic Quarkus/CDI; one minimal smoke test; no bespoke patterns |
| Compliance | PASS | No feeds bundled; internal data-governance only |

Re-checked post-design (Phase 1): no new violations. No deviations requiring
the Complexity Tracking table.

## Project Structure

### Documentation (this feature)

```text
specs/001-project-skeleton/001-FR-03-quarkus-skeleton/
├── plan.md                  # This file
├── spec.md                  # Clarified feature spec (2 clarified decisions recorded)
├── research.md              # Phase 0 output
├── data-model.md            # Phase 1 output
├── quickstart.md            # Phase 1 output
├── contracts/
│   └── build-contract.md    # Phase 1 output: build/dev surface later features bind to
└── tasks.md                 # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
# Option 1: Single project at repository root (app == repo root)
pom.xml                          # NEW — Quarkus 3.33.3.1, mandated dep set + quarkus-junit
mvnw                             # NEW — committed wrapper script (Maven 3.9.9)
mvnw.cmd                         # NEW — Windows wrapper script
.mvn/wrapper/maven-wrapper.jar   # NEW — committed binary wrapper (offline first-run)
.mvn/wrapper/maven-wrapper.properties   # NEW — pinned distributionUrl/wrapperUrl
src/main/java/                   # empty (no feature code this slice)
src/test/java/dev/datafetcher/AppSmokeTest.java   # NEW — the one smoke test
.gitignore                       # NEW — target/, IDE dirs, local artifacts
```

The Quarkus app lives at the repository root (same level as
`docker-compose.yml`, `scripts/`, `specs/`) — parent spec's documented dev
boot is `./mvnw quarkus:dev` from the repo root, matching the README contract.
No `application.properties` is created in this slice: runtime config ownership
is 001-FR-04, and Quarkus boots with defaults (HTTP port 8080) in its absence.

**Structure Decision**: Single root project, no parent/submodule split. The
constitution mandates a single-replica local-dev-first runtime; one root
`pom.xml` matches the documented commands exactly and keeps docker-compose,
scripts, and app in one checkout. Later features add sources on top of this
root module without a reactor restructuring.

## Complexity Tracking

> Not needed — Constitution Check passed with no violations. Deliverable is one
> root `pom.xml` + committed Maven wrapper + one smoke test; no abstractions or
> extra projects introduced.