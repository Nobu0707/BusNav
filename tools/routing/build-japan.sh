#!/usr/bin/env bash
set -euo pipefail
image=ghcr.io/valhalla/valhalla-scripted@sha256:42a9678526bd04558121968a6cffaefe9bbc483f6703deed94be1c1260879c95
pbf="${BUSNAV_JAPAN_PBF:-$HOME/busnav/osm/japan-latest.osm.pbf}"
runtime="${BUSNAV_JAPAN_RUNTIME:-$HOME/busnav/valhalla-japan}"
threads="${BUSNAV_BUILD_THREADS:-6}"
[[ -f "$pbf" ]] || { echo "Download and verify Japan PBF first" >&2; exit 1; }
[[ "$threads" =~ ^[1-9][0-9]*$ ]]
checksum="$(awk '{print $1}' "$pbf.md5.remote")"
[[ "$checksum" =~ ^[a-fA-F0-9]{32}$ ]]
printf '%s  %s\n' "$checksum" "$pbf" | md5sum --check
[[ ! -e "$runtime/custom_files/valhalla_tiles" ]] || { echo "Use a fresh runtime directory for each build" >&2; exit 1; }
mkdir -p "$runtime/custom_files"
# A hard link avoids a second multi-GB copy and is visible inside /custom_files.
ln "$pbf" "$runtime/custom_files/japan-latest.osm.pbf"
date -u +%FT%TZ > "$runtime/build-start.txt"
docker run --name busnav-valhalla-japan-build \
  --memory=24g --memory-swap=28g \
  -e server_threads="$threads" -e serve_tiles=False -e tile_urls= \
  -v "$runtime/custom_files:/custom_files" \
  "$image" > "$runtime/build.log" 2>&1
date -u +%FT%TZ > "$runtime/build-end.txt"
test -s "$runtime/custom_files/valhalla_tiles.tar"
grep 'Successfully built files' "$runtime/build.log"
find "$runtime/custom_files/valhalla_tiles" -name '*.gph' | wc -l
du -sh "$runtime/custom_files/valhalla_tiles" "$runtime/custom_files/valhalla_tiles.tar"
