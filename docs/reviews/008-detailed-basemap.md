# Review 008: Detailed local vector basemap

## 1. Phase overview

Phase 004.5 として、既存の中部地方 OSM PBF から OpenMapTiles 互換 MBTiles を生成し、ローカル TileServer GL を介して MapLibre Android に詳細ベースマップを表示する開発基盤を追加した。Phase 005 の maneuver / Navigation Guidance は実装していない。

## 2. BASE_SHA

`7da14df7e853956410d314b30fb004618da035e8`

## 3. Current problem

従来のデモスタイルは海岸線中心で、道路、道路名、高速道路、IC/JCT 相当、地名を確認しながら START / DEST / VIA / SHAPING を配置できなかった。Valhalla が経路を返しても、入力地点と道路網の関係を画面上で検証できない状態だった。

## 4. Architecture

表示用タイルと routing graph を分離した。既存 OSM PBF は Valhalla と Planetiler の双方が読むが、Android は Valhalla の `/route` と TileServer GL の style / TileJSON / vector tile / glyph を別 endpoint で利用する。BusNav backend や独自道路情報は本 Phase の対象外である。

## 5. OSM PBF reuse

既存の `~/busnav/valhalla/custom_files/chubu-latest.osm.pbf`（510,262,495 bytes）を確認し、そのまま再利用した。中部地方 PBF の重複 download や repository 内への copy は行っていない。`BUSNAV_OSM_PBF` で明示 path を指定でき、未指定時だけ既知 path を probe する。

## 6. Tile generation method

`openmaptiles/planetiler-openmaptiles:latest` を固定入口として OpenMapTiles profile を実行し、`chubu.mbtiles` を生成した。実行時 Planetiler は 0.10.2（git `0e5588c4a6e8c29a270a33afe8df62027d889604`）、image digest は `sha256:cdd536498df473ffe8bebf20ed62a89f05a01ba63d5ee7cb92a3581afcaaaa89`。生成物は 301,981,696 bytes、z0–14、61,783 tiles、16,630,319 features で、repository 外の `~/.local/share/busnav/basemap/chubu.mbtiles` に置いた。

## 7. TileServer GL

`maptiler/tileserver-gl:v5.6.0`（digest `sha256:3a9ccdb24820b6814c8119bcc8a4376c39867cb0ffe69d62919ef898b90c2427`）を Docker Compose で起動する。MBTiles、style、config、glyph を read-only mount し、host は `127.0.0.1:8080`、container は 8080 を使用する。`check-tileserver.sh` は style、TileJSON、日本語 glyph、富士周辺の既知 vector tile を検証する。

## 8. Emulator network path

Windows / WSL host は `http://localhost:8080`、Android Emulator は `http://10.0.2.2:8080` を使用する。TileServer の Host header 対応により、style 内 resource URL も Emulator から到達可能な `10.0.2.2` になる。Valhalla の `10.0.2.2:8002` とは port と責務を分離した。

## 9. MapLibre integration

MapLibre Native Android 13.6.1 の既存 `MapScreen` / `MapController` に `BasemapController` を統合した。debug の `BASEMAP_STYLE_URL` は既定で `http://10.0.2.2:8080/styles/busnav/style.json`、Gradle property `busnavBasemapStyleUrl` で上書きできる。release の既定値は空文字で、localhost を配布 build に残さない。

## 10. Style source

BusNav 管理の Mapbox Style JSON は `tools/basemap/style/busnav.json` に置いた。vector source は TileServer の `chubu` data endpoint を使用し、style resource は TileServer による request host 展開を利用する。API key や secret は含まない。

## 11. Glyph / sprite handling

日本語 glyph は OpenMapTiles fonts の `Klokantech Noto Sans CJK Regular` を `prepare-fonts.sh` で repository 外に取得し、TileServer の `/fonts/{fontstack}/{range}.pbf` から配信する。`12288-12543.pbf`（148,239 bytes）を health check で確認した。現 style は sprite icon を使用しないため sprite dependency はない。

## 12. Japanese labels

道路名・地名・junction label は `name:ja`、`name`、`name:latin` の順で coalesce する。Emulator の目視確認で「長野市」「名古屋市」などの日本語地名を確認した。

## 13. Displayed road classes

motorway、motorway link / ramp、trunk、primary、secondary、tertiary、residential、service を表示する。加えて road labels、route/ref numbers、motorway junction 相当、tunnel、bridge、railway、river/water、coastline、city/town/place labels、high zoom の building を含む。

## 14. Road hierarchy

motorway を最も強く、trunk / primary、secondary / tertiary、residential / service の順に線幅・色・表示 zoom を調整した。低 zoom は motorway / trunk / major place、中 zoom は primary / secondary / ref / town / rail / water、高 zoom は local road / road name / building / junction detail を主対象とする。

## 15. Dark theme

既存の BusNav dark theme に合わせた low-glare 配色とし、海・陸・建物の彩度を抑えた。道路は階層を判別できる contrast を保ちつつ、candidate / active route overlay より目立たない。

## 16. Route overlay z-order

`OverlayLayerOrder` に BusNav layer ID を集約し、planned route、candidate / active route、START、VIA、SHAPING、DEST、vehicle を basemap の後に追加する。style load 完了時に overlay source / layer を再登録するため、詳細 style から fallback への reload 後も overlay が復元される。

