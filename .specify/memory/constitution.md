<!--
  ============================================================
  SYNC IMPACT REPORT
  ------------------------------------------------------------
  Version change   : 1.0.0 -> 1.1.0
  Modified         : n/a (no principle text changed)
  Added sections   : Technology Standard; Security Requirements;
                     Performance Expectations; Coding Conventions;
                     Compliance
  Removed sections : n/a
  Templates updated:
    ✅ .specify/memory/constitution.md      (this file, overwritten)
    ✅ .specify/templates/plan-template.md  (verified - Constitution
       Check gate stays generic; no change required)
    ✅ .specify/templates/spec-template.md  (verified - new mandatory
       sections are in constitution, not spec; no change)
    ✅ .specify/templates/tasks-template.md (verified - principle-driven
       task types still align; no change)
    ⚠ .specify/templates/commands/*.md     (no commands/ dir exists; n/a)
    ⚠ README.md                            (verified - no conflicts;
       no change required)
  Follow-up TODOs  : none.
  Clarifications   : User accepted recommended baseline for all five
                     topics (stack locked, local-dev/no-authN, README
                     concurrency caps, Java/Quarkus idioms + review,
                     internal data-governance only).
  ============================================================
-->

# Data Fetcher Service Constitution

## Mission

Asynchronously fetch validate-and-checksum datasets from defined sources into
a Bronze staging area, so an external Ingestion service can pick them up.

## Core Principles

### I. Spec-Kit Documentation First
All feature work MUST be driven from a specification. Every spec, plan,
data model, contract, and task artifact MUST be generated under the
`spec/` directory at the repository root. Design decisions from the README
(in particular the HTTP-first transport, async download model, and Bronze
layout) are codified here and in specs before any implementation begins.
Rationale: a predictable, discoverable artifact trail makes each change
testable and auditable against the service contract.

### II. Asynchronous Job Execution (NON-NEGOTIABLE)
Download operations MUST be asynchronous. A download tool call MUST return
a `jobId` immediately; the download runs as a job. When concurrency is
saturated, jobs MUST remain in `QUEUED` state until permits free up. The
service MUST persist authoritative state (sources, datasets, jobs,
artifacts) in PostgreSQL 18. Rationale: fetches are long-running and
unpredictable; decoupling call from completion keeps the MCP surface
responsive and observable.

### III. Atomicity & Data Integrity
Every downloaded artifact MUST be written atomically and verifiable:
download to a temp `.part` file, compute MD5 while streaming, and
rename/move into the final Bronze location only when complete. The system
MUST always compute and store the MD5 for every artifact, write the
`<artifact>.md5` sidecar and `_metadata.json`, and support `artifacts.verify`
against the stored checksum. Rationale: partial or corrupt files in Bronze
would silently poison downstream ingestion.

### IV. Fail-Fast Runtime Configuration
Configuration MUST be treated as runtime knobs (Consul 2.0 KV), not as the
source registry. Required keys (bronze root path, timeouts, retry policy,
concurrency limits) MUST be validated at startup; if missing or invalid the
service MUST fail fast. Download retries are not applied automatically
(`retry/maxAttempts` fixed at `1`). Rationale: silent misconfiguration
produces hard-to-trace data failures, so configuration errors surface at
boot rather than mid-run.

### V. Resilience With Explicit Semantics
The service makes NO attempt to resume jobs. Download retries are the
caller's responsibility via `downloads.retry`. On application startup, any
job in `RUNNING` state MUST be marked `FAILED` (error `SERVICE_RESTARTED`).
The service MUST run as a single replica using Quarkus on JVM (Java 21)
with virtual threads, and MUST remain local-dev-first with no OpenShift
deployment concerns in Month 1. Rationale: resume logic is complex and
error-prone; explicit no-resume semantics keep failure behavior simple,
predictable, and testable.

## Bronze Storage & Data Integrity

All artifacts MUST be written under the configurable Bronze root (default
`/data/bronze`) to disk (filesystem path; Docker volume in local dev), in
the layout:

```
/data/bronze/
  category=<category>/
    source=<sourceName>/
      dataset=<datasetKey>/
        dt=YYYY-MM-DD/
          <artifact>
          <artifact>.md5
          _metadata.json
```

For every download: write to `.part` temp, compute MD5 while streaming,
rename into final path only on completion, then write `.md5` and
`_metadata.json` sidecars. Data remains in Bronze until an external
Ingestion service picks it up; this service performs no ETL, clipping, or
conversion. This layout and the atomicity rule MUST NOT be changed without
a constitution amendment because downstream systems depend on the contract.

## Runtime & Resilience

The service MUST expose its MCP tool surface over HTTP (REST endpoints
mapping to "tools"); WebSockets are deferred to iteration 2. Persistence is
PostgreSQL 18 (container), authoritative for sources, datasets, download jobs,
and artifacts. On startup, any `RUNNING` job MUST be transitioned to
`FAILED` with `SERVICE_RESTARTED`. No job resume is supported. Concurrency
MUST use virtual threads on the Quarkus JVM; running at single replica is
required for Month 1. Rationale: bounded, explicit runtime semantics are a
quality gate for every change.

## Technology Standard

The technology stack is LOCKED as specified in the README and MUST NOT be
changed without a constitution amendment:

- Runtime: Quarkus on the JVM, Java 21, using virtual threads for
  concurrency.
- Transport: MCP tool surface over HTTP (REST endpoints mapping to tools);
  WebSockets deferred to iteration 2.
- Persistence: PostgreSQL 18 (container) as the authoritative source registry
  and store for jobs/artifacts.
- Runtime configuration: Consul 2.0 KV for runtime knobs (bronze root path,
  timeouts, retry policy, concurrency limits); PostgreSQL 18 is the source
  registry, not Consul 2.0.
- Local dev: Docker Compose; Bronze stored on a Docker volume.
- Outputs: all Spec-Kit artifacts generated under `spec/` at repo root.
- Validation: MD5 computed while streaming; downloads written to `.part` and atomically renamed into final Bronze location.

Rationale: a locked, well-understood stack keeps the service maintainable
and removes recurring stack-selection debate from feature planning.

## Security Requirements

For v1 the service is local-development only, single replica, and NOT
exposed to untrusted networks, so no authentication is enforced. This
posture is explicit, not incidental:

- Secrets MUST NEVER be committed to the repository.
- Secrets MUST NOT be stored in Consul 2.0 KV or in committed config files;
  use environment variables / local secrets for anything sensitive.
- The MCP HTTP surface is trusted internal-only for v1; authentication and
  TLS are NOT in scope and are deferred with the WebSockets/iteration-2
  transport work.
- Validation MUST be enforced on all tool inputs (source/dataset
  definitions, download requests) at the trust boundary before any job or
  write is accepted.

Rationale: local-first does not mean careless; the rules above are the
minimum to avoid leaking credentials and to keep the input contract sound
before the surface expands.

## Performance Expectations

- Concurrency MUST be bounded by an explicit, fixed max parallel download
  limit (Consul 2.0 key `datafetcher/download/concurrency/maxParallelJobs`,
  e.g. `4`).
- When concurrency is saturated, additional jobs MUST remain `QUEUED` and
  MUST NOT fail purely due to load; permits are released as jobs complete.
- No per-download or per-endpoint latency SLA is defined for v1; download
  time is dominated by upstream feeds and is not a service-controlled
  target.
- Tool endpoints MUST respond promptly for non-download operations
  (status, list, verify, CRUD), returning synchronously rather than
  blocking on network fetches.

Rationale: the bounded-concurrency queue prevents resource exhaustion and
unbounded file churn; explicit non-SLA on download latency avoids false
guarantees on third-party feed speed.

## Coding Conventions

- Follow standard Java 21 and idiomatic Quarkus conventions (CDI beans,
  Panache/JPA for PostgreSQL 18, smallrye config, virtual threads).
- Code MUST be clear and self-documenting; comments are reserved for
  non-obvious rationale, not restated code.
- Every implementation plan MUST pass the "Constitution Check" gate in the
  plan template before and after design; deviations MUST be justified in
  the plan's Complexity Tracking table.
- Fail-fast configuration MUST be respected: startup validation of required
  Consul 2.0 keys and fail-fast boot are mandatory.
- Use the standard Java 21 + Quarkus idioms rather than introducing
  bespoke frameworks or patterns without justification.

Rationale: boring, idiomatic code is decodable at 3am; conventions reduce
review churn and keep the codebase approachable.

## Compliance

- Compliance is internal data-governance only; no external/regulatory
  regime is in scope for v1.
- MD5 MUST always be computed and stored for every artifact, with
  `<artifact>.md5` and `_metadata.json` sidecars, so every artifact carries
  integrity and provenance information for downstream auditing.
- Artifacts MUST remain in Bronze (filesystem staging) until an external
  Ingestion service picks them up; this service MUST NOT silently mutate,
  clip, convert, or enrich downstream data.
- The Bronze layout and atomicity rules are a downstream contract MUST be
  preserved unless amended.
- Source-license obligations for the KSH/OSM/GTFS feeds (attribution,
  redistribution terms) MUST be tracked where feeds are bundled or
  redistributed, in the appropriate plan/spec as feeds are onboarded.

Rationale: explicit staging + integrity sidecars deliver auditability and
clear provenance without imposing external regulatory scope on a
local-first v1.

## Governance

This constitution supersedes all other project practices. It is the
canonical source of non-negotiable rules for the Data Fetcher Service and
MUST be honored by every spec, plan, and task generated in this repository.

- **Amendment procedure**: any change to principles, Bronze layout,
  runtime semantics, or versioning requires documentation of the change,
  its rationale, and a migration/impact note before approval.
- **Versioning policy**: `CONSTITUTION_VERSION` follows semantic versioning
  (MAJOR for principle removal/redefinition, MINOR for added sections or
  materially expanded guidance, PATCH for wording/clarification).
- **Compliance review**: every implementation plan MUST pass the
  "Constitution Check" gate in the plan template before and after design;
  any deviation from a principle MUST be justified in the plan's Complexity
  Tracking table. Use `README.md` for runtime development guidance.

**Version**: 1.2.1 | **Ratified**: 2026-09-04 | **Last Amended**: 2026-09-04
