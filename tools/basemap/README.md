# BusNav local basemap tooling

Small, tracked configuration for a local OpenMapTiles-compatible development basemap. Large inputs and outputs live outside this repository.

The default runtime directory is `~/.local/share/busnav/basemap`. Override it with `BUSNAV_BASEMAP_DATA_DIR`.

## Quick start in WSL2

```bash
cd /mnt/c/projects/BusNav
export BUSNAV_OSM_PBF="$HOME/busnav/valhalla/custom_files/chubu-latest.osm.pbf"
./tools/basemap/generate-chubu-tiles.sh
./tools/basemap/start-tileserver.sh
```

The generator uses the Planetiler 0.10.2 image pinned by its verified SHA-256 digest (see [reproducibility table](../../docs/development/basemap.md)), reuses the mounted PBF read-only, and writes `chubu.mbtiles` outside Git. Planetiler downloads only its separate Natural Earth, water polygon, lake centerline, and tile-weight support data. It does not download another Chubu PBF.

`prepare-fonts.sh` verifies a commit-pinned archive with SHA-256, then copies the OFL/Apache-licensed OpenMapTiles Noto Sans glyph set into the runtime directory. `start-tileserver.sh` requires the Japanese glyph range before starting `maptiler/tileserver-gl:v5.6.0`.

Endpoints:

- Host style: `http://localhost:8080/styles/busnav/style.json`
- Host TileJSON: `http://localhost:8080/data/chubu.json`
- Emulator style: `http://10.0.2.2:8080/styles/busnav/style.json`
- Valhalla remains separate at `http://10.0.2.2:8002`

Stop with `./tools/basemap/stop-tileserver.sh`. Verify style, TileJSON, a Japanese glyph range, and a Fuji-area vector tile with `./tools/basemap/check-tileserver.sh`.

## Phase 004.5.1 regional basemaps

全国Valhallaのrouting coverageとKanto/Chubuの表示coverageは独立しています。BasemapRegionをDeveloper ConnectionsのDataStoreへ保存し、BasemapConfigが地域別style URLを生成します。MapViewと経路状態を保持したままstyleをreloadします。単一style templateからruntimeで2地域のstyleを生成し、日本語glyphと旧Chubu成果物を維持します。[構築・切替・rollback手順](../../docs/development/japan-routing-and-regional-basemaps.md)。

## Phase008.5A Light / Dark

Tracked templates are style/busnav.json (Dark) and style/busnav-light.json (Light).
prepare-regions.py derives both for Kanto and Chubu using the same MBTiles and Japanese fonts.
Endpoints: /styles/busnav-{kanto|chubu}[-light]/style.json; legacy busnav[-light] aliases Chubu.
After regenerating styles, restart the existing TileServer process to load new config entries.
No tile rebuild is required. transportation.brunnel is present in both current regional TileJSON schemas.
See [presentation policy](../../docs/theme-map-presentation.md).
