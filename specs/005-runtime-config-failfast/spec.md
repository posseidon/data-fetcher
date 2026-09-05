# Feature Specification: Runtime Config & Fail-Fast Startup

**Feature Branch**: `005-runtime-config-failfast`
**Created**: 2026-09-04
**Status**: Draft
**Input**: User description: "Runtime configuration via Consul 2.0 KV with fail-fast startup"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configure the service at runtime (Priority: P1)

An operator sets runtime knobs (bronze root path, download timeout, retry
policy, concurrency limit) in Consul 2.0 KV. The service reads them at startup and
all other features consume these values.

**Why this priority**: Configuration is the trust boundary for every runtime
behavior; knobs come from Consul 2.0, not from committed files.

**Independent Test**: Can be fully tested by setting the required keys to known
values and observing the service boot and run with those exact values (e.g.
bronze root honored by downloads, concurrency cap enforced).

**Acceptance Scenarios**:

1. **Given** all required Consul 2.0 keys present and valid, **When** the service
   boots, **Then** it starts and surfaces the configured values (bronze root,
   timeout, max attempts, max parallel jobs).
2. **Given** changed values in Consul 2.0 KV, **When** the service restarts, **Then**
   the new values take effect.

### User Story 2 - Fail fast on missing or invalid configuration (Priority: P1)

An operator starts the service with a required key missing or invalid (wrong
type, negative concurrency, zero timeout). The service refuses to start and
reports exactly which key is wrong.

**Why this priority**: Silent misconfiguration produces hard-to-trace data
failures; configuration errors must surface at boot, not mid-run.

**Independent Test**: Can be fully tested by booting once with each required
key missing and once with each key invalid; every attempt must fail fast with a
clear error naming the key.

**Acceptance Scenarios**:

1. **Given** a missing required key, **When** the service boots, **Then** it
   fails fast and names the missing key.
2. **Given** an invalid value (e.g. `maxParallelJobs=0`, `timeoutMs=-1`,
   `maxAttempts=5` instead of `1`), **When** the service boots, **Then** it
   fails fast with the key and expected range/constraint.
3. **Given** all keys correct, **When** the service boots, **Then** it starts
   normally (no false failures).

### Edge Cases

- What happens when Consul 2.0 is unreachable at boot? (Fail fast or use a
  documented local default? Decision must be explicit and tested.)
- What happens when a key has the right shape but an out-of-range value
  (concurrency > some sane bound)? (Constraint validation must reject or
  clamp with an explicit rule.)
- What happens when a key exists but holds an empty string? (Treated as
  missing/invalid and surfaced as such.)
- What happens when values change while the service is running? (v1 reads at
  startup only; mid-run behavior is out of scope unless documented.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST read runtime configuration from Consul 2.0 KV, not from
  committed config files or the DB.
- **FR-002**: Required keys MUST be validated at startup:
  `datafetcher/bronze/rootPath` (string), `datafetcher/download/timeoutMs`
  (positive int), `datafetcher/download/retry/maxAttempts` (MUST be `1`),
  `datafetcher/download/concurrency/maxParallelJobs` (positive int, explicit
  fixed value).
- **FR-003**: If any required key is missing or invalid, the service MUST fail
  fast at startup, naming the offending key and expected constraint.
- **FR-004**: The effective configuration MUST be consumed by the other
  features (bronze root by artifact-writer, timeout/retry/max-parallel by the
  job-runner).
- **FR-005**: Configuration is read at startup; v1 has no mid-run hot-reload
  requirement.
- **FR-006**: Values MUST be honored exactly as the boundary contract defines
  (e.g. `maxParallelJobs` is a hard cap, `maxAttempts=1` disables auto-retry).

### Key Entities *(include if feature involves data)*

- **runtime config**: Four required Consul 2.0 keys (bronze root path, download
  timeout, max retry attempts, max parallel jobs) validated as a whole at
  startup.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of boots with a missing or invalid required key fail fast
  and name the offending key; 0% of such boots start silently.
- **SC-002**: 100% of boots with valid keys start normally and the configured
  values are observable as effective behavior (bronze root used, concurrency
  cap enforced, retries disabled).
- **SC-003**: A config change takes effect after restart with no code change.

## Assumptions

- Consul 2.0 is available in local dev (Docker Compose) as per README; Consul 2.0 KV
  holds runtime knobs only — PostgreSQL 18 remains the authoritative registry.
- v1 enforces no live config reload; startup-read is sufficient.
- `maxAttempts` is deliberately locked to `1` to disable automatic retries;
  retries are the caller's job via `downloads.retry`.
- Startup fail-fast covers the four required keys; other optional keys are
  additive later and must not weaken the required-key gate.