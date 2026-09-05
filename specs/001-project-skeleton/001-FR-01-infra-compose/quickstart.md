# Quickstart: Local Infrastructure (001-FR-01)

Brings up PostgreSQL 18 + Consul 2.0 for local development and E2E testing.
Requires Docker with compose v2 support (`docker compose version`).

## Up

```sh
docker compose up -d
```

Wait for healthy:

```sh
docker compose ps
# NAME          IMAGE                      STATUS
# df-postgres   postgres:18-alpine         Up ... (healthy)
# df-consul     hashicorp/consul:2.0.3     Up ... (healthy)
```

## Verify

```sh
# PostgreSQL ready — same probe as the healthcheck
docker compose exec df-postgres pg_isready -U df_dev_user -d df_agent_db
# expecting: /var/run/postgresql:5432 - accepting connections

# Consul leader elected (HTTP API on loopback)
curl -fsS http://localhost:8500/v1/status/leader
# expecting: a non-empty IP:port string; exit 0

# Consul DNS responds on 8600/tcp (loopback). Stock Consul refuses bare
# single-label names (only serves *.consul), and colima's Docker engine can't
# forward host-loopback UDP — so query *.consul over TCP.
dig +tcp +short @127.0.0.1 -p 8600 consul.service.consul
# expecting: the df-consul container IP (e.g. 172.18.0.x)
```

## Connect from the host

```sh
psql postgresql://df_dev_user:df_dev_secret@localhost:5432/df_agent_db
```

Full endpoint/credential contract: `contracts/infrastructure-contract.md`.

## Troubleshooting

- **Port already in use (5432/8500/8600)** — compose fails loudly. Either stop
  the conflicting process or override the host port:

  ```sh
  POSTGRES_HOST_PORT=5433 CONSUL_HTTP_HOST_PORT=8501 CONSUL_DNS_HOST_PORT=8601 docker compose up -d
  ```

- **Docker daemon not running** — compose errors before starting anything; start
  Docker Desktop / docker daemon first.
- **Container stuck "unhealthy"** — `docker compose logs df-postgres df-consul`
  and inspect probe failures; `docker compose ps --format json` shows status.
- **Wrong path/Auth loop** — confirm host port env vars are applied via
  `docker compose port`.

## CI notes

Set `POSTGRES_HOST_PORT=0 CONSUL_HTTP_HOST_PORT=0 CONSUL_DNS_HOST_PORT=0` for
dynamic allocation (see contract §CI).

## Teardown

```sh
# stop containers
docker compose down --timeout 30
# stop AND delete the postgres named volume (destroys local DB data)
docker compose down --volumes
```

Consul stays ephemeral by design — KV is re-seeded by
`scripts/consul-seed.sh` in feature 005 if needed.