# Research: PostgreSQL → Consul Registration

**Branch**: `006-consul-postgres-registration` | **Date**: 2026-09-05 | **Spec**: [spec.md](./spec.md)

## Decision 1: Registration mechanism — declarative service-definition file

**Finding**: The Compose Specification has no `consul` top-level element
(verified: compose-spec repo exposes `services`/`volumes`/`networks`/`configs`/
`secrets`/`models` only). Docker Compose cannot natively register a service
into Consul, so "native compose definition" (spec Q1, first clarify session) is
not implementable verbatim.

**Choice**: Drop a declarative `consul/config/df-postgres.json` service
definition into the Consul agent's config directory (`/consul/config`, bind-mount,
repo-tracked) + give `df-postgres` a **static IP** on `df-net`
(`172.20.0.0/24`, PG at `172.20.0.10`).

**Why static IP**: the agent loads config-dir at startup; the advertised
address must be known before `df-postgres` starts. A static IP removes the
chicken-and-egg (dynamic bridge IP unknown at registration time). Keeps the
compose project as single source of truth; no init container, no app-side
script.

**Rejected**:
- Registrator sidecar (label-based) — third container, contradicts user choice
  "no extra containers"; per-container labels drift from compose source of truth.
- Dynamic IP + HTTP register call — needs a script container; defeats
  declarative intent.

## Decision 2: Health check — Consul TCP check, not pg_isready

**Finding**: Consul script checks execute *inside the agent container*.
`hashicorp/consul:2.0.3` ships no `postgresql-client`, so a `pg_isready` script
check cannot run. Compose-level `healthcheck` for `df-postgres` already uses
`pg_isready` (runs in the PG container — valid there); that is unrelated to the
*Consul-catalog* check.

**Choice**: Consul built-in TCP check on `172.20.0.10:5432` — interval 10s,
timeout 5s, `deregister_critical_service_after` 1m.
TCP reachability on 5432 is readiness-equivalent to `pg_isready` accepting
connections.

**Implementation deviations (validated against Consul 2.0.3)**:
- `start_period` does NOT exist in Consul check config (Docker-only field) →
  omitted; a check is critical from registration until first pass, so the 1m
  dereg window doubles as boot grace.
- Section config dir must be mounted **read-write**; the image entrypoint runs
  `chown` on `/consul/config` and aborts on a read-only mount. File content is
  still repo-owned immutable config.

## Architecture sketch

```
host
 ├─ df-postgres (172.20.0.10, port 5432, static)
 │    healthcheck: pg_isready (compose-level, stays)
 │    depends_on: df-consul service_healthy
 ├─ df-consul (server agent, 8500/8600)
 │    volumes: ./consul/config → /consul/config (ro)
 │    loads df-postgres.json at startup → registers + TCP check
 └─ df-net bridge 172.20.0.0/24
```

## Lifecycle behavior

- **Boot**: consul starts → loads `df-postgres.json` → registers
  `df-postgres@172.20.0.10:5432`; check is critical until first pass. PG
  starts after consul reported healthy; check flips passing within interval
  (verified ≤15s, inside the 30s SC window).
- **PG unhealthy**: TCP check fails → status critical → auto-deregistered after
  1m critical (verified: `passing` → `critical` → gone in ~80s).
- **PG recovers before dereg**: check passes again → status auto-recovers to
  passing.
- **PG recovers after dereg**: the entry is gone; agent re-registers only on
  config reload → `consul reload` (or consul container restart). Verified:
  reload → single passing entry.
- **Consul restarts**: config-dir persists on bind mount → re-registers on
  startup (idempotent, service id fixed).
- **PG restarts**: same static IP; if entry still exists, TCP check re-passes
  with no agent action.

## Verification

```bash
docker compose up -d
# goroutine: wait pg healthy (compose healthcheck)
curl -s 'http://127.0.0.1:8500/v1/health/service/df-postgres?passing'
# expect: service df-postgres, Address 172.20.0.10, Port 5432, Status passing
```