# Infrastructure Contract: Local Docker Compose (001-FR-01)

The interface this sub-feature exposes to developers, E2E tests, and all later
features (002-005). Stable once implemented — later features and the Quarkus
config (001-FR-04) bind against these, not against arbitrary values.

## Endpoints (host-visible)

All host bindings are **loopback-only** (`127.0.0.1`) per security posture.

| Service | Host address | Host port (default) | Container port | Protocol | Purpose |
|---------|--------------|---------------------|----------------|----------|---------|
| df-postgres | 127.0.0.1 | 5432 (`POSTGRES_HOST_PORT`) | 5432 | TCP | JDBC / psql |
| df-consul | 127.0.0.1 | 8500 (`CONSUL_HTTP_HOST_PORT`) | 8500 | TCP | HTTP API + UI |
| df-consul | 127.0.0.1 | 8600 (`CONSUL_DNS_HOST_PORT`) | 8600 | UDP | DNS resolution |
| df-consul | 127.0.0.1 | 8600 (`CONSUL_DNS_HOST_PORT`) | 8600 | TCP | DNS over TCP (twin mapping; see note) |

Not exposed: Consul gossip/RPC 8300/8301/8302 (internal to `df-net` only).

> DNS notes: 8600 is mapped as both UDP and TCP. colima's Docker engine cannot
> forward host-loopback UDP published ports, so the TCP twin is what carries
> loopback DNS traffic. Stock Consul refuses bare single-label DNS names (only
> serves `*.consul`), so query services as FQDNs, e.g.
> `dig +tcp +short @127.0.0.1 -p 8600 consul.service.consul`.

## Connectivity from inside `df-net`

| Target | Hostname | Port |
|--------|----------|------|
| PostgreSQL | `df-postgres` | 5432 |
| Consul HTTP | `df-consul` | 8500 |
| Consul DNS | `df-consul` | 8600 |

## Credentials (dev placeholders, no secrets in repo)

| Key | Value |
|-----|-------|
| POSTGRES_DB | `df_agent_db` |
| POSTGRES_USER | `df_dev_user` |
| POSTGRES_PASSWORD | `df_dev_secret` |

- JDBC (host): `jdbc:postgresql://localhost:5432/df_agent_db`
- JDBC (in-net): `jdbc:postgresql://df-postgres:5432/df_agent_db`
- These exact values MUST mirror 001-FR-04 `application-dev.properties`.

## Readiness contract (SC-001)

Healthy = compose healthcheck passes, verified by `docker compose ps`.

| Service | Probe |
|---------|-------|
| df-postgres | `pg_isready -U df_dev_user -d df_agent_db` |
| df-consul | `consul members` |

Dependent services MUST use `depends_on.condition: service_healthy`.

## CI simultaneously

Parallel E2E suites set host ports to `0` (dynamic allocation) via env:

```sh
POSTGRES_HOST_PORT=0 CONSUL_HTTP_HOST_PORT=0 CONSUL_DNS_HOST_PORT=0 \
  docker compose up -d
```

Resolve assigned ports with `docker compose port <service> <port>`; the test
framework (e.g. Testcontainers) uses the resolved value.

## Change policy

- Adding a container, changing a host port/hostname, or changing the credential
  tuple is a breaking change for downstream features — must update 001-FR-04
  defaults and this contract in the same change.
- Image tag bumps must stay within the pinned major (Postgres 18 / Consul 2.0)
  unless a review approves a version change.