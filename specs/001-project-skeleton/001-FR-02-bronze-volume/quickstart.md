# Quickstart: Bronze Volume Bind Mount (001-FR-02)

Proves the Bronze staging-area mount contract: host `~/data/bronze` ⇄
container `/data/bronze`, bidirectional. Requires Docker with compose v2 and
the FR-01 infra compose file present.

## Pre-create the host Bronze dir (first boot)

```sh
scripts/precreate-bronze.sh   # wraps: mkdir -p ~/data/bronze
```

The path MUST exist before `compose up`. Compose will not auto-create it.
Verify:

```sh
ls -ld ~/data/bronze
```

## Up

```sh
docker compose up -d
docker compose ps
# NAME              IMAGE       STATUS
# df-bronze-mount   busybox     Up ... (sleep infinity)
# df-postgres       ...         Up ... (healthy)   (FR-01)
# df-consul         ...         Up ... (healthy)   (FR-01)
```

## Verify the mount (SC-002)

```sh
# host → container
mkdir -p ~/data/bronze
printf 'host-to-container\n' > ~/data/bronze/probe.txt
docker compose exec df-bronze-mount cat /data/bronze/probe.txt
# expecting: host-to-container

# container → host
docker compose exec df-bronze-mount sh -c "echo container-to-host > /data/bronze/probe.txt"
cat ~/data/bronze/probe.txt
# expecting: container-to-host
```

Both probe directions passing = the bind-mount check passes (SC-002).

## Troubleshooting

- **Compose fails / path not a directory** — `~/data/bronze` does not exist or
  is a file. Run `scripts/precreate-bronze.sh`, or for a file-in-the-way case:
  `rm <file>` then create the directory. The pre-create script fails loudly on
  a file-in-the-way (compose v2 itself silently binds such a path — running
  `compose up` without the script on a file path is unsupported).
- **Probe file root-owned on host** — expected; the container writes as root.
  `sudo cat ~/data/bronze/probe.txt` or `chown` if host tools must read it.
- **Mount not shown** — confirm the bind mount is under the `bronze-mount`
  service in `docker-compose.yml` and `docker compose config` validates.

## Teardown

```sh
docker compose rm -sf df-bronze-mount   # remove just the probe carrier
# or full teardown (same as FR-01):
docker compose down --timeout 30
```

Teardown keeps `~/data/bronze` on disk (it is a host path; `compose down`
does not delete it). `scripts/precreate-bronze.sh` re-covers first boots
after manual host cleanup.