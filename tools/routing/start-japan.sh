#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
image=ghcr.io/valhalla/valhalla-scripted@sha256:42a9678526bd04558121968a6cffaefe9bbc483f6703deed94be1c1260879c95
runtime="${BUSNAV_JAPAN_RUNTIME:-$HOME/busnav/valhalla-japan}"
action="${1:-preview}"
run_service() {
  docker run -d --name "$1" --restart unless-stopped -p "$2:8002" \
    -v "$runtime/custom_files:/custom_files:ro" --entrypoint valhalla_service \
    "$image" /custom_files/valhalla.json 6
}
health() {
  for ((attempt=0; attempt<30; attempt++)); do
    if curl --fail --silent "$1/status" >/dev/null; then return 0; fi
    sleep 1
  done
  return 1
}
case "$action" in
  preview)
    test -f "$runtime/build-end.txt"
    test -s "$runtime/custom_files/valhalla_tiles.tar"
    run_service busnav-valhalla-japan-preview 127.0.0.1:18002
    health http://127.0.0.1:18002
    python3 "$script_dir/check-japan.py" http://127.0.0.1:18002
    ;;
  activate)
    # Smoke before touching the current service. Keep its container and all data.
    python3 "$script_dir/check-japan.py" http://127.0.0.1:18002
    if docker inspect busnav-valhalla-chubu-backup >/dev/null 2>&1; then
      echo "Backup name already exists; inspect it before another switch" >&2; exit 1
    fi
    docker stop busnav-valhalla
    docker rename busnav-valhalla busnav-valhalla-chubu-backup
    if run_service busnav-valhalla 0.0.0.0:8002 && health http://127.0.0.1:8002 &&
        python3 "$script_dir/check-japan.py" http://127.0.0.1:8002; then
      docker stop busnav-valhalla-japan-preview
    else
      bash "$0" rollback
      exit 1
    fi
    ;;
  rollback)
    docker inspect busnav-valhalla-chubu-backup >/dev/null
    if docker inspect busnav-valhalla >/dev/null 2>&1; then
      docker stop busnav-valhalla
      docker rename busnav-valhalla "busnav-valhalla-japan-stopped-$(date +%s)"
    fi
    docker rename busnav-valhalla-chubu-backup busnav-valhalla
    docker start busnav-valhalla
    health http://127.0.0.1:8002
    ;;
  *) echo "Usage: start-japan.sh preview|activate|rollback" >&2; exit 1 ;;
esac