## 17. Fallback behavior

詳細 style が初回 load に失敗した場合、`asset://basemap/fallback-style.json` の dark plain background へ一度だけ切り替える。詳細 style 成功後の tile/source error では破壊的な style reload を行わず、現在の overlay を維持したまま unavailable 状態を通知する。fallback 自体の失敗だけ既存 map error callback へ通知する。

## 18. Basemap state

`BasemapState` は `LOADING`、`AVAILABLE`、`UNAVAILABLE`。server 未接続または release fallback 時は「詳細地図サーバー未接続」を小さく表示する。routing state とは独立しており、basemap unavailable を Valhalla failure として扱わない。

## 19. Attribution

map 左下に `© OpenMapTiles © OpenStreetMap contributors` を常時表示する。MapLibre の attribution icon のみへ依存せず、ユーザーがデータ帰属を直接確認できる。

## 20. License notes

OSM data は ODbL と attribution 要件、OpenMapTiles schema / style は各 license と attribution、Planetiler / planetiler-openmaptiles / TileServer GL / OpenMapTiles fonts は各 upstream license に従う。詳細と upstream link は `docs/development/basemap.md` と `tools/basemap/README.md` に記録した。font binary は Git に含めない。

## 21. Generated-file Git exclusion

`.gitignore` に `*.osm.pbf`、`*.mbtiles`、`*.pmtiles` と basemap runtime / Planetiler work / generated style cache を追加した。review archive の file filter にも同拡張子と Planetiler layerstats を追加し、PBF、tiles、fonts、cache、APK、build、logcat、secret が archive に混入しない構成にした。

## 22. Deterministic tests

local server 不要の unit test で style URL config、debug/release 判定、state transition、fallback、URL sanitization、layer order、attribution を検証する。MapLibre native pixel rendering は unit test に持ち込まない。最終 `test` は 95 tests、failures 0、errors 0 で PASS。

## 23. Connected tests

Compose / instrumentation では map container、route editor overlay、unavailable message、fallback 中の editor 継続を検証する。`BasemapRuntimeSmokeTest` は server 到達時だけ style、known vector tile、日本語 glyph、Emulator 上の `AVAILABLE` を検証する。connected suite は 17 tests。

## 24. TileServer OFF smoke

TileServer 停止状態で `connectedDebugAndroidTest` を実行し、17 tests、failures 0、errors 0 を確認した。live basemap probe は `SKIP: local TileServer unavailable` を記録して return し、通常 suite と fallback UX は PASS した。

## 25. TileServer ON smoke

TileServer 起動状態で style、TileJSON、glyph、`/data/chubu/10/906/404.pbf` の health check が PASS。Pixel 8 AVD（Android 16）の最終 connected suite は 17 tests、failures 0、errors 0、skipped 0。live basemap smoke を実行し、style / tile / glyph 到達と `BasemapState.AVAILABLE` を確認した。

## 26. Road-point-selection manual smoke

Pixel 8 AVD（Android 16）で中部地方を表示し、道路と日本語地名を見ながら START / DEST / VIA / SHAPING の4種類を長押し追加できた。各 marker は basemap より前面に表示された。既知の通行可能区間を START / DEST に設定した別試行では Valhalla が 42.1 km、推定1時間27分の candidate route を返し、道路・ラベルの上に候補線が表示された。自動 live routing smoke は短距離・長距離を含む7回の計算を通しており、最低3パターンの route test 要件も満たす。一時 screenshot は `build/manual-evidence/` のみに保存し Git へ含めない。

## 27. Performance

3D building、terrain、hillshade、過剰な POI は入れていない。道路確認に必要な layer と zoom gate を優先し、style は低～中価格帯 Android 端末を意識した。生成 runtime は auxiliary data / fonts を含め約1.7 GB、配信用 MBTiles は約302 MB。

## 28. Known limitations

本構成は開発用 HTTP server で、本番 CDN / authentication / cache policy は未実装。endpoint snapping、reverse geocoding、POI search、全国 tile、PMTiles offline、route-corridor cache、独自大型車道路 DB、交通実績、VICS、map matching は対象外。OSM の道路属性・名称・大型車制限の欠損や誤りは残り、実車運行の安全性を保証しない。

## 29. Phase 005 handoff

Phase 005 は既存 Valhalla response に maneuver / road name / turn direction / distance / route progress を追加し、今回の詳細地図上で Navigation Guidance を検証する。Phase 004.5 では maneuver、JCT 模式図、音声案内を実装していない。

## 30. Phase 007 handoff

将来の production / offline phase では、development の TileServer GL + MBTiles から production tile server / CDN、または MapLibre 13.6.1 の PMTiles source と route-corridor cache を比較する。`BasemapConfig` の `LOCAL_DEV` / `REMOTE_STYLE` / `FALLBACK` 境界をその切替点として使う。

## 31. Commit SHA

- `8946d867b77856f6a528b960647edfcefe1e86d8` — `feat: add detailed local vector basemap`
- 本 Review008 の確定 commit と最終 HEAD は review archive の `meta/review-info.txt` を正とする。

## 32. Archive names

生成予定の lightweight alias は `busnav-review-latest.zip`、full alias は `busnav-full-review-latest.zip`。immutable archive 名、HEAD、BASE_SHA、self-check は各 archive の `meta/review-info.txt` と `meta/archive-self-check.txt` を正とする。
