#!/usr/bin/env bash
set -euo pipefail

region="${1:?Usage: download-osm.sh japan|kanto}"
case "$region" in
  japan) url=https://download.geofabrik.de/asia/japan-latest.osm.pbf ;;
  kanto) url=https://download.geofabrik.de/asia/japan/kanto-latest.osm.pbf ;;
  *) echo "Unsupported region: $region" >&2; exit 1 ;;
esac
data_dir="${BUSNAV_OSM_DATA_DIR:-$HOME/busnav/osm}"
mkdir -p "$data_dir"
cd "$data_dir"
name="$region-latest.osm.pbf"
curl -L --fail --silent --show-error "$url.md5" -o "$name.md5.remote"
# Geofabrik may name the dated extract in its checksum file.
checksum="$(awk '{print $1}' "$name.md5.remote")"
[[ "$checksum" =~ ^[a-fA-F0-9]{32}$ ]]
if [[ -f "$name" ]]; then
  printf '%s  %s\n' "$checksum" "$name" | md5sum --check
  echo "Reusing verified $name"
  exit 0
fi
curl -L --fail --show-error --silent --retry 3 --continue-at - --remote-time \
  "$url" -o "$name.part"
printf '%s  %s\n' "$checksum" "$name.part" | md5sum --check
mv "$name.part" "$name"
date -u +%FT%TZ > "$name.downloaded-at"
stat --printf='%n %s bytes modified %y\n' "$name"
file "$name"
