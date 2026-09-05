# Research: Quarkus Application Skeleton (001-FR-03)

Phase 0 output — decisions, rationale, alternatives. Both spec ambiguities
(PG-down boot posture, test-scope dependency allowance) were resolved in
`/speckit.clarify` (recorded in `spec.md` §Clarifications), so no
`NEEDS CLARIFICATION` items remain.

## D1 — Quarkus version pin

- **Decision**: Quarkus **3.33.3.1** (LTS), imported via
  `io.quarkus.platform:quarkus-bom` with property
  `quarkus.platform.version=3.33.3.1`.
- **Rationale**: The constitution mandates a *locked* stack; Quarkus
  publishes its production-recommended **latest LTS** as the stable choice
  (releases: 3.33 LTS line maintained for 12 months). A pinned BOM version in
  the pom is the reproducible, reviewable way to lock it. Java 21 is fully
  supported since 3.5 and is the current LTS baseline for the 3.33 line.
- **Alternatives considered**: 3.38.x (latest community, non-LTS — 4–6 week
  support window, wrong for a locked dev foundation); 3.20 LTS (EOL Mar 2026 —
  out of support).

## D2 — Application location: repo root

- **Decision**: The Quarkus app is a **single root module** — `pom.xml`,
  `mvnw`, `.mvn/wrapper/`, `src/` at the repository root.
- **Rationale**: Parent spec and quickstart document `./mvnw quarkus:dev` from
  the repo root; root is steady-state (later features 002-004 add sources to
  the same module). Docker compose, scripts, and specs already live at root —
  one checkout, no reactor ceremony, matches single-replica local-dev-first
  (constitution §V).
- **Alternatives considered**: Nested `app/` module (adds a relative-root hop
  to every documented command and breaks the "repo root" contract); Maven
  multi-module parent (unjustified abstraction for one deployable).

## D3 — Dependency set (exact mandated minimum)

- **Decision**: `pom.xml` depends on exactly: `quarkus-rest-jackson`
  (HTTP REST tool surface), `quarkus-hibernate-orm-panache` +
  `quarkus-jdbc-postgresql` (JPA Panache persistence for PostgreSQL 18), and
  test-scope `quarkus-junit` (one boot smoke test). SmallRye Config is NOT a
  separate artifact — it is baked into every Quarkus core runtime, satisfying
  FR-004 "SmallRye Config for configuration loading" implicitly.
- **Rationale**: FR-004 mandates the set and forbids WebSocket/MCP SDKs. The
  clarified allowance adds exactly one test-scope dependency
  (`quarkus-junit`) and no others; the runtime surface stays the mandated
  minimum. No Consul client, no health extension, no rest-assured.
- **Alternatives considered**: legacy `quarkus-resteasy-reactive-jackson`
  (rejected — renamed upstream to `quarkus-rest-jackson` in the 3.33 BOM; the
  old coordinate is no longer managed, so using it would force an unmanaged
  manual version pin and a relocation warning); explicit
  `smallrye-config` artifact (redundant — already on the compile classpath);
  `quarkus-rest-assured`/`quarkus-test-security` (unapproved test extras).

## D4 — Committed Maven wrapper (offline self-contained)

- **Decision**: Commit `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.jar`
  (wrapper **type=bin**), and `.mvn/wrapper/maven-wrapper.properties` pinned
  to Maven **3.9.9** + wrapper **3.3.2** (`distributionUrl` +
  `wrapperUrl` point at `repo.maven.apache.org/maven2`).
- **Rationale**: FR-001 requires a wrapper usable without pre-installed
  Maven; the spec edge case requires the clone to be self-contained **offline**
  (wrapper JAR committed, "Maven wrapper JAR missing" must not happen on a
  clean clone). One `./mvnw` command downloads and caches Maven on first run
  (SC-003's "warmed toolchain"), then reproduces the same Maven everywhere.
- **Alternatives considered**: `type=only-script` (no committed JAR — first
  run still downloads the JAR from `wrapperUrl`, breaking the offline/self-
  contained contract); `type=source` (adds a Java downloader in `.mvn/wrapper`
  to compile — heavier, same online-first gap); relying on a global `mvn`
  install (violates FR-001).

## D5 — Java 21 toolchain enforcement

- **Decision**: `maven.compiler.release=21` plus maven-enforcer-plugin
  `requireJavaVersion` with `[21,22)`.
- **Rationale**: Spec edge case: wrong toolchain MUST fail with a clear,
  attributable error. Enforcer's rule fails with an unambiguous message
  ("Detected JDK Version X is not in the allowed range") before compilation;
  `release=21` additionally pins the class-file target, eliminating cryptic
  class-version failures.
- **Alternatives considered**: Only `maven.compiler.release=21` (a Java 22+
  or 17 toolchain yields confusing bytecode/linking errors instead of a clear
  gate); Maven toolchains `~/.m2/toolchains.xml` (machine-local, not
  reproducible in CI without setup — rejected for reproducibility in SC-004).

## D6 — Boot with PostgreSQL 18 down (clarified posture)

- **Decision**: No datasource configuration is added in this slice (001-FR-04
  owns it), so `quarkus:dev` boots with **no DB dependency and no source
  registry**; when a datasource is later present but PG is down, Quarkus'
  lazy datasource leaves boot successful and connection failures surface at
  first use (clarification: Option B).
- **Rationale**: The clarified spec (US1, Edge Cases, Assumptions) is
  explicit: skeleton must boot successfully and must not *silently* mask DB
  problems — surfacing error at first use satisfies this without duplicating
  feature 005's fail-fast validation work.
- **Alternatives considered**: Fail-fast exit at boot (rejected in clarify —
  deferred to 005); adding datasource config now (violates FR-03 runtime
  config ownership boundary with 001-FR-04).

## D7 — Boot smoke test (approved test-scope allowance)

- **Decision**: One `@QuarkusTest` (`AppSmokeTest`) asserting that
  `quarkus.http.port` resolves to a numeric port. Boots the full Quarkus context.
- **Rationale**: Makes `./mvnw verify` non-trivial (clarified US2) — the test
  both starts the app context and exercises the config subsystem (SmallRye
  Config), proving the mandated dependency set resolves and loads. Uses only
  `quarkus-junit`; no rest-assured.
- **Alternatives considered**: No tests at all (`verify` vacuous — rejected
  by clarification); test-scope assertions landing in a future feature's suite
  (skeleton build integrity would go unproven until feature 002+).

## D8 — Interaction with sub-features 001-FR-01/02

- **Decision**: No compose changes. The `df-bronze-mount` carrier service
  (001-FR-02) continues to own `/data/bronze` until a future slice wires the
  app service; the app skeleton is pure Maven/Java with no container.
- **Rationale**: Infrastructure ownership is 001-FR-01/02; FR-03 proves the
  Maven build and empty boot only. Adding an app service to compose here would
  need an image build (not yet defined) and duplicate FR-02's carrier.
- **Alternatives considered**: Replace `busybox` carrier with the Quarkus app
  service now (premature — requires Dockerfile, image build, and endpoint
  contract that this skeleton does not define).