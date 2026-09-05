# Data Model: Bronze Volume Bind Mount (001-FR-02)

Phase 1 output. This feature manages infrastructure, not application data —
the "entities" are the bind-mount declaration and the path contract later
features bind against.

## Entities

### `bronze-mount` (bind-mount carrier service)

| Attribute | Value | Source |
|-----------|-------|--------|
| image | `busybox` | research D4 |
| command | `sleep infinity` | research D3 |
| network | `df-net` (reuse from 001-FR-01) | infra-compose contract |
| container_name | `df-bronze-mount` | FR-01 naming convention |
| mount source (host) | `~/data/bronze` (`${HOME}/data/bronze`) | spec FR-003 |
| mount target (container) | `/data/bronze` | spec FR-003 / constitution default |
| mount type | bind | research D1 |

### Bronze bind mount (the path contract)

| Attribute | Value | Source |
|-----------|-------|--------|
| host root | `~/data/bronze` | spec FR-003 |
| container root | `/data/bronze` | spec FR-003 |
| direction | bidirectional (same inode via bind) | spec US-1 / SC-002 |
| first-boot creation | `mkdir -p ~/data/bronze` (explicit, before `compose up`) | spec FR-003a / clarify |
| pre-create script | `scripts/precreate-bronze.sh` | research D2 |
| ownership (container-as-root writes) | root-owned on host; readable via probe, host tools may need sudo/group | research D5 |

No authoritative data lives here. Bronze is the reachable staging path the
artifact writer (feature 004) later fills with the constitutional
`category/service/dataset/dt` layout. This slice does not create that layout.

## Lifecycle / state transitions

| State | Trigger | Signal |
|-------|---------|--------|
| host dir created | `scripts/precreate-bronze.sh` (or manual `mkdir -p`) | `~/data/bronze` exists |
| mount carried | `docker compose up` → `bronze-mount` container runs | `docker compose ps` "up" |
| file sync | bind mount active | file appears both sides (SC-002) |
| file-at-host-path | `~/data/bronze` is a file, not dir | compose fails loudly, no silent bind |
| host dir deleted while mounted | host rm | container sees empty/nonexistent root (dev-managed; no recovery logic in this slice) |

## Constraints & validation rules

- Host dir MUST exist before `compose up` (FR-003a); compose fails loudly if absent.
- Container path is exactly `/data/bronze` (constitution default; must match
  001-FR-04 `application-dev.properties` default and the app service in FR-03/04).
- No named volume fallback (research D1) — must remain a bind mount.
- No application code writes to Bronze in this slice (spec Assumptions).
- No secrets involved; bind path is fixed host-local `~/data/bronze`.

## Derived constants (the contract later features read)

- Host Bronze root: `~/data/bronze`
- Container Bronze root: `/data/bronze`
- Pre-create command: `mkdir -p ~/data/bronze` (wrapped in
  `scripts/precreate-bronze.sh`)
- Probe target service: `df-bronze-mount` (replaced by the app service in
  FR-03/04; the `/data/bronze` target path is unchanged)