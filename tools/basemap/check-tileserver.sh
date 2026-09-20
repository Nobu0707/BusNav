#!/usr/bin/env bash
set -euo pipefail

base_url="${BUSNAV_BASEMAP_BASE_URL:-http://localhost:8080}"
retry_count="${BUSNAV_BASEMAP_CHECK_RETRIES:-20}"

for ((attempt=1; attempt<=retry_count; attempt++)); do
  if curl --fail --silent --show-error "$base_url/styles/busnav/style.json" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" -eq "$retry_count" ]]; then
    echo "TileServer style endpoint did not become ready: $base_url/styles/busnav/style.json" >&2
    exit 1
  fi
  sleep 1
done

curl --fail --silent --show-error "$base_url/data/chubu.json" >/dev/null
curl --fail --silent --show-error "$base_url/fonts.json" >/dev/null
curl --fail --silent --show-error \
  "$base_url/fonts/Klokantech%20Noto%20Sans%20CJK%20Regular/12288-12543.pbf" >/dev/null
curl --fail --silent --show-error \
  "$base_url/data/chubu/10/906/404.pbf" >/dev/null

echo "PASS style: $base_url/styles/busnav/style.json"
echo "PASS TileJSON: $base_url/data/chubu.json"
echo "PASS Japanese glyphs: Klokantech Noto Sans CJK Regular 12288-12543"
echo "PASS vector tile: Fuji area z10/906/404"
