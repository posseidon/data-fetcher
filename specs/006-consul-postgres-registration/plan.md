# Implementation Plan: PostgreSQL Consul Registration

**Branch**: `006-consul-postgres-registration` | **Date**: 2026-09-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-consul-postgres-registration/spec.md`

## Summary

Register the `df-postgres` container into the local Consul datacenter via a
repo-tracked service-definition file loaded from the agent's config directory,
so later features (005) can discover PostgreSQL through Consul. `df-postgres`
gets a static `df-net` IP; Consul monitors it with a built-in TCP check
(interval 10s, timeout 5s, deregister-after-1m; `consul reload` re-registers
after a deregistration).

## Technical Context

**Language/Version**: YAML (Docker Compose v2), JSON (Consul service definition)
**Primary Dependencies**: Docker Compose v2, `postgres:18-alpine`,
`hashicorp/consul:2.0.3`
**Storage**: N/A (compose project only; PG named volume untouched)
**Testing**: manual verification via consul HTTP API (`curl`) per acceptance scenarios
**Target Platform**: local/CI; loopback-exposed, bridge-isolated
**Project Type**: infrastructure (compose file + tracked config)
**Performance Goals**: SC-001 — `df-postgres` visible in catalog within 30s of up
**Constraints**: no app-side registration scripts/containers; compose project is
single source of truth; static IP for deterministic registration
**Scale/Scope**: single local datacenter, one service (`df-postgres`)

## Constitution Check

*GATE: N/A — no constitution file present in repo.*

## Project Structure

### Documentation (this feature)

```text
specs/006-consul-postgres-registration/
├── plan.md              # this file
├── research.md          # decisions + rationale
├── spec.md              # requirements + clarifications
└── checklists/requirements.md
```

### Source Code (repository root)

```text
docker-compose.yml          # + subnet on df-net, static IP, config mount, depends_on
consul/config/
└── df-postgres.json        # service definition + TCP check (new, repo-tracked)
```

## Implementation Steps

1. **`consul/config/df-postgres.json`** (new): service entry — `name`
   `df-postgres`, `id` `df-postgres`, `tags [db, postgres]`, `address`
   `172.20.0.10`, `port 5432`; check — `tcp 172.20.0.10:5432`, interval 10s,
   timeout 5s, `deregister_critical_service_after` 1m.
2. **`docker-compose.yml`**:
   - `networks.df-net.ipam.config[0].subnet: 172.20.0.0/24`
   - `services.df-postgres.networks.df-net.ipv4_address: 172.20.0.10`
   - `services.df-postgres.depends_on: df-consul: {condition: service_healthy}`
   - `services.df-consul.volumes: ./consul/config:/consul/config` (rw — image
     entrypoint chowns the dir; read-only mount aborts boot)
3. **Verify**: `docker compose up -d` → `curl
   'http://127.0.0.1:8500/v1/health/service/df-postgres?passing'` returns
   `Address 172.20.0.10`, `Port 5432`, status `passing`; unhealthy drift shows
   `critical` and service drops from catalog after ~1m; recovery after
   deregistration via `docker exec df-consul consul reload`.

## Complexity Tracking

None — single-file infra change; no constitution violations to justify.