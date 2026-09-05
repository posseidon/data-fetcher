# Bronze Mount Contract: Bind Mount (001-FR-02)

The interface this sub-feature exposes to developers, E2E tests, and all later
features. Stable once implemented — the Quarkus app service (001-FR-03/04),
the artifact writer (feature 004), and the app config (001-FR-04
`application-dev.properties`) bind against the path contract below, not against
arbitrary values.

## Path contract

| Key | Value |
|-----|-------|
| Host Bronze root | `~/data/bronze` |
| Container Bronze root | `/data/bronze` |
| Mount type | bind mount (bidirectional) |
| Carrier service (this slice) | `df-bronze-mount` (image `busybox`) |

The container path `/data/bronze` MUST equal the constitution default and the
`application-dev.properties` default set by 001-FR-04. If either changes, this
contract and those defaults MUST change together (breaking change).

## First-boot contract

`~/data/bronze` MUST exist on the host before `docker compose up`. Run the
pre-create script, or the equivalent manually:

```sh
scripts/precreate-bronze.sh   # wraps: mkdir -p ~/data/bronze
# or directly:
mkdir -p ~/data/bronze
```

Compose will NOT auto-create the path. If the path is absent, `compose up`
MUST fail loudly (no silent bind to a wrong path). If the path exists but is
a file (not a directory), `scripts/precreate-bronze.sh` MUST fail with a clear
message and exit non-zero (enforced by the script's guard). NOTE: compose v2
itself does not fail in the file-in-the-way case — it silently binds the file
to `/data/bronze` and the version of the file's contents is invisible in the
container. The pre-create guard is the enforcement point; running `compose up`
without it on such a path is unsupported.

## Bidirectional verification (SC-002)

```sh
# host → container
mkdir -p ~/data/bronze
printf 'host-to-container\n' > ~/data/bronze/probe.txt
docker compose exec df-bronze-mount cat /data/bronze/probe.txt

# container → host
docker compose exec df-bronze-mount sh -c "echo container-to-host > /data/bronze/probe.txt"
cat ~/data/bronze/probe.txt
```

## Ownership expectations

Files written inside the container run as root and appear root-owned on the
host (`root:root`). For the bidirectional probe this is fine (files are
created by whoever writes them). Host tools that must read Bronze artifacts
may need `sudo` or group membership for root-owned files. A `user:` uid
directive is intentionally NOT applied in this slice — the app service's uid
policy is decided in 001-FR-03/04.

## Change policy

- Changing `/data/bronze` (container target) is a breaking change for the
  artifact writer and 001-FR-04 config — must update this contract and the
  same change.
- The `df-bronze-mount` carrier service is replaced by the real app service
  (001-FR-03/04) which reuses the same `/data/bronze` mount; the mount path
  contract is unchanged by that swap.
- Bronze layout (`category/.../dt=...`) and atomic-write semantics are feature
  004 scope, NOT part of this contract.