#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
data_dir="${BUSNAV_BASEMAP_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/busnav/basemap}"
image="${BUSNAV_PLANETILER_IMAGE:-openmaptiles/planetiler-openmaptiles:latest}"
output="$data_dir/chubu.mbtiles"

if [[ -n "${BUSNAV_OSM_PBF:-}" ]]; then
  pbf="$BUSNAV_OSM_PBF"
else
  candidates=(
    "$HOME/busnav/valhalla/custom_files/chubu-latest.osm.pbf"
    "$HOME/valhalla/custom_files/chubu-latest.osm.pbf"
  )
  pbf=""
  for candidate in "${candidates[@]}"; do
    if [[ -f "$candidate" ]]; then
      pbf="$candidate"
      break
    fi
  done
fi

if [[ -z "$pbf" || ! -f "$pbf" ]]; then
  echo "Chubu PBF not found. Set BUSNAV_OSM_PBF to the existing chubu-latest.osm.pbf." >&2
  exit 1
fi
if [[ -e "$output" && "${BUSNAV_FORCE_REGENERATE:-0}" != "1" ]]; then
  echo "Refusing to overwrite $output. Set BUSNAV_FORCE_REGENERATE=1 to regenerate." >&2
  exit 1
fi

mkdir -p "$data_dir"
echo "Reusing OSM PBF: $pbf"
echo "Writing MBTiles: $output"
docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e JAVA_TOOL_OPTIONS="${BUSNAV_PLANETILER_JAVA_OPTIONS:--Xmx4g}" \
  -v "$(dirname "$pbf"):/input:ro" \
  -v "$data_dir:/data" \
  "$image" \
  --osm-path="/input/$(basename "$pbf")" \
  --output=/data/chubu.mbtiles \
  --download \
  --force \
  --storage=mmap \
  --building-merge-z13=false \
  --exclude-layers=poi,housenumber,aerodrome_label

if [[ "${BUSNAV_SKIP_FONTS:-0}" != "1" ]]; then
  "$script_dir/prepare-fonts.sh"
fi

du -h "$output"
