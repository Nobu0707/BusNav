#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
bash "$root/tools/basemap/build-road-profile.sh"
classpath="$root/build/road-profile:$root/build/planetiler-api/app/classes:$root/build/planetiler-api/app/libs/*"
javac --release 21 -encoding UTF-8 -classpath "$classpath" -d "$root/build/road-profile" \
  "$root/tools/basemap/profile/BusNavProfileTest.java"
java -classpath "$classpath" net.nobu0707.busnav.tiles.BusNavProfileTest
