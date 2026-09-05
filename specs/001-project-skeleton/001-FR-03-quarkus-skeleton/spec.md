# Feature Specification: Quarkus Application Skeleton

**Feature Branch**: `001-project-skeleton` (sub-feature 001-FR-03)
**Created**: 2026-09-05
**Status**: Draft
**Input**: Decomposition of feature 001 (Project Skeleton), parent user stories "Build and run the app" (P1) and "Verify build integrity" (P2), and parent FR-001, FR-004, FR-008, FR-009.

## Clarifications

### Session 2026-09-05

- Q: What happens when the app boots with PostgreSQL 18 down? → A: Boot succeeds; DB connection failures surface in the log / at first use — no silent fallback. Hard fail-fast (exit non-zero on unreachable config) stays feature 005. **SUPERSEDED for the dev profile by 001-FR-04 SC-007** — the dev-profile probe now fails boot loudly when status is "pg down".
- Q: May the skeleton include test-scope dependencies? → A: Yes — `quarkus-junit` (test scope) plus one minimal boot smoke test; the runtime dependency set stays exactly the constitution-mandated minimum, making `./mvnw verify` non-trivial.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build and run the app (Priority: P1)

A developer runs the documented dev boot (`./mvnw quarkus:dev`). The Quarkus
app on Java 21 starts in dev mode, loads configuration, and reports its HTTP
port. No feature endpoints are required yet.

**Why this priority**: A bootable skeleton proves the locked stack works before
feature code lands; it is the foundation the four planned features build on.

**Independent Test**: Can be fully tested by running the dev command and
observing a successful startup log line with no errors.

**Acceptance Scenarios**:

1. **Given** a Java 21 toolchain and the Maven wrapper, **When** a developer
   runs `./mvnw quarkus:dev`, **Then** the app boots with no errors and reports
   its HTTP port.
2. **Given** the source tree contains only the skeleton (no feature code),
   **When** the app boots, **Then** no feature endpoints are served and
   startup succeeds.

### User Story 2 - Verify build integrity (Priority: P2)

A developer (or CI-style check) runs `./mvnw package` / `./mvnw verify` on a
clean checkout and confirms the dependency set from the constitution compiles
and the minimal boot smoke test passes.

**Why this priority**: Locked stack = the build must succeed reproducibly on a
clean machine with zero pre-installed artifacts beyond Maven/Java.

**Independent Test**: Can be fully tested by running the build command on a
clean checkout and observing a successful build + one passing smoke test.

**Acceptance Scenarios**:

1. **Given** a clean checkout, **When** `./mvnw package` runs, **Then** the
   build succeeds.
2. **Given** a modified pom that drops a mandated dependency, **When** the build
   runs, **Then** any feature relying on that dependency fails to compile.
3. **Given** a clean checkout, **When** `./mvnw verify` runs, **Then** a minimal
   boot smoke test executes and passes.

---

> The runtime configuration (virtual threads, datasource defaults) that this
> app loads at boot is owned by sub-feature 001-FR-04. This sub-feature proves
> the skeleton builds, resolves the mandated dependency set, and boots empty.

### Edge Cases

- What happens when the Maven wrapper JAR is missing from the repo? (Wrapper
  must be committed so `./mvnw` works offline; clone must be self-contained.)
- What happens when the app starts with PostgreSQL 18 down? (Boot must still
  succeed — Quarkus datasource is lazy; DB connection failures surface in the
  log / at first use, not silently masked. No silent fallback. Hard fail-fast
  (exit non-zero on unreachable config) is feature 005. **Superseded for the
  dev profile**: 001-FR-04 SC-007 probe fails boot loudly.)
- What happens when Java 21 is not the active toolchain? (Build must fail with
  a clear, attributable error, not a cryptic class-version failure.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The project MUST be a Maven (Java 21) Quarkus application with a
  committed Maven wrapper (`./mvnw`) usable without a pre-installed Maven.
- **FR-004**: The `pom.xml` MUST declare the dependency set mandated by the
  constitution: RESTEasy Reactive + Jackson for the HTTP tool surface, JPA
  Panache + PostgreSQL 18 JDBC for persistence, and SmallRye Config for
  configuration loading. WebSockets and MCP wire-protocol SDKs MUST NOT be
  added (deferred to iteration 2 / out of REST-first plan). A single
  test-scope dependency (`quarkus-junit`) for the boot smoke test is
  permitted and does not count toward the runtime tool surface.
- **FR-008**: The app MUST boot in dev mode (`./mvnw quarkus:dev`) with no
  application feature code written, producing a successful startup log.
- **FR-009**: The project MUST be single-replica / local-dev-first (no
  deployment or OpenShift concerns in Month 1).

### Key Entities *(include if feature involves data)*

- **Build artifact / runtime footprint**: the Maven project and compiled app.
  No authoritative data is stored in this sub-feature; the skeleton exists so
  later features (002-005) have a known, buildable base.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-003**: `./mvnw quarkus:dev` boots the app on a clean checkout in under
  ~2 minutes on a warmed toolchain with zero build errors.
- **SC-004**: `./mvnw package` succeeds on a clean checkout, proving the
  constitution-mandated dependency set resolves and compiles with no
  unapproved libraries.

## Assumptions

- The Maven wrapper (`.mvn/wrapper` + `mvnw`) is committed; Maven itself is
  not required to be pre-installed, only a Java 21 toolchain.
- PostgreSQL 18 need not be up for a skeleton boot (Quarkus lazy datasource);
  when down, DB connection failures surface in the log / at first use — no
  silent fallback and no boot-time crash (superseded for the DEV profile by
  001-FR-04 SC-007: boot fails loudly). Hard fail-fast enforcement of
  config validity remains feature 005.
- Runtime dependency set is exactly the constitution-mandated minimum (FR-004);
  no speculative libraries (e.g. Consul 2.0 client, WebSocket/MCP SDKs) are
  added. Test-scope `quarkus-junit` for the boot smoke test is the single
  approved exception.
- This sub-feature depends on sub-feature 001-FR-01 (infrastructure reachable)
  for its boot acceptance scenario but is independently testable for build
  integrity (US2).