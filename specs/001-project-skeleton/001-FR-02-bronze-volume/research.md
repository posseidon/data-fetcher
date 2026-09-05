# Research: Bronze Volume Bind Mount (001-FR-02)

Phase 0 output — decisions, rationale, alternatives. The spec's one open
first-boot question (host dir pre-create) was resolved in `/speckit.clarify`
(recorded in `spec.md` §Clarifications), so no `NEEDS CLARIFICATION` items
remain.

## D1 — Bind mount vs named Docker volume

- **Decision**: Use a **bind mount** (`~/data/bronze` → `/data/bronze`), not a
  named volume.
- **Rationale**: Spec FR-003 explicitly requires host `~/data/bronze` and the
  container Bronze root to point to the same files. A bind mount is the only
  option that exposes the real host filesystem path. This matches the
  constitution's local-dev model ("Bronze stored on a Docker volume") where
  the developer needs direct host access to the staging area.
- **Alternatives considered**: Named volume (lives in Docker's storage dir,
  not at `~/data/bronze` — fails FR-003); `tmpfs` (ephemeral, no host path).

## D2 — Host path pre-creation (resolved in clarify)

- **Decision**: `~/data/bronze` MUST be pre-created with an explicit
  `mkdir -p ~/data/bronze` before `docker compose up`. Ship as
  `scripts/precreate-bronze.sh`.
- **Rationale**: The clarified contract (spec FR-003a) is explicit: compose
  will not auto-create the path; relying on auto-creation risks a silent bind
  to the wrong location. Explicit pre-create makes the first-boot behavior
  deterministic and the failure mode (path absent → compose fails) loud.
- **Alternatives considered**: Compose auto-create (per clarification, rejected
  — silent wrong-path risk); embed `mkdir` in a manual README step (not
  executable/reproducible).

## D3 — Mount target service (no app service exists yet)

- **Decision**: Declare a minimal long-running `bronze-mount` service in
  `docker-compose.yml` (image `busybox`, `command: sleep infinity`) whose sole
  job is to carry the bind mount and serve as the probe target. The Quarkus
  app service (001-FR-03/04) later replaces or reuses the exact
  `container:/data/bronze` path.
- **Rationale**: Docker Compose cannot declare an unattached bind mount — a
  bind mount must live under a service's `volumes:` list, and the container
  must actually run for the mount to exist and be probeable (SC-002). A
  `busybox` carrier is the minimal rendezvous point; it proves the mount
  contract in this slice and gives a stable `container:/data/bronze` target
  the app service reuses.
  `ponytail: bronze-mount busybox carrier exists only to host the bind until FR-03/04 swaps in the real service; delete it when the app service lands`
- **Alternatives considered**: Declare mount on the not-yet-existing app
  service (invalid — no such service; compose fails); orphan top-level volume
  (bind mounts can't be orphaned; named volumes only, and a named volume
  fails D1); `docker compose run` ad-hoc probe without a committed service
  (works for one-off verify but leaves no durable compose contract — spec
  FR-003 wants the mount in the composed file).

## D4 — Probe image: `busybox`

- **Decision**: `busybox` (latest) as the bronze-mount carrier image.
- **Rationale**: Tiny, ubiquitous, exec-friendly; lets the developer
  `docker compose exec bronze-mount` and write/read a file for the
  bidirectional probe (SC-002). No app runtime needed for a mount probe.
- **Alternatives considered**: `alpine` (equally fine, larger pull); the real
  app image (not built until 001-FR-03).

## D5 — Bidirectional permission model (root vs host reads)

- **Decision**: Document the ownership expectation in the data model and
  contract: files written by the container run as root will be root-owned on
  the host; for the bind-mount probe this is acceptable (readable probe only),
  and host tools may need `sudo`/group membership to read root-owned Bronze
  artifacts. No `user:` directive is added in this sub-feature — the app
  service's uid policy is a 001-FR-03/04 concern.
- **Rationale**: Spec edge case "file written by container as root" must be
  documented so host tooling expectations are set. Scope stays minimal here
  (mount contract only).
- **Alternatives considered**: Force a `user:` uid now (premature — app
  service not yet defined; would conflict with FR-03/04 uid decisions).

## D6 — Verification (SC-002 testability)

- **Decision**: `docker compose config` (validate) + bidirectional file probe:
  write `probe.txt` inside container → appears at `~/data/bronze/probe.txt`;
  write on host → appears at `/data/bronze` in container.
- **Rationale**: SC-002 defines "bind-mount check passes" exactly this way;
  it is fully deterministic and needs no network fixtures.
- **Alternatives considered**: Automated unit test against the compose file
  (overkill — compose is declarative; a live two-way file probe is the real
  contract proof).
