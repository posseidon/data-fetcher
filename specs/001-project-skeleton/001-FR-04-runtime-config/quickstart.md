# Quickstart: Runtime Configuration & Dev Boot (001-FR-04)

Applies the dev runtime defaults on the 001-FR-03 skeleton and proves they take
effect. Requires Java 21, infra up (`docker compose up -d`, 001-FR-01), and
`./mvnw` (Maven via wrapper).

## Prereqs

```sh
docker compose up -d   # 001-FR-01: PostgreSQL 18 + Consul 2.0
docker compose ps      # df-postgres healthy
./mvnw -v              # Maven 3.9.9 / Java 21
```

## Dev boot with defaults (SC-005, scenario 1-2)

```sh
./mvnw quarkus:dev
```

Expect the probe/observer line in the startup log (recorded 2026-09-05, warm
toolchain; boot reached "started in 1.487s"):

```text
INFO  [dev.datafetcher.config.StartupRuntimeProbe] (Quarkus Main Thread) runtime defaults: virtual threads=true | datasource=jdbc:postgresql://localhost:5432/df_agent_db | bronze.root=/data/bronze
INFO  [dev.datafetcher.config.StartupRuntimeProbe] (Quarkus Main Thread) postgresql reachable: ok
```

Two facts asserted — virtual threads + datasource — MUST both appear. Note:
`src/main/resources/application.properties` exists but is EMPTY — Quarkus
requires a base file beside `application-dev.properties` before it loads the
profile file; all default values live in the dev-profile file only.

## Override via env var (SC-006, scenario 3)

```sh
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5433/df_agent_db \
BRONZE_ROOT=/tmp/df-bronze \
./mvnw quarkus:dev
```

Expect the observer line to report the overridden values (`:5433`,
`/tmp/df-bronze`). Zero file edits — the properties file is untouched.

## PG-down boot (SC-007, scenario 4)

```sh
docker compose stop df-postgres
./mvnw quarkus:dev     # startup fails loudly; no "Listening on"/"started in" line
# strict process-exit proof (dev runner persists by design):
./mvnw package -DskipTests
java -Dquarkus.profile=dev -jar target/quarkus-app/quarkus-run.jar   # exit code 1
docker compose start df-postgres
```

The probe's connection failure aborts startup loudly — no silent success. The
`quarkus:dev` RUNNER stays alive after the failed start by design (hot-reload
shell awaits changes); the app never becomes available. Strict JVM termination
(non-zero) is proven with the packaged `quarkus-run.jar` in dev profile. This
supersedes the 001-FR-03 "boots with PG down" note for the dev profile.

## Build integrity stays green

```sh
./mvnw verify   # AppSmokeTest still passes. Test profile auto-starts a Dev
                # Services PostgreSQL → probe logs its URL + reachable: ok;
                # packaged/prod profiles have no datasource → probe skipped.
```

## Troubleshooting

- **Boot fails with connection error while infra "healthy"** — host port
  changed? Override the URL (`QUARKUS_DATASOURCE_JDBC_URL`) or realign compose
  host port (001-FR-01 `POSTGRES_HOST_PORT`).
- **Observer line missing** — running a non-dev profile? `quarkus:dev` is the
  only default-profile activation; packaged/prod runs log "no datasource
  configured — probe skipped" (the test profile logs a Dev Services URL).
- **`bronze.root` not in log during override test** — confirm `BRONZE_ROOT`
  (not `BRONZE.ROOT`) and re-run.

## Teardown

```sh
./mvnw quarkus:dev   # Ctrl-C
docker compose down  # infra teardown owned by 001-FR-01
```

No new containers, volumes, or host files created by this sub-feature.