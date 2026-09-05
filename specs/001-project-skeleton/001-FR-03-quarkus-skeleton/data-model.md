# Data Model: Quarkus Application Skeleton (001-FR-03)

Phase 1 output. This feature produces a buildable application skeleton, not
authoritative data — the "entities" are the build artifact model and the
dependency set later features compile against.

## Entities

### Root Maven project (build artifact)

| Attribute | Value | Source |
|-----------|-------|--------|
| location | repository root (`pom.xml`) | research D2 |
| artifactId | `data-fetcher` | FR-001 naming |
| packaging | `jar` | Quarkus app |
| Java release | 21 (`maven.compiler.release=21`) | FR-001 / research D5 |
| Java gate | enforcer `requireJavaVersion [21,22)` | research D5 |
| Quarkus | 3.33.3.1 LTS via `io.quarkus.platform:quarkus-bom` | research D1 |
| build tool | committed Maven wrapper 3.9.9 (`./mvnw`) | research D4 |

### Mandated dependency set (runtime tool surface)

| Dependency | Role | Source |
|------------|------|--------|
| `quarkus-rest-jackson` | REST HTTP tool surface (RESTEasy Reactive + Jackson) | FR-004 |
| `quarkus-hibernate-orm-panache` | JPA Panache persistence | FR-004 |
| `quarkus-jdbc-postgresql` | PostgreSQL 18 JDBC driver | FR-004 |
| SmallRye Config | configuration loading (baked into Quarkus core — no explicit artifact) | FR-004 / research D3 |
| `quarkus-junit` (test scope) | boot smoke test — the single approved test dep | clarify 2026-09-05 Q2 |

WebSockets and MCP wire-protocol SDKs MUST NOT be present (FR-004). No
Consul client, no health extension, no rest-assured.

### Boot smoke test (`AppSmokeTest`)

| Attribute | Value | Source |
|-----------|-------|--------|
| type | `@QuarkusTest` | research D7 |
| assertion | `quarkus.http.port` resolves to a numeric port | research D7 |
| effect | boots full Quarkus context + exercises SmallRye Config | US2 / SC-004 |
| location | `src/test/java/dev/datafetcher/AppSmokeTest.java` | plan structure |

## Lifecycle / state transitions

| State | Trigger | Signal |
|-------|---------|--------|
| toolchain valid | `./mvnw` with JDK 21 | enforcer gate passes |
| toolchain invalid | `./mvnw` with JDK ≠21.x | enforcer fails with clear message (spec edge case) |
| wrapper present | clone (jar committed) | `./mvnw -v` works offline-first |
| wrapper missing | partial checkout / gitignore mistake | scripts attempt `wrapperUrl` download (should not happen on clean clone) |
| app booted (dev) | `./mvnw quarkus:dev` | startup log reports HTTP port 8080, no endpoints |
| PG 18 down | app started without reachable DB | boot still succeeds; DB errors surface at first use (clarify Q1) |
| build verified | `./mvnw verify` | smoke test executes and passes |

## Constraints & validation rules

- Dependency set is exactly the constitution-mandated minimum + the single
  test-scope `quarkus-junit` exception (spec clarification); no speculative
  libraries (FR-004, spec Assumptions).
- Java 21 only: compiler `release=21` + enforcer range `[21,22)` (spec edge
  case: clear attributable error, never a cryptic class-version failure).
- No `application.properties` in this slice — runtime config (virtual threads,
  datasource defaults) is owned by 001-FR-04.
- Boot must succeed without PostgreSQL 18 (clarified posture); must not
  silently mask DB unavailability.
- No application feature code (sources/datasets/jobs/artifacts) ships here.
- No secrets; `.gitignore` must exclude `target/` and IDE/local artifacts while
  keeping `mvnw`, `mvnw.cmd`, and `.mvn/wrapper/*` committed.

## Derived constants (the contract later features read)

- Build command (root): `./mvnw quarkus:dev` (dev) | `./mvnw package` | `./mvnw verify`
- HTTP port: `8080` (Quarkus default — no config override this slice)
- Coordinates: `dev.datafetcher:data-fetcher` (Java 21)
- Pinned versions: Quarkus `3.33.3.1` | Maven `3.9.9` | wrapper `3.3.2`
- Runtime-config home for later features: `src/main/resources/application.properties` (created by 001-FR-04)