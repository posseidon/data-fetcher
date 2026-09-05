# Build Contract: Quarkus Application Skeleton (001-FR-03)

Host-facing contract of this sub-feature: the Maven build surface and boot
behavior later features (002-004) compile against and extend. This contract
defines the *base*, not endpoints — no feature endpoints exist yet.

## Build tool surface

| Item | Contract | Owner |
|------|----------|-------|
| Build tool | committed Maven wrapper; `./mvnw` works with NO pre-installed Maven | FR-001 |
| Maven version | `3.9.9` (pinned in `.mvn/wrapper/maven-wrapper.properties`) | research D4 |
| Java | 21 only — `release=21` + enforcer `[21,22)`; wrong JDK fails loudly | FR-001, spec edge case |
| Dev boot | `./mvnw quarkus:dev` → successful startup log + HTTP port reported | FR-008 |
| Package | `./mvnw package` → clean-checkout build success | SC-004 |
| Verify | `./mvnw verify` → runs the boot smoke test | US2 (clarified) |
| HTTP port | `8080` (Quarkus default; no override in this slice) | quickstart |
| Coordinates | `dev.datafetcher:data-fetcher` (jar) | research D2 |

## Mandated dependency set (lock)

Later features MUST compile against exactly:

- `quarkus-rest-jackson` — REST tool surface
- `quarkus-hibernate-orm-panache` + `quarkus-jdbc-postgresql` — PostgreSQL 18
  persistence
- SmallRye Config — configuration loading (Quarkus core, no explicit artifact)
- `quarkus-junit` (test scope) — the single approved test dependency

**Additionally**: single-replica/local-dev runtime posture; WebSockets, MCP
SDKs, Consul client, and unapproved libraries MUST NOT be added (FR-004).

## Boot behavior contract

- Skeleton boots with **no feature endpoints**.
- **No datasource configured yet** (this slice): boot succeeds regardless of
  PostgreSQL 18 — DB errors surface at first use (clarified Q1, 2026-09-05).
- **Superseded for the dev profile by 001-FR-04**: from 001-FR-04 onward,
  `application-dev.properties` configures a datasource and a startup
  reachability probe makes `quarkus:dev` FAIL loudly when PostgreSQL 18 is down
  (SC-007). Non-dev profiles keep this lazy posture.
- Hard fail-fast validation of runtime config is **feature 005**, not this
  slice.

## Extension points (what later features add on top)

- **001-FR-04**: `src/main/resources/application-dev.properties` — datasource
  defaults, virtual threads, Bronze root `/data/bronze`, overriding
  `quarkus.http.port` if needed — plus the `StartupRuntimeProbe` boot observer.
  Adds feature config on this root module.
- **001-FR-02 → app service**: the `df-bronze-mount` carrier service continues
  to own the `/data/bronze` bind mount until a future slice wires the app
  service container against the same target path.
- **Features 002-005**: individual Quarkus/CDI sources, Panache entities, and
  endpoints added to `src/main/java/dev/datafetcher/...` on this root module.

## Verification of this contract

1. `./mvnw -v` prints Maven 3.9.9 on a Java-21-only checkout.
2. `./mvnw verify` → BUILD SUCCESS, 1 smoke test passed.
3. `./mvnw quarkus:dev` → startup log reports `http://localhost:8080`, then
   Ctrl-C cleanly.
4. On a non-21 JDK, the build fails with the enforcer's clear message.