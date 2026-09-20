#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export BUSNAV_BASEMAP_DATA_DIR="${BUSNAV_BASEMAP_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/busnav/basemap}"
docker compose -f "$script_dir/docker-compose.yml" down
