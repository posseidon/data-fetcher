# Data Model: Local Infrastructure Compose (001-FR-01)

Phase 1 output. This feature manages infrastructure, not application data —
the "entities" are the compose footprint and the runtime config contract the
rest of the system binds against.

## Entities

### `df-postgres` (PostgreSQL 18 service)

| Attribute | Value | Source |
|-----------|-------|--------|
| image | `postgres:18-alpine` | research D2 / spec (PostgreSQL 18) |
| container_name | `df-postgres` | spec FR-002.1 |
| network | `df-net` | spec FR-002.1 |
| host binding | `127.0.0.1:${POSTGRES_HOST_PORT:-5432}:5432` | spec FR-002.2 / research D8 |
| env db | `POSTGRES_DB=df_agent_db` | spec FR-010.1 |
| env user | `POSTGRES_USER=df_dev_user` | spec FR-010.1 |
| env pass | `POSTGRES_PASSWORD=df_dev_secret` | spec FR-010.1 |
| volume | `df-postgres-data` → `/var/lib/postgresql` (parent, not `/data` — postgres 18 image uses major-version subdirs) | implemented compose |
| healthcheck | `pg_isready -U df_dev_user -d df_agent_db` | spec FR-002.4 |

### `df-consul` (Consul 2.0 service)

| Attribute | Value | Source |
|-----------|-------|--------|
| image | `hashicorp/consul:2.0.3` | research D3 / spec (Consul 2.0) |
| container_name | `df-consul` | spec FR-002.1 |
| network | `df-net` | spec FR-002.1 |
| host binding — HTTP/UI | `127.0.0.1:${CONSUL_HTTP_HOST_PORT:-8500}:8500` | spec FR-002.2 / FR-005.1 |
| host binding — DNS (UDP) | `127.0.0.1:${CONSUL_DNS_HOST_PORT:-8600}:8600/udp` | spec FR-002.2 / FR-005.1 |
| host binding — DNS (TCP) | `127.0.0.1:${CONSUL_DNS_HOST_PORT:-8600}:8600/tcp` (colima cannot forward host-loopback UDP) | implemented compose |
| unexposed (internal only) | 8300/8301/8302 (gossip/RPC) | spec FR-005.1 |
| command | `-server -bootstrap-expect=1 -bind=0.0.0.0 -client=0.0.0.0 -data-dir=/consul/data -ui` | research D4 |
| healthcheck | `consul members` | spec FR-002.4 |

### `df-net` (network)

| Attribute | Value | Source |
|-----------|-------|--------|
| type | user-defined bridge | spec FR-002.1 |
| services attached | `df-postgres`, `df-consul` (+ future app) | spec FR-002.2 |
| internal hostnames | `df-postgres`, `df-consul` | spec FR-002.2 |

### Named volume `df-postgres-data`

PostgreSQL data survives `compose down`/up across local dev iterations
(research D10). Bronze bind mount is NOT part of this sub-feature
(001-FR-02).

## Derived constants (the contract every later feature reads)

- JDBC URL (host path): `jdbc:postgresql://localhost:5432/df_agent_db`
- JDBC URL (in `df-net`): `jdbc:postgresql://df-postgres:5432/df_agent_db`
- Consul HTTP: `http://localhost:8500` (host), `http://df-consul:8500` (in net)
- Consul DNS: `127.0.0.1:8600` (host, UDP + TCP twin; stock Consul serves only
  `*.consul` names — query `consul.service.consul`, e.g.
  `dig +tcp +short @127.0.0.1 -p 8600 consul.service.consul`)
- Credential tuple: `df_dev_user` / `df_dev_secret` / `df_agent_db`

## Lifecycle / state transitions (compose-level)

| State | Trigger | Signal |
|-------|---------|--------|
| created | `docker compose up` | container exists |
| running | compose engine | `docker compose ps` "running" |
| healthy | healthcheck passes | `docker compose ps` "healthy" (SC-001) |
| unhealthy → restart | daemon policy | `docker compose ps` "unhealthy" |
| exited | `docker compose down` / crash | `docker compose ps` "exited" |

SC-001 = both services reach **healthy** with zero manual steps.

## Constraints & validation rules

- No secrets in repo; credentials are fixed placeholders (FR-010, FR-010.1).
- No host binding outside `127.0.0.1` (FR-002.2).
- No Consul privacy ports (8300/8301/8302) mapped (FR-005.1).
- Exactly two containers + one network + one named volume (spec acceptance
  scenario 2).
- Images pinned for reproducibility (research D2/D3).