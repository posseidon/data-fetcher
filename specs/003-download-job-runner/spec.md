# Feature Specification: Async Download Job Runner

**Feature Branch**: `003-download-job-runner`
**Created**: 2026-09-04
**Status**: Draft
**Input**: User description: "Asynchronous download job orchestration with queueing"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start a download and track it (Priority: P1)

An operator starts a download for a registered, enabled dataset via
`downloads.start`. The tool returns a `jobId` immediately; the job runs in the
background. The operator polls `downloads.status` and later sees the job in a
terminal state.

**Why this priority**: Fetches are long-running and unpredictable; decoupling
call from completion is the core contract of the service.

**Independent Test**: Can be fully tested by starting a download, observing an
immediate `jobId` return, and then watching status transitions (QUEUED →
RUNNING → SUCCESS/FAILED) without the call site ever blocking on the network
fetch.

**Acceptance Scenarios**:

1. **Given** an enabled source/dataset pair, **When** an operator calls
   `downloads.start`, **Then** the call returns a `jobId` synchronously and the
   job is recorded in `download_job`.
2. **Given** a running job, **When** the operator calls `downloads.status`,
   **Then** it returns the current state and related metadata.
3. **Given** any completed or failed job, **When** the operator lists
   `downloads.list`, **Then** the job is present with its terminal state.

### User Story 2 - Queueing under concurrency pressure (Priority: P1)

An operator starts more downloads than the configured max parallel limit (e.g.
`maxParallelJobs=4`). Excess jobs remain `QUEUED`, then start as running jobs
finish and permits free up. No job fails purely because of load.

**Why this priority**: Bounded-concurrency queue prevents resource exhaustion
and unbounded file churn — a non-negotiable service rule.

**Independent Test**: Can be fully tested by starting N jobs with a max
parallel limit of 1 and observing that only one job is ever `RUNNING` while the
rest stay `QUEUED` and eventually complete.

**Acceptance Scenarios**:

1. **Given** `maxParallelJobs=1` and two download requests, **When** both are
   started, **Then** the second job is `QUEUED` until the first completes.
2. **Given** a saturated queue, **When** any running job finishes, **Then** a
   `QUEUED` job transitions to `RUNNING` until none remain.

### User Story 3 - Cancel and retry jobs (Priority: P2)

An operator cancels a queued or running download via `downloads.cancel`, and
subsequently retries a failed download via `downloads.retry` (automatic retry
is never applied).

**Why this priority**: Retries are explicitly the caller's responsibility; the
service never auto-resumes or auto-retries.

**Independent Test**: Can be fully tested by cancelling a queued job (cancelled,
no artifact written) and retrying a failed job (a fresh job is created and
runs).

**Acceptance Scenarios**:

1. **Given** a queued job, **When** `downloads.cancel` runs, **Then** job state
   becomes `CANCELLED` and it is never executed.
2. **Given** a killed/cancelled running job, **When** `downloads.retry` runs,
   **Then** a new job is created referencing the same dataset and executes.
3. **Given** any failing download, **When** the operator does nothing, **Then**
   the job stays `FAILED` (no automatic retry, `retry/maxAttempts` is `1`).

### User Story 4 - Survive restarts with explicit semantics (Priority: P2)

The service restarts while a job is `RUNNING`. On startup that job is marked
`FAILED` with error `SERVICE_RESTARTED`. No resume is attempted.

**Why this priority**: No-resume semantics keep failure behavior simple,
predictable, and testable.

**Independent Test**: Can be fully tested by starting a job, restarting the
service, and observing the stale RUNNING job transition to `FAILED`.

**Acceptance Scenarios**:

1. **Given** a job left `RUNNING`, **When** the service starts up, **Then** the
   job is marked `FAILED` with error `SERVICE_RESTARTED`.
2. **Given** an already-finished job, **When** the service restarts, **Then**
   its terminal state is unchanged.

### Edge Cases

- What happens when `downloads.start` targets a disabled source/dataset or a
  definition missing from the catalog? (Reject: no job is created.)
- What happens when the queue is saturated AND the operator cancels a running
  job? (A queued job may advance once permits free; cancel of a queued job must
  not corrupt the queue.)
- What happens when retry runs against a job that is `RUNNING`? (Reject or
  queue as a distinct new job; never double-execute the same job record.)
- What happens to status/list calls while the service is mid-restart? (DB is
  authoritative; states remain queryable.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST persist an authoritative `download_job` record for
  every download (dataset reference, state, timestamps, error detail).
- **FR-002**: `downloads.start` MUST return a `jobId` synchronously and MUST
  NOT block on the network fetch.
- **FR-003**: Jobs MUST transition QUEUED → RUNNING → SUCCESS|FAILED|CANCELLED;
  when concurrency is saturated, new jobs MUST remain `QUEUED` until permits
  free up.
- **FR-004**: Concurrency MUST be bounded by an explicit, fixed max parallel
  limit; jobs MUST NOT fail purely due to load.
- **FR-005**: `downloads.status`, `downloads.list`, and `downloads.cancel`
  MUST be available for every job; cancel MUST prevent execution of a queued
  job and stop a running job.
- **FR-006**: `downloads.retry` MUST create a fresh job for the same dataset;
  automatic retry MUST NOT be applied (`retry/maxAttempts` fixed at `1`).
- **FR-007**: On application startup, any job in `RUNNING` state MUST be marked
  `FAILED` with error `SERVICE_RESTARTED`; no job resume is supported.
- **FR-008**: `downloads.start` MUST reject targets that are disabled or not
  present in the catalog, before any job record is written.
- **FR-009**: Job execution MUST use virtual threads on the Quarkus JVM (single
  replica).
- **FR-010**: System MUST expose the job-running operations over HTTP as tool
  endpoints (MCP-compatible), not as bespoke commands.

### Key Entities *(include if feature involves data)*

- **download_job**: Async orchestration record; references a dataset; carries
  state (QUEUED/RUNNING/SUCCESS/FAILED/CANCELLED), timestamps, error detail.
- **artifact** (link): produced by the runner; written into Bronze by the
  artifact-writer feature — the runner owns scheduling, not file writes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every `downloads.start` call returns a `jobId` with no network
  blocking, measurable as "call returns immediately regardless of fetch
  duration".
- **SC-002**: With a max parallel limit of N, no more than N jobs are ever
  `RUNNING` concurrently; queued jobs complete after earlier jobs finish.
- **SC-003**: After a service restart, zero jobs remain in `RUNNING` state; all
  such jobs read as `FAILED` with `SERVICE_RESTARTED`.
- **SC-004**: Zero jobs ever transition to a terminal state without a matching
  `download_job` record in PostgreSQL 18.

## Assumptions

- PostgreSQL 18 is the authoritative job store; the runner is stateless across
  restarts apart from the DB.
- `download_job` references datasets by their catalog identity from feature
  001; jobs may not be started against definitions unknown to the catalog.
- Bounded concurrency is read from runtime config (feature 004); the runner
  consumes the effective max-parallel value.
- Cancel is best-effort for a download that is mid-write; the artifact-writer's
  atomic rename (feature 003) guarantees no partial file becomes visible,
  regardless of cancel timing.
- The Hungary/OSM/BKK feeds are the target data; downloading details live in
  the artifact-writer feature, this feature only orchestrates.