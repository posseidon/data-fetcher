# Feature Specification: Bronze Artifact Writer

**Feature Branch**: `004-bronze-artifact-writer`
**Created**: 2026-09-04
**Status**: Draft
**Input**: User description: "Atomic Bronze downloads with MD5 integrity sidecars"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Download an artifact into Bronze atomically (Priority: P1)

A job runner requests a fetch of a dataset URL. The writer downloads to a temp
`.part` file while streaming, computes MD5 as it goes, and only on success
renames the file into its final Bronze location. A download that fails partway
never leaves a visible partial artifact.

**Why this priority**: Partial or corrupt files in Bronze would silently poison
downstream ingestion; atomicity is a non-negotiable contract.

**Independent Test**: Can be fully tested by fetching a known file and
interrupting a second fetch mid-stream; the interrupted attempt must leave no
visible file in the final Bronze location.

**Acceptance Scenarios**:

1. **Given** a dataset URL, **When** the writer completes a fetch, **Then** the
   final file exists only at its final Bronze path (no leftover `.part`).
2. **Given** a fetch that fails mid-stream, **When** the writer aborts, **Then**
   the final path is untouched and any `.part` file is cleaned up.
3. **Given** any completed download, **When** the process crashes after the
   rename, **Then** the artifact file is fully present, never truncated.

### User Story 2 - Integrity sidecars (MD5 + metadata) (Priority: P1)

Every completed artifact has `<artifact>.md5` and `_metadata.json` written next
to it. MD5 is computed while streaming and stored; the metadata records
provenance (source, dataset, download timestamp).

**Why this priority**: Every artifact must carry integrity and provenance
information for downstream auditing.

**Independent Test**: Can be fully tested by downloading an artifact and
verifying that both sidecars exist and that the stored MD5 equals freshly
recomputed MD5 of the artifact.

**Acceptance Scenarios**:

1. **Given** a completed artifact at `<path>/<artifact>`, **When** the writer
   finishes, **Then** `<path>/<artifact>.md5` and `<path>/_metadata.json` also
   exist.
2. **Given** the stored checksum, **When** a verification recomputes MD5 of the
   artifact, **Then** the values match (unless the file was modified).

### User Story 3 - Inspect and verify artifacts (Priority: P2)

An operator lists artifacts (`artifacts.list`), reads an artifact's stored
metadata (`artifacts.readMetadata`), and verifies its integrity
(`artifacts.verify`) against the stored checksum.

**Why this priority**: Inspection is the observability surface for everything
written to Bronze.

**Independent Test**: Can be fully tested by listing a known artifact, reading
its metadata, and running verify against both an intact and a tampered file.

**Acceptance Scenarios**:

1. **Given** a stored artifact, **When** `artifacts.list` runs, **Then** the
   artifact is listed with its Bronze path and size.
2. **Given** a stored artifact, **When** `artifacts.readMetadata` runs, **Then**
   provenance metadata (source, dataset, timestamp) is returned.
3. **Given** an intact artifact, **When** `artifacts.verify` runs, **Then** the
   artifact is reported valid; a modified artifact is reported invalid.

### Edge Cases

- What happens when the same dataset downloads twice on the same day? (Final
  path is per `dt=YYYY-MM-DD`; duplicate writes must not interleave — atomic
  rename means last-complete-wins with no torn state.)
- What happens when the Bronze root is missing or not writable? (Writer must
  fail the job with a clear error, not create a partial tree silently.)
- What happens when a URL redirects or responds with a non-200 status? (Writer
  must fail without leaving artifacts.)
- What happens to `.part` files left by a hard crash? (Must be cleaned or
  ignored; never promoted to final.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST download to a temp `.part` file and compute MD5 while
  streaming.
- **FR-002**: System MUST rename/move the completed file into its final Bronze
  location ONLY when the download is complete and valid.
- **FR-003**: System MUST always compute and store MD5 for every artifact,
  writing `<artifact>.md5` and `_metadata.json` sidecars at the final path.
- **FR-004**: Artifacts MUST be written under the configurable Bronze root in
  the layout `category=<category>/source=<sourceName>/dataset=<datasetKey>/dt=YYYY-MM-DD/`.
- **FR-005**: `artifacts.list` MUST list artifacts with their Bronze path and
  size; `artifacts.readMetadata` MUST return stored provenance metadata;
  `artifacts.verify` MUST check the artifact against the stored checksum.
- **FR-006**: On download failure (HTTP error, interrupted stream, disk error),
  the final path MUST be untouched and the `.part` file MUST NOT be promoted.
- **FR-007**: Artifacts MUST remain in Bronze (filesystem staging) until an
  external Ingestion service picks them up; this service MUST NOT silently
  mutate, clip, convert, or enrich them.
- **FR-008**: Failed/interrupted writes MUST leave no `.part` residue (cleanup)
  and MUST NOT overwrite an existing valid artifact except via a complete
  atomic rename.
- **FR-009**: System MUST expose `artifacts.*` operations over HTTP as tool
  endpoints (MCP-compatible), not as bespoke commands.

### Key Entities *(include if feature involves data)*

- **artifact**: Download output; file on Bronze filesystem + record in
  PostgreSQL 18 (path, size, md5, metadata); references the producing dataset and
  job.
- **Bronze path**: `category=<category>/source=<sourceName>/dataset=<datasetKey>/dt=YYYY-MM-DD/<artifact>` — a downstream contract.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of completed artifacts carry a matching `.md5` sidecar;
  zero artifacts exist in Bronze without their sidecar pair.
- **SC-002**: Interrupted or failed downloads leave the final Bronze path
  untouched in 100% of cases (no partial or truncated file ever visible).
- **SC-003**: `artifacts.verify` correctly reports valid vs invalid for 100% of
  tested artifacts (including tampered files).
- **SC-004**: 100% of downloads land in the canonical
  `category/source/dataset/dt` layout under the configurable Bronze root.

## Assumptions

- Bronze root defaults to `/data/bronze`; this feature honors the runtime
  configurable root from feature 004.
- The filesystem is a Docker volume in local dev; no cloud object storage in
  v1.
- MD5 (not SHA-256) is mandated by the README contract; changing it requires a
  constitution amendment.
- Sidecar metadata covers provenance (source, dataset, download timestamp);
  downstream ETL reads these sidecars, so their existence is contractual.
- `dt=YYYY-MM-DD` is the download date; Budapest clipping/filtering happens in
  downstream ETL, never here.