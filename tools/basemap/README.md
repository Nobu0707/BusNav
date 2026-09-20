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
