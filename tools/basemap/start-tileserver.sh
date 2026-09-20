#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export BUSNAV_BASEMAP_DATA_DIR="${BUSNAV_BASEMAP_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/busnav/basemap}"
mkdir -p "$BUSNAV_BASEMAP_DATA_DIR"
BUSNAV_BASEMAP_DATA_DIR="$(cd "$BUSNAV_BASEMAP_DATA_DIR" && pwd)"
export BUSNAV_BASEMAP_DATA_DIR

if [[ ! -f "$BUSNAV_BASEMAP_DATA_DIR/chubu.mbtiles" ]]; then
  echo "Missing $BUSNAV_BASEMAP_DATA_DIR/chubu.mbtiles; run generate-chubu-tiles.sh first." >&2
  exit 1
fi
if [[ ! -f "$BUSNAV_BASEMAP_DATA_DIR/fonts/Klokantech Noto Sans CJK Regular/12288-12543.pbf" ]]; then
  echo "Japanese glyphs are missing; run prepare-fonts.sh first." >&2
  exit 1
fi

python3 "$script_dir/prepare-regions.py" "$BUSNAV_BASEMAP_DATA_DIR"

docker compose -f "$script_dir/docker-compose.yml" up -d
bash "$script_dir/check-tileserver.sh"
