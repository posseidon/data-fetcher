# Runtime Config Contract: Dev Defaults & Boot Behavior (001-FR-04)

Host-facing contract of this sub-feature: the effective runtime configuration
and boot-time behavior that features 001-004 and feature 005 bind against.
Stable once implemented. Values intentionally mirror the 001-FR-01
infrastructure contract and the 001-FR-02 Bronze mount contract.

## Effective config (dev profile)

Artifact: `src/main/resources/application-dev.properties` (loaded by
`./mvnw quarkus:dev`; NOT loaded by test/packaged/prod profiles). An EMPTY base
`application.properties` sits beside it — REQUIRED by Quarkus before any
`application-{profile}.properties` is read; it holds no values.

| Property | Default | Env override | Notes |
|----------|---------|--------------|-------|
| `quarkus.thread.virtual.enabled` | `true` | `QUARKUS_THREAD_VIRTUAL_ENABLED` | FR-006 |
| `quarkus.datasource.jdbc.url` | `jdbc:postgresql://localhost:5432/df_agent_db` | `QUARKUS_DATASOURCE_JDBC_URL` | host-loopback default only |
| `quarkus.datasource.username` | `df_dev_user` | `QUARKUS_DATASOURCE_USERNAME` | dev placeholder |
| `quarkus.datasource.password` | `df_dev_secret` | `QUARKUS_DATASOURCE_PASSWORD` | dev placeholder, not a secret |
| `bronze.root` | `/data/bronze` | `BRONZE_ROOT` | matches 001-FR-02 mount |

Env overrides use standard SmallRye mapping (`QUARKUS_DATASOURCE_JDBC_URL` →
`quarkus.datasource.jdbc.url`, `BRONZE_ROOT` → `bronze.root`); no custom
config-source code exists.

The in-net datasource hostname `df-postgres` is reachable ONLY via
`QUARKUS_DATASOURCE_JDBC_URL` override — it is not the dev default.

## Boot behavior

1. **Startup observer** (`StartupRuntimeProbe`) logs an explicit line stating
   virtual-threads flag, resolved datasource URL, and resolved `bronze.root`
   (FR-007.2 / SC-005 / SC-006 assertion point).
2. **DB reachability probe** (FR-007.1): when a datasource is configured (dev
   profile), the observer opens+closes a connection; failure aborts startup
   loudly. PostgreSQL 18 down → startup fails with a connection error (SC-007)
   — no silent fallback, no `Listening on`/"started in" line. In `quarkus:dev`
   the RUNNER persists after the failed start by design (hot-reload shell
   awaits changes); strict JVM termination (exit non-zero) is demonstrated with
   the packaged `quarkus-run.jar` in dev profile.
3. **No datasource configured** (packaged/prod profiles): probe logs "no
   datasource configured — probe skipped"; boot unaffected (lazy posture). The
   TEST profile auto-starts a Dev Services PostgreSQL and the probe logs its
   URL + `reachable: ok`.

## Profile semantics

| Profile | Config file | Datasource | Boot on PG-down |
|---------|-------------|------------|-----------------|
| dev | `application-dev.properties` | configured | FAILS startup loudly (probe, SC-007) |
| test | none | absent → Dev Services auto-starts PostgreSQL | succeeds (probe logs reachable: ok) |
| other/prod | none (until feature 005) | absent | succeeds (probe skipped) |

Config-value validation (invalid values, required keys) and Consul 2.0 KV
sourcing remain feature 005 scope — this contract does not pre-empt them.

## Change policy

- Changing a datasource default (host/port/credential tuple) or `bronze.root`
  is a breaking change — update 001-FR-01 `infrastructure-contract.md` and
  001-FR-02 `bronze-mount-contract.md` in the same change.
- Adding a config *source* (Consul, Vault) or startup fail-fast beyond the
  reachability probe is feature 005 scope.
- Accepted overrides are additive and MUST NOT require a code change.

## Verification (maps to spec scenarios)

1. `docker compose up -d` (001-FR-01) → healthy.
2. `./mvnw quarkus:dev` → startup log shows
   `virtual threads=true`, `quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/df_agent_db`,
   `bronze.root=/data/bronze` (SC-005).
3. `QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5433/df_agent_db BRONZE_ROOT=/tmp/df-bronze ./mvnw quarkus:dev`
   → same log line reports the overridden values; zero file edits (SC-006).
4. With `df-postgres` stopped, dev boot → startup fails loudly with the probe's
   connection error (SC-007); strict exit proven via
   `java -Dquarkus.profile=dev -jar target/quarkus-app/quarkus-run.jar` → exit 1.
   `./mvnw verify` still passes (test profile uses a Dev Services PostgreSQL; probe
   logs reachable: ok).