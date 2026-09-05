# Feature Specification: Bronze Volume Bind Mount

**Feature Branch**: `001-project-skeleton` (sub-feature 001-FR-02)
**Created**: 2026-09-05
**Status**: Draft
**Input**: Decomposition of feature 001 (Project Skeleton), parent user story "Boot local infrastructure" (P1) and parent FR-003.

## Clarifications

### Session 2026-09-05

- Q: How should `~/data/bronze` be handled on first boot when it doesn't exist? → A: Explicit host-side pre-create script (`mkdir -p ~/data/bronze`) run before `compose up`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bronze reachable on host and in container (Priority: P1)

A Docker volume/bind mount exposes the service's Bronze staging area both
inside the container (at `/data/bronze`) and on the host (at `~/data/bronze`),
pointing to the same files bidirectionally.

**Why this priority**: The Bronze directory is the downstream contract
(category/source/dataset/dt layout, artifacts + sidecars). Until the mount is
wired, no artifact feature can be verified locally.

**Independent Test**: Can be fully tested by running the mount from this
sub-feature's compose file, writing a file inside the container's Bronze path,
and confirming it appears at `~/data/bronze` on the host (and vice versa).

**Acceptance Scenarios**:

1. **Given** the composed mount in place, **When** a developer writes `probe.txt`
   inside the container at the Bronze root, **Then** the same file appears at
   `~/data/bronze/probe.txt` on the host within seconds.
2. **Given** `~/data/bronze` does not exist on first boot, **When** a developer
   runs `docker compose up -d`, **Then** the directory is created (or the
   command explicitly fails with guidance) — never a silent bind to a wrong path.
3. **Given** a file written on the host under `~/data/bronze`, **When** the
   developer checks inside the container's Bronze root, **Then** the file is
   visible (bidirectional mount).

---

> Only one user story: the mount contract. Bronze layout and atomic-write
> semantics are exercised by feature 004, not here.

### Edge Cases

- What happens when `~/data/bronze` does not exist on first boot? (Resolved:
  explicit `mkdir -p ~/data/bronze` runs before `compose up`; compose fails
  if path absent.)
- What happens when the host path exists but is a file, not a directory?
  (Compose must fail with a clear message.)
- What happens when a file is written by the container as root? (Ownership/permission
  expectations must be documented so host tools can still read Bronze.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-003**: A Docker volume/bind mount MUST expose the service's Bronze path
  such that `~/data/bronze` on the host and the container's Bronze root point
  to the same files (default container path `/data/bronze`).
- **FR-003a**: The host directory `~/data/bronze` MUST be pre-created via an
  explicit `mkdir -p ~/data/bronze` step before `docker compose up`. Compose
  will not auto-create the path; relying on auto-creation risks a silent
  bind to the wrong location.

### Key Entities *(include if feature involves data)*

- **Bronze volume/bind mount**: maps host `~/data/bronze` to container
  `/data/bronze`. Not authoritative data; it is the reachable filesystem path
  the artifact writer (feature 004) will later fill with the constitutional
  `category/source/dataset/dt` layout.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-002**: A bind-mount check passes: a file written to the container's
  Bronze root appears at `~/data/bronze` on the host within seconds, and a
  file written on the host appears at `/data/bronze` in the container.

## Assumptions

- The host path is `~/data/bronze`; the container path is `/data/bronze`
  (matches the constitution's default Bronze root, also the default in
  sub-feature 001-FR-04's `application-dev.properties`).
- `~/data/bronze` MUST be pre-created on the host before `docker compose up`
  (via `mkdir -p ~/data/bronze`); compose will not auto-create it.
- No application code writes to Bronze in this sub-feature; only the mount
  contract is proven here.