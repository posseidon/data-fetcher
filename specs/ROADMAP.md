# Data Fetcher Service — Implementation ROADMAP

**Purpose**: Track implementation features. Each feature is owned by one feature
folder under `specs/<NNN>-<name>/` containing its spec, plan, tasks, and
checklists. This ROADMAP only lists the active feature and completed work.

**Anti-scope-creep rule**: This ROADMAP does NOT enumerate future milestones.
Prompt context (constitution, spec-template) stays scoped to the single active
feature so agents never pre-build helpers or stubs for unstarted work. Product
context beyond the active feature lives in `README.md` (non-goals, deferred
iteration-2 items, optional data sources).

## Feature lifecycle

Each feature passes through: specify → clarify → plan → tasks → verify. The
branch and feature folder are created by `/speckit.specify`.

## Active / In-flight

| Branch | Feature | Folder | Phase | Status |
|--------|---------|--------|-------|--------|
| 001-project-skeleton | Quarkus/Java 21/Maven skeleton + docker-compose deps | `specs/001-project-skeleton/` | specify | Spec written |
| 002-source-dataset-catalog | Sources + datasets CRUD registry | `specs/002-source-dataset-catalog/` | specify | Spec written |
| 003-download-job-runner | Async download jobs + queueing | `specs/003-download-job-runner/` | specify | Spec written |
| 004-bronze-artifact-writer | Atomic Bronze downloads + MD5 sidecars | `specs/004-bronze-artifact-writer/` | specify | Spec written |
| 005-runtime-config-failfast | Consul 2.0 KV config + fail-fast boot | `specs/005-runtime-config-failfast/` | specify | Spec written |

## Done

*(none yet)*

## Pointers

- Product scope, non-goals, target data sets, architecture decisions: `README.md`
- Non-negotiable rules (stack, atomicity, resilience, governance): `.specify/memory/constitution.md`
- Feature lifecycle and tooling: `.specify/`
