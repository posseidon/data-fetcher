# Quickstart: Quarkus Application Skeleton (001-FR-03)

Boots the empty Quarkus (Java 21, Maven) app at the repo root and proves build
integrity. No feature endpoints exist yet. Requires only a Java 21 toolchain —
Maven is provided by the committed wrapper.

## Toolchain check

```sh
java -version   # must be 21.x
./mvnw -v       # first run downloads Maven 3.9.9 into ~/.m2/wrapper; then prints the version
```

Wrong Java version → the build fails with a clear "JDK version not in allowed
range" enforcer message (not a cryptic bytecode error).

## Dev boot (US1 / FR-008 / SC-003)

```sh
./mvnw quarkus:dev
```

Expect a successful startup log reporting the HTTP port:

```text
data-fetcher 0.1.0-SNAPSHOT on JVM (powered by Quarkus 3.33.3.1) started in X.XXXs.
Listening on: http://localhost:8080
```

Measured 2026-09-05 (SC-003 baseline): first cold boot 12.1 s (incl. dependency
download), warm boot 2.9 s — well under the ~2 min / zero build errors target.

No endpoints are served (no feature code). PostgreSQL 18 does NOT need to be
up — the skeleton boots with the DB down; DB errors surface at first use, not
at boot.

## Build integrity (US2 / FR-001 / SC-004)

```sh
./mvnw package   # clean checkout: dependency set resolves + compiles
./mvnw verify    # also runs the one boot smoke test (AppSmokeTest)
```

The smoke test boots the Quarkus context and asserts `quarkus.http.port`
resolves to a numeric port (test mode binds the test port, 8081 for Quarkus
defaults), proving the mandated dependency set loads through SmallRye Config.

## Troubleshooting

- **Enforcer rejection ("Detected JDK Version ... not in the allowed range")**
  — the active toolchain is not Java 21.x. Install/select a JDK 21
  (`quarkus.maven` builds require exactly the enforced range `[21,22)`).
- **`./mvnw` fails to bootstrap** — `.mvn/wrapper/` is missing or not
  committed. A clean clone must contain `mvnw`, `mvnw.cmd`, and
  `.mvn/wrapper/maven-wrapper.jar` (wrapper is committed; do not gitignore it).
- **Startup errors referencing the datasource** — expected if PostgreSQL 18 is
  down and a later feature has configured it; the skeleton itself configures no
  datasource. Start the infra (`docker compose up -d`, features 001-FR-01/02)
  or wait for 001-FR-04.
- **`verify` runs 1 test, always green** — by design: `AppSmokeTest` is the
  only test in the skeleton.

## Teardown

```sh
./mvnw quarkus:dev   # Ctrl-C stops dev mode
```

No containers, volumes, or host files are created by this sub-feature. Infra
teardown is owned by 001-FR-01/02.