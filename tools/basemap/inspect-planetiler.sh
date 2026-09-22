#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
cache="$root/build/planetiler-api"
mkdir -p "$cache"
image="openmaptiles/planetiler-openmaptiles@sha256:cdd536498df473ffe8bebf20ed62a89f05a01ba63d5ee7cb92a3581afcaaaa89"
if [[ ! -d "$cache/app" ]]; then
  container=$(docker create "$image")
  trap 'docker rm "$container" >/dev/null' EXIT
  docker cp "$container:/app" "$cache/app"
fi
javap -classpath "$cache/app/classes:$cache/app/libs/*" \
  org.openmaptiles.OpenMapTilesProfile \
  com.onthegomap.planetiler.reader.osm.OsmRelationInfo \
  com.onthegomap.planetiler.reader.osm.OsmElement\$Relation \
  com.onthegomap.planetiler.FeatureCollector\$Feature \
  com.onthegomap.planetiler.Planetiler
