#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
data_dir="${BUSNAV_BASEMAP_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/busnav/basemap}"
image="${BUSNAV_PLANETILER_IMAGE:-openmaptiles/planetiler-openmaptiles@sha256:cdd536498df473ffe8bebf20ed62a89f05a01ba63d5ee7cb92a3581afcaaaa89}"
region="${1:?Usage: generate-region-tiles.sh kanto|chubu [PBF]}"
case "$region" in kanto|chubu) ;; *) echo "Unsupported region: $region" >&2; exit 1 ;; esac
output="$data_dir/$region.mbtiles"

if [[ -n "${2:-${BUSNAV_OSM_PBF:-}}" ]]; then
  pbf="${2:-$BUSNAV_OSM_PBF}"
else
  candidates=(
    "$HOME/busnav/osm/$region-latest.osm.pbf"
    "$HOME/busnav/valhalla/custom_files/$region-latest.osm.pbf"
    "$HOME/valhalla/custom_files/$region-latest.osm.pbf"
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
  echo "Region PBF not found. Pass the existing PBF as the second argument." >&2
  exit 1
fi
if [[ -e "$output" && "${BUSNAV_FORCE_REGENERATE:-0}" != "1" ]]; then
  echo "Refusing to overwrite $output. Set BUSNAV_FORCE_REGENERATE=1 to regenerate." >&2
  exit 1
fi

pbf="$(realpath "$pbf")"
mkdir -p "$data_dir"
# Build separately so a failed rebuild cannot truncate a served MBTiles file.
pending="$data_dir/$region.pending.mbtiles"
echo "Reusing OSM PBF: $pbf"
echo "Writing MBTiles: $output"
docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e JAVA_TOOL_OPTIONS="${BUSNAV_PLANETILER_JAVA_OPTIONS:--Xmx4g}" \
  -v "$(dirname "$pbf"):/input:ro" \
  -v "$data_dir:/data" \
  "$image" \
  --osm-path="/input/$(basename "$pbf")" \
  --output="/data/$region.pending.mbtiles" \
  --download \
  --force \
  --storage=mmap \
  --building-merge-z13=false \
  --exclude-layers=poi,housenumber,aerodrome_label

python3 "$script_dir/prepare-regions.py" --validate "$pending"
mv "$pending" "$output"

if [[ "${BUSNAV_SKIP_FONTS:-0}" != "1" ]]; then
  bash "$script_dir/prepare-fonts.sh"
fi

du -h "$output"
