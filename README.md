Data Fetcher Service (MCP Tools) — Spec‑Kit First
Overview
Data Fetcher is a Quarkus (JVM) service that exposes an MCP server over HTTP (iteration 1) with tool-style operations to:


Manage download source definitions (add/remove/update/list/enable/disable) and datasets.

Start asynchronous download jobs that fetch datasets into a predefined Bronze filesystem folder structure.

Validate downloaded artifacts by computed MD5 (always compute and store).

Provide job and artifact inspection (status, listing, metadata, verify).

All downloaded data remains in Bronze until an external Ingestion service picks it up.


Month 1 Scope (Budapest-only)
Month 1 focuses on Budapest-relevant datasets (some inputs are national-level raw files; Budapest clipping/filtering happens downstream in ETL, not here).


Target datasets (initial)
KSH (INSPIRE, geometry/attributes)


https://www.ksh.hu/docs/hun/inspire/grid1km_eov.zip

https://www.ksh.hu/docs/hun/inspire/inspire_population_2022.xlsx

https://www.ksh.hu/docs/hun/inspire/inspire_dwellingstock_2022.xlsx

OSM (Geofabrik Hungary extract)


https://download.geofabrik.de/europe/hungary-latest.osm.pbf

GTFS (BKK Budapest)


https://go.bkk.hu/api/static/v1/public-gtfs/budapest_gtfs.zip

Optional later:


Overpass exports for Budapest-specific POIs/landuse.

Non-Goals (Month 1)
No OpenShift deployment concerns yet (dev/local-first).

Single replica only.

No ETL transformations in this service (clipping, conversion, enrichment happen downstream).

No WebSockets transport initially (planned for iteration 2).

Key Design Decisions (to codify in Spec‑Kit)
Transport: HTTP-first (REST endpoints mapping to MCP “tools”). WebSockets may be added later.

Jobs: downloads are asynchronous; tool call returns a jobId.

Queuing: if concurrency is saturated, jobs remain in QUEUED state until permits free up.

Bronze storage: filesystem path (PVC-like). In local dev: Docker volume.

Persistence: PostgreSQL (container) is the authoritative source registry and stores jobs/artifacts.

Configuration: Consul KV stores runtime knobs (bronze root path, timeouts, retry policy, concurrency limits).

Runtime: Quarkus on JVM using Java 21. Concurrency uses virtual threads.

Resilience: no job resume. On service restart, in-progress downloads fail.

Bronze Folder Layout
All artifacts are written under a configurable Bronze root (default: /data/bronze):


/data/bronze/
category=<category>/
source=<sourceName>/
dataset=<datasetKey>/
dt=YYYY-MM-DD/
<artifact>
<artifact>.md5
_metadata.json



Copy

Download
### Atomicity rule
- download to temp (`.part`) first
- compute MD5 while streaming
- rename/move into final location only when complete
- write `<artifact>.md5` and `_metadata.json` sidecars

---

## Persistence Model (normalized tables)
- `data_source` (authoritative definitions; mutable via MCP tools)
- `dataset` (per-source datasets; mutable via MCP tools)
- `download_job` (async orchestration)
- `artifact` (download outputs, md5, metadata)

### Restart semantics
On application startup:
- any job with status `RUNNING` is marked `FAILED` with an error like `SERVICE_RESTARTED`.

---

## MCP Tool Surface (HTTP v1)
The service exposes “tool endpoints” over HTTP. Tool groups:

### Sources
- `sources.list`
- `sources.get`
- `sources.upsert`
- `sources.delete`
- `sources.enable`
- `sources.validate`

### Datasets
- `datasets.list`
- `datasets.get`
- `datasets.upsert`
- `datasets.delete`
- `datasets.enable`

### Downloads (async)
- `downloads.start` → returns `jobId`
- `downloads.status`
- `downloads.list`
- `downloads.cancel`
- `downloads.retry`

### Artifacts
- `artifacts.list`
- `artifacts.verify`
- `artifacts.readMetadata`

---

## Configuration (Consul KV)
Consul KV is used for runtime configuration (not as the source registry).

Required keys:
- `datafetcher/bronze/rootPath` (string, e.g. `/data/bronze`)
- `datafetcher/download/timeoutMs` (int, e.g. `600000`)
- `datafetcher/download/retry/maxAttempts` (int, must be `1`)
- `datafetcher/download/concurrency/maxParallelJobs` (int, explicit fixed value, e.g. `4`)

If required keys are missing or invalid, the service should fail fast at startup.

---

## Spec‑Kit Outputs Location
All Spec‑Kit artifacts must be generated under:
- `spec/`

---

## Run Spec‑Kit Constitution
You have Spec‑Kit installed offline already. Run from repo root:

```bash
mkdir -p spec
speckit.constitution --output spec/
If your installed Spec‑Kit build uses a different flag name, keep the same intent:


project root: .

output directory: spec/

Local Development (Docker Compose)
Start dependencies
bash


Copy

Download
docker compose up -d
Run the service (dev mode)
bash


Copy

Download
./mvnw quarkus:dev

