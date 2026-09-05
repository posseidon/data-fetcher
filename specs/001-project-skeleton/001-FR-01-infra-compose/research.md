# Research: Local Infrastructure Compose (001-FR-01)

Phase 0 output — decisions, rationale, alternatives. All `NEEDS CLARIFICATION`
items from the spec were resolved during the `/speckit.clarify` session
(recorded in `spec.md` §Clarifications).

## D1 — Compose file: single root `docker-compose.yml`, compose v2

- **Decision**: One file at repo root, `docker compose` (v2) syntax.
- **Rationale**: Spec FR-002 requires the compose file to be the single source
  of truth for local dependencies; v2 is the current engine, is validated by
  `docker compose config`, and supports `depends_on.condition` health wiring.
- **Alternatives considered**: Multiple per-service files (overkill for 2
  services); docker-compose v1 (legacy, dropped by Docker).

## D2 — PostgreSQL image: `postgres:18-alpine`

- **Decision**: `postgres:18-alpine` (major-line tag, pinned by the spec's
  "PostgreSQL 18" decision).
- **Rationale**: Alpine variant keeps local footprint small; includes
  `pg_isready` for the healthcheck probe (spec FR-002.4). Spec pins Major 18.
- **Alternatives considered**: `postgres:18` (Debian base, larger pull);
  `postgres:18.x.y` exact-suffix (tighter pin but blocks patch pickup; 18 is a
  GA major — pinning the major is adequate for local dev).

## D3 — Consul image: `hashicorp/consul:2.0.3` (exact patch)

- **Decision**: `hashicorp/consul:2.0.3` — exact patch tag.
- **Rationale**: Spec pins "Consul 2.0". Docker Hub confirms `2.0.x` tags
  exist (2.0.0 → 2.0.3, linux/amd64 + arm64). Exact patch = reproducible; the
  moving `2.0` tag would drift. Verified Publisher image (official `consul`
  image deprecated since 1.16).
- **Alternatives considered**: `hashicorp/consul:2.0` (moving tag, drift risk);
  `consul:1.22`/`hashicorp/consul:1.22.x` (older line — violates the pinned
  2.0 decision).

## D4 — Consul runtime mode: single server node (NOT `-dev`)

- **Decision**: Run a single **server** agent with
  `-server -bootstrap-expect=1 -bind=0.0.0.0 -client=0.0.0.0 -data-dir=/consul/data -ui`.
- **Rationale**: Dev-mode agents do not run the built-in DNS server; the
  clarified contract (spec FR-005.1) requires reaching `8600/udp` DNS from the
  host. A single bootstrap server node serves HTTP API, UI, and DNS on the
  standard ports (`-client=0.0.0.0` exposes the client listener inside the
  container so host mappings and the loopback-bound 8500/8600 work).
- **Alternatives considered**: `-dev` (no DNS server — breaks FR-005.1);
  dual-node cluster (unneeded for single-replica local dev).

## D5 — Network & identity: explicit `df-net` bridge, `df-` names

- **Decision**: Declare an explicit user-defined bridge `df-net`; set
  `container_name: df-postgres`, `df-consul`; optionally `name: df-infra`
  for the project.
- **Rationale**: Clarifications fix the naming to avoid daemon-level conflicts
  and give stable DNS-hostnames inside the bridge for the future app service
  (FR-002.1/FR-002.2).
- **Alternatives considered**: Default `default` network (unpredictable name);
  `network_mode: host` (exposes everything, breaks isolation + loopback policy).

## D6 — Loopback-only host bindings

- **Decision**: Every host mapping is `127.0.0.1:<host>:<container>`; no
  `0.0.0.0`/bare `"host:container"` mappings.
- **Rationale**: Clarified security posture — unencrypted datastore and
  management/UI ports must not be reachable from other interfaces; container-
  to-container traffic stays inside `df-net`.
- **Alternatives considered**: Bare `5432:5432` (binds all interfaces — rejected);
  no host bindings at all (breaks host-side dev/E2E assertions).

## D7 — Healthchecks & readiness contract (SC-001 testability)

- **Decision**: Per-service `healthcheck` (PostgreSQL `pg_isready -U df_dev_user
  -d df_agent_db`; Consul `consul members`) with `start_period`/`interval`;
  dependent services use `depends_on.condition: service_healthy`.
- **Rationale**: Spec clarification defines "healthy" this way; `docker compose
  ps --format json` then gives a deterministic SC-001 signal. Probes use tools
  guaranteed in each image (no curl/wget assumptions).
- **Alternatives considered**: "Container running" as healthy (weak — masks
  boot failures); `curl`/`wget` probes (not guaranteed present in images).

## D8 — CI dynamic ports: env-var templating, no extra file

- **Decision**: Host ports are template-able: `"127.0.0.1:${POSTGRES_HOST_PORT:-5432}:5432"`
  (same for Consul 8500/8600). CI sets `POSTGRES_HOST_PORT=0` etc.; tests
  resolve the assigned port via `docker compose port`.
- **Rationale**: Spec FR-002.3 requires `0:<container>` capability for
  parallel CI. Templating keeps it in one file (single source of truth) and
  defaults to the fixed local-dev ports.
- **Alternatives considered**: Separate `docker-compose.ci.yml` override
  (splits truth across two files); Testcontainers managing its own containers
  (out of scope — the compose file remains the contract).

## D9 — Credentials: fixed placeholders inline in compose

- **Decision**: `POSTGRES_DB=df_agent_db`, `POSTGRES_USER=df_dev_user`,
  `POSTGRES_PASSWORD=df_dev_secret` declared inline; mirrored verbatim into
  001-FR-04's `application-dev.properties`.
- **Rationale**: Spec FR-010.1 fixes the values as non-production placeholders;
  inline keeps the compose file the single source of truth. No `.env` file
  (an `.env` committed with creds would be a secret-in-repo anti-pattern).
- **Alternatives considered**: `.env` file (another moving part, no benefit for
  fixed placeholder values); env-var-gated no-default creds (spec chose fixed
  values over fail-loud option).

## D10 — Data durability / persistence of Consul KV

- **Decision**: PostgreSQL uses a named volume `df-postgres-data` (data must
  survive local restarts for dev convenience). Consul runs with
  `-data-dir=/consul/data` inside the container and NO host volume.
- **Rationale**: DB data loss on every `compose down` is unacceptable for dev
  iteration; Consul KV is re-seedable in feature 005 (consul-seed.sh) so
  ephemerality is acceptable there.
  `ponytail: consuls data is ephemeral — keys vanish on container recreate; reseed via scripts/consul-seed.sh, add a volume only if KV must survive container replacement`
- **Alternatives considered**: Consul volume (extra state to manage, KV is
  re-seedable); no DB volume (data loss churn in dev).