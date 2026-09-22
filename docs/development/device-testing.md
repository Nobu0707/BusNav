# 実機 Android / Emulator 開発接続

Debug のルート編集 →「開発接続設定」で、Valhalla サーバーと地図タイルサーバーの Base URL を入力する。
エミュレータの既定値は `http://10.0.2.2:8002` と `http://10.0.2.2:8080`。
**実機の `10.0.2.2` は開発PCを指さない。実機はPCのLAN IPを指定する。**

## Windows / WSL2 / Docker

PCと端末を同一LAN / Wi-Fiへ接続し、Windowsで `ipconfig` または
`Get-NetIPAddress -AddressFamily IPv4` を実行する。使用中のEthernet / Wi-FiのIPv4
（例 `192.168.1.100`）を使う。WSL仮想NICのアドレスと混同しない。

Docker内のサービスは `8002:8002`、`8080:8080` でホストへpublishする。
`docker ps` でbindとportを確認する。`127.0.0.1:8080` はPC自身のみのため、実機には届かない。
TileServerの既定bindは引き続きloopback。実機開発時は利用者が明示的に次を実行する。

```bash
BUSNAV_TILESERVER_BIND=0.0.0.0 ./tools/basemap/start-tileserver.sh
# 実機テスト後、loopbackへ戻す
./tools/basemap/start-tileserver.sh
```

Windows側は `Get-NetTCPConnection -State Listen -LocalPort 8002,8080` で確認できる。
TCP 8002 / 8080 が Windows Firewall に遮断される場合、信頼するプライベートLANに範囲を限定して許可する。
WSL2 NAT / mirrored networking、Docker Desktop / WSL内Dockerの違いにより、Windowsホストのforwardingや
Hyper-V firewallの確認が必要になる。アプリ・本タスクはFirewallやforwardingを自動変更しない。
Wi-Fiの端末間分離、VPN、サーバー内の127.0.0.1限定bindも確認する。

まず実機ブラウザから以下へ到達できることを確認する。

- `http://192.168.1.100:8002/status`
- `http://192.168.1.100:8080/styles/busnav/style.json`

## アプリ確認

1. Debug APKをインストールし、ルート編集 → 開発接続設定を開く。
2. Valhallaに `http://192.168.1.100:8002`、地図に `http://192.168.1.100:8080` を入力。
3. 地図地域をKantoまたはChubuから選択（既定Kanto）。各「接続テスト」で接続成功を確認して保存。テストは入力中の値を使い、保存はしない。
4. 地図の道路・日本語ラベルを確認し、道路付近を長押ししてSTART / DESTを配置する。
5. 経路探索、candidate route overlay、再探索、VIA / SHAPINGを確認する。
6. アプリを終了して再起動し、入力値と地図接続が維持されることを確認する。
7. 「デフォルトに戻す」でoverrideを削除する。実機はEmulatorの既定値に戻ると接続できなくなるため、
   続けて実機テストする場合はLAN IPを再保存する。

入力はhttp/https・host必須、userinfo/query/fragment禁止、pathは空または `/` のみ。
前後空白と末尾 `/` は正規化する。style pathは選択地域に応じ `/styles/busnav-kanto/style.json` または `/styles/busnav-chubu/style.json`。
接続結果は成功、HTTPステータスエラー、タイムアウト、DNS/host接続失敗、URL不正を表示し、生の応答を表示しない。
HTTP接続はDebugだけで許可する。

保存後、次のValhalla requestは新URLを使う。地図は現在のMapViewを保ちスタイルを再読込する。
経路候補、編集地点、カメラを保持し、fallbackからの復帰も可能。再起動は不要。
Preferences DataStoreは上書き値だけを保存し、reset後はBuildConfig既定値を使う。
[DataStore 1.2.1](https://developer.android.com/jetpack/androidx/releases/datastore#1.2.1) を固定使用する。

Releaseは開発設定入口を表示せず、開発用DataStoreを読み込まない。地図はfallback、routingは未設定なら
CONFIGURATIONを返す。必要なRelease用HTTPSは別property `busnavReleaseValhallaBaseUrl` を使用する。
`busnavValhallaBaseUrl` はDebug専用で、Releaseへ持ち越さない。

TODO: 将来、入口を Settings → Developer Options → 接続先へ移設する。
`developer/` のrepository/storeと `ui/settings/developer/` の画面を分離しているため、
MainActivityやMapControllerに保存形式の知識を持たせず移設できる。

## Phase004.5当時の実機結果（履歴）

Physical Android test: NOT RUN
Reason: no physical device attached

接続されたのは Pixel 8 AVD / Android 16 のみ。端末ID/serialは記録しない。

現在の全国routing・両地域地図の手順と実機検証は [環境ガイド](japan-routing-and-regional-basemaps.md) と [Review011](../reviews/011-kanto-japan-routing-environment.md) を参照してください。

## Phase010 traffic fixture

Debug の「規制」パネル内 Developer tools で None / Debug fixture と各 scenario を選択します。CLOSURE / ENTRANCE / ACCIDENT / ROADWORK / CONGESTION / FUTURE / EXPIRED / PARALLEL / MIXED は公開の合成関東座標で、実GPS履歴を使いません。Release には fixture 実装・操作 UI を含めません。情報源ラベルは「開発用交通情報」で、VICS/JARTIC live feed は未接続です。端末試験は TrafficFlowTest と TrafficOverlayTest を含む全 connected tests を実行します。USB 端末では runtime URL と adb reverse を使用しても構いません。端末IDをレビューへ保存しないでください。[Review013](../reviews/013-traffic-road-restrictions.md)。
