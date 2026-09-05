# data-fetcher Development Guidelines

Auto-generated from feature plans. Last updated: 2026-09-05

## Active Technologies

- Docker Compose v2 + PostgreSQL 18 + Consul 2.0 (001-project-skeleton)
- PostgreSQL 18 (001-project-skeleton)

## Project Structure

```text
docker-compose.yml
specs/001-project-skeleton/001-FR-01-infra-compose/
  ├── spec.md        # clarified spec
  ├── plan.md        # implementation plan
  ├── research.md    # decisions + rationale
  ├── data-model.md  # infra footprint model
  ├── quickstart.md  # up/verify/teardown
  └── contracts/
      └── infrastructure-contract.md   # host-facing endpoint contract
scripts/consul-seed.sh        # feature 005 scope, pre-existing
```

## Commands

- `docker compose up -d` / `docker compose ps` / `docker compose logs <svc>`
- `docker compose port <service> <port>`
- Health probes: `pg_isready -U df_dev_user -d df_agent_db`, `curl http://localhost:8500/v1/status/leader`, `dig +tcp +short @127.0.0.1 -p 8600 consul.service.consul`

## Code Style

No application code in this sub-feature (infrastructure declaration).
App conventions (Quarkus/Java 21) tracked from 001-FR-03 onward.

## Recent Changes

- 001-project-skeleton: Added Docker Compose v2 infra — PostgreSQL 18 +
  Consul 2.0 on `df-net` bridge, loopback-only bindings, readiness
  healthchecks, dynamic-port CI escape hatch.

<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->