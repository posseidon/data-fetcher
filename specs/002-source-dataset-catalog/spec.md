# Feature Specification: Source & Dataset Catalog

**Feature Branch**: `002-source-dataset-catalog`
**Created**: 2026-09-04
**Status**: Draft
**Input**: User description: "Manage download source definitions and datasets"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register and maintain download sources (Priority: P1)

An operator registers an upstream data source (name, URL, category, license
contact), then keeps it in sync: list, view, update, delete, enable/disable.

**Why this priority**: Everything downstream (jobs, downloads, artifacts) hangs
off registered sources; without sources nothing can be fetched.

**Independent Test**: Can be fully tested by registering a source, updating it,
listing it, disabling it, and deleting it through the catalog endpoints; all
states persist across restarts and deliver a browsable source registry.

**Acceptance Scenarios**:

1. **Given** an empty source registry, **When** an operator registers a source
   with a name, URL, category, and license reference, **Then** the source
   appears in `sources.list` with a stable identifier.
2. **Given** an existing enabled source, **When** an operator disables it,
   **Then** the source is disabled and may not be referenced by new downloads.
3. **Given** any source, **When** an operator deletes it, **Then** it is removed
   from the registry and later `sources.get` reports it as absent.
4. **Given** a source definition, **When** an operator submits invalid input
   (missing name, malformed URL), **Then** the catalog rejects it with a
   descriptive error and no record is written.

### User Story 2 - Manage datasets per source (Priority: P1)

An operator declares datasets under a source (dataset key, download URL,
format), and lists/updates/enables/disables them. Initial targets: KSH
(INSPIRE grid + population + dwelling stock), OSM Geofabrik Hungary extract,
BKK Budapest GTFS.

**Why this priority**: Datasets are the unit of fetch; without them downloads
have no target.

**Independent Test**: Can be fully tested by declaring the five Month-1
datasets under their sources, then listing, updating, disabling, and deleting a
dataset; record state persists.

**Acceptance Scenarios**:

1. **Given** a registered source, **When** an operator creates a dataset under
   it with a key, URL, and format, **Then** it appears in `datasets.list` under
   that source.
2. **Given** a dataset, **When** an operator updates its URL, **Then**
   subsequent reads reflect the new URL.
3. **Given** a dataset belonging to a source, **When** an operator deletes the
   source, **Then** the dataset is no longer listed (child records do not
   orphan).
4. **Given** any catalog input, **When** it fails validation, **Then** no
   partial record is persisted.

### User Story 3 - Validate source definitions (Priority: P2)

An operator runs `sources.validate` to confirm a source definition is
well-formed and referenceable before wiring downloads to it.

**Why this priority**: Guard rail that prevents misconfigured sources from
silently failing at fetch time.

**Independent Test**: Can be fully tested by running validate against both a
valid and an invalid source definition and observing pass/fail outcomes.

**Acceptance Scenarios**:

1. **Given** a valid source definition, **When** `sources.validate` runs,
   **Then** the source is reported valid.
2. **Given** a source definition with a malformed URL or missing required
   fields, **When** `sources.validate` runs, **Then** specific validation
   errors are returned.

### Edge Cases

- What happens when a source or dataset key already exists on upsert? (Must
  update in place, preserving identity, and not duplicate.)
- What happens when a delete targets a source/dataset that is referenced by an
  in-flight or historical download job? (Catalog must either block with a
  clear error or record the reference consistently so history remains
  readable.)
- How does the system handle enable/disable toggles racing a concurrent
  download start? (Disable must win such that no new job starts against a
  disabled definition.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST persist an authoritative `data_source` registry
  (name, URL, category, license reference, enabled flag) in PostgreSQL 18.
- **FR-002**: System MUST persist per-source `dataset` definitions (dataset
  key, download URL, format, enabled flag) in PostgreSQL 18.
- **FR-003**: Users MUST be able to `sources.list`, `sources.get`,
  `sources.upsert`, `sources.delete`, `sources.enable`, and
  `sources.validate`.
- **FR-004**: Users MUST be able to `datasets.list`, `datasets.get`,
  `datasets.upsert`, `datasets.delete`, and `datasets.enable`.
- **FR-005**: Upsert MUST be idempotent by identity key (update in place, no
  duplicates).
- **FR-006**: System MUST enforce input validation on all catalog writes at the
  trust boundary before any record is written (missing/malformed name, URL,
  or key rejected with a descriptive error).
- **FR-007**: Disabled sources/datasets MUST NOT be referenceable by new
  download requests.
- **FR-008**: Deleting a source MUST make its datasets no longer listed without
  orphaning historical download/artifact records.
- **FR-009**: System MUST expose catalog operations over HTTP as tool endpoints
  (MCP-compatible), not as bespoke commands.
- **FR-010**: Month-1 dataset definitions (KSH INSPIRE grid, KSH population
  2022, KSH dwelling stock 2022, OSM Geofabrik Hungary, BKK GTFS) MUST be
  representable by this catalog.

### Key Entities *(include if feature involves data)*

- **data_source**: Authoritative source definition; mutable via catalog tools;
  has stable identity, URL, category, license reference, enabled state.
- **dataset**: Per-source dataset; belongs to exactly one source; has stable
  key, download URL, format, enabled state.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can register a source and its Month-1 datasets and
  see them listed consistently across restarts.
- **SC-002**: 100% of invalid catalog writes are rejected with a descriptive
  error before any record is persisted.
- **SC-003**: No duplicate source/dataset rows can be created by repeated
  upserts of the same identity.
- **SC-004**: All catalog endpoints respond synchronously (no blocking on
  network fetches) and can be exercised end-to-end over HTTP with no
  WebSocket transport.

## Assumptions

- PostgreSQL 18 (container) is running and is the authoritative catalog store;
  Consul 2.0 KV is NOT used for catalog records.
- Catalog identity is the state on which idempotency relies; no
  auto-generated numeric key is exposed as the identity for upsert.
- Source-license obligations (KSH/OSM/GTFS attribution, redistribution terms)
  are tracked in the catalog's license reference field as part of onboarding,
  not resolved in v1 automation.
- No authentication is enforced (local-dev v1); validation is the only
  trust-boundary control.
- Deleting a referenced source is an operator-initiated act; historical jobs
  keep readable references even after deletion.