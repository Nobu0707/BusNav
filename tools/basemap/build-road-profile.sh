#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
cache="$root/build/planetiler-api"
bash "$root/tools/basemap/inspect-planetiler.sh" >/dev/null
mkdir -p "$root/build/road-profile"
javac --release 21 -encoding UTF-8 -classpath "$cache/app/classes:$cache/app/libs/*" \
  -d "$root/build/road-profile" \
  "$root/app/src/main/java/net/nobu0707/busnav/map/JapaneseRoadNetwork.java" \
  "$root/tools/basemap/profile/BusNavProfile.java"
