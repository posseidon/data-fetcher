#!/usr/bin/env bash
set -euo pipefail

host_bronze="$HOME/data/bronze"

if [[ -e "$host_bronze" && ! -d "$host_bronze" ]]; then
    echo "ERROR: $host_bronze exists but is not a directory." >&2
    echo "Remove or rename the file, then re-run this script." >&2
    exit 1
fi

mkdir -p "$host_bronze"