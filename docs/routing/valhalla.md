# Valhalla 接続

BusNav Phase 004 は Valhalla の `POST /route` を JSON で呼び出す。公式 API の location type、truck costing option、polyline6 は [Turn-by-Turn API](https://valhalla.github.io/valhalla/api/turn-by-turn/overview/) と [shape decoding](https://valhalla.github.io/valhalla/api/decoding/) を基準にした。

## endpoint

debug の既定値は `http://10.0.2.2:8002` である。Android Emulator の `10.0.2.2` はホスト側 localhost を指す。実機から同一 LAN のサーバーへ接続する場合はDebugアプリの「開発接続設定」でPCのLAN IPを保存する。[実機開発手順](../development/device-testing.md) を参照。ビルド時の既定値も以下で上書き可能。

```powershell
.\gradlew.bat assembleDebug -PbusnavValhallaBaseUrl=http://192.168.1.20:8002
```

release の既定値は空であり、未設定なら `CONFIGURATION` を返す。本番 endpoint は別property `busnavReleaseValhallaBaseUrl` のHTTPSを使う。Debug用propertyやDataStoreは取り込まない。cleartext 許可は debug manifest にだけ置き、main/release では許可しない。

## request

```json
{
  "locations": [{"lat":35.0,"lon":139.0,"type":"break"}],
  "costing":"truck",
  "costing_options":{"truck":{"height":3.5,"width":2.5,"length":12.0,"weight":16.0,"axle_load":10.0,"use_highways":0.8,"use_living_streets":0.1,"use_tracks":0.0,"exclude_unpaved":true}},
  "units":"kilometers",
  "shape_format":"polyline6",
  "directions_type":"none"
}
```

START/DESTINATION は `break`、VIA は `via`、SHAPING は `through` に変換し、入力順を維持する。安全制約を無効にする `ignore_restrictions`、`ignore_access`、`ignore_oneways`、`ignore_closures` は送信しない。

大型バスの物理通行可能性を優先し `truck` costing を使う。Valhalla の `bus` は bus/psv access に適する一方、Phase 004 は height/width/length/weight/axle_load を確実に反映する保守的方針を採った。ただし大型貨物車とバスの access 規則は同一ではなく、truck costing が本来バスの通れる道路を過剰回避する可能性がある。将来は bus access と寸法重量制約を組み合わせる hybrid strategy を検証する。

## response

`trip.summary.length`（km）、`trip.summary.time`（秒）、全 `trip.legs[].shape` だけを読む。shape は6桁精度で decode し、複数 leg の同一境界点を1点にまとめる。最低2点未満、欠落、不正 polyline/JSON は `INVALID_RESPONSE` とする。

## ローカル確認

Valhalla 3.8.3 は実装時点の公式最新 release である。Docker を使う場合も `latest` 固定ではなく、検証済み version/digest を pin する。Valhalla は OSM から構築した routing tiles が必要であり、本リポジトリは日本全域 PBF の取得や tile 構築を自動化しない。

```powershell
curl.exe http://localhost:8002/status
curl.exe -X POST http://localhost:8002/route -H "Content-Type: application/json" -d '{"locations":[{"lat":35.6812,"lon":139.7671},{"lat":35.6895,"lon":139.6917}],"costing":"truck","shape_format":"polyline6","directions_type":"none"}'
```

`/status` の version と tile 情報を記録し、対象地点の tiles がロード済みであることを確認する。公開 Valhalla endpoint を負荷試験や大量要求には使用しない。
