# 所定経路ライブラリ（Phase008.5C）

## 操作

ナビ画面の「所定経路 • 一覧・保存」を押すとライブラリを開きます。既存の下部「ルート」は引き続きエディタへ直接入ります。エディタ地点一覧の「所定経路一覧」からも入れます。

- 「新しい所定経路を作成」→既存エディタで出発地・経由地・通過指定・目的地を登録→「経路探索」→候補の「このルートを使用」→ナビ画面の一覧・保存→「所定経路として保存」。名前必須、説明は任意。
- 「開く」は保存済み geometry / guidance の復元と全体表示です。再探索も Valhalla HTTP もありません。
- 「その他」→「ナビに使用」は同じ復元後にナビ開始状態にします。選択だけでは夜間・トンネルのナビ用 dark theme を開始しません。library/editor は LIGHT。
- 保存済み経路の「編集」および使用中経路の下部「ルート」は draft を作ります。元 DB は変更しません。「戻る」・システム Back は破棄します。
- 地点を変更した場合は「経路探索」で明示的に再計算し、候補を適用してから保存します。旧 geometry のまま保存する操作は UI / state holder / domain validation で拒否します。
- 「編集完了」または「上書き保存」で保存ダイアログ。別名保存・複製は新しい UUID。上書きは ID と作成日時を保持します。
- 名前変更は record.name のみを canonical な表示名として更新します。payload 内の計算時 route.name / plan.name は元の値を保持します。再計算しません。
- 削除確認は必須。現在使用中（選択中を含む）の ID は削除禁止（方針 A）。「経路の使用を終了」後に削除できます。

## Identity と責務

`PrescribedRouteRecord.id` は UUID、名前とは独立です。同名を許容し、plan ID / 計算 route ID と混同しません。
`NavigationUiState.activePrescribedRouteId` と `NavigationMode.PRESCRIBED` を導入。`FREE` は列挙値のみで Phase008.5D の実装待ちです。

`PrescribedRouteLibraryStateHolder` は一覧・操作の busy/error・current snapshot・draft を所有し、ViewModel で Activity recreation を越えます。
draft.id は sourcePrescribedRouteId に相当し、draft.routePlan は最後に候補確認した入力です。
現在の editor plan と一致しなければ保存不可。キャンセルでは DB を書きません。
`RouteCalculationStateHolder` へ保存した VehicleProfile を渡して明示的に再計算します。

呼び出しは `PrescribedRouteRepository` → record → `NavigationStateHolder.openPrescribedRoute`。
RoutingEngine は repository/library に依存注入されません。
経路スナップショット交換で既存の route generation、matcher、deviation、progress、高速案内キャッシュの再構築と全体表示要求を利用します。
同じ経路を再度開いても decode による新 snapshot で reset します。

## 永続化・スキーマ

Room 2.8.4、KSP 2.3.4、kotlinx.serialization 1.9.0。
AGP 9.4.0 / Kotlin 2.3.21 / minSdk23 の実ビルドで確認。
[Room リリース](https://developer.android.com/jetpack/androidx/releases/room#2.8.4)、
[KSP 移行ガイド](https://developer.android.com/build/migrate-to-ksp) を参照。

`BusNavContainer` は application context で DB を一度だけ生成します。MainActivity は repository だけを注入します。
DB 名は `prescribed-routes.db`、DB version は 1。
`prescribed_routes` の列：

| 列 | 用途 |
| --- | --- |
| id (TEXT PK) | stable UUID |
| name / description | 表示名・説明 |
| createdAtEpochMillis / updatedAtEpochMillis | wall clock。位置情報の elapsedRealtime とは別 |
| schemaVersion (INTEGER) | payload format version |
| payloadJson (TEXT) | geometry・guidance を含む原子的な snapshot |
| distanceMeters / startName / destinationName | 一覧専用の冗長メタデータ |

一覧は updatedAt 降順、同時刻は ID 昇順。巨大 payload を SELECT / deserialize しません。
一覧列は save 時に snapshot から同時に生成します。rename は payload に触れません。
save は Room transaction、rename/delete は原子的 SQL。UI 操作は busy で直列化し、古い draft の上書きは existingOnly により削除済み record を復活させません。
DB と codec は IO dispatcher。domain には保存 annotation を付与していません。

Payload V1：
- plan: id/name、全地点の id/type/座標/name
- route: id/name、全 geometry points、route points、metadata description/distance/duration/source
- guidance: null と空リストを区別、全 maneuver の index/type/instruction、事前事後音声案内、街路名、geometry begin/end、距離/時間
- highway signs: type/text/consecutiveCount
- vehicle: id/name、長さ/幅/高さ/重量/軸重（null も保持）

Double は kotlinx.serialization の round trip で exact equality を検証。間引き・再ルーティングはありません。
4,001 geometry points / 100 maneuvers の payload と DB reopen で全フィールド比較します。

## 検証・破損・migration

保存条件は名前、plan validation、START/DEST各1、地点順序・ID/type/座標一致、geometry>=2、案内 index 範囲、有限かつ正の車両寸法重量、非負・有限の距離時間です。

不正 JSON / domain 不正は Corrupt、未知の schemaVersion は Unsupported、削除競合は Missing。
画面に読込不能・更新必要・削除済みを表示し、データを自動削除しません。payload 全文をログに出しません。
一覧では schemaVersion を表示判定し、payload の破損検査は開く時に遅延します。

Room schema JSON を `app/schemas/` に追跡します。将来の DB 変更には明示的 Migration と保持検証が必要。
payload は version switch の codec を追加して移行します。`fallbackToDestructiveMigration` は使用禁止。

Debug / Release とも自動 sample load はありません。`createDevelopmentSampleRoute` は明示的な既存テスト用 fixture として残します。
保存ライブラリに開発 sample を自動登録しません。

## 制限と引継ぎ

保存済みライブラリはプロセス再起動後も残ります。最後に選択した経路・未保存 draft はプロセス終了時に自動復元しません。Activity recreation は ViewModel で保持します。
新規作成の車両設定 UI は既存の開発用車両条件を使用します。保存した条件は編集再計算で引き継ぎます。
地図背景のオフライン配布は対象外。保存経路線と案内の復元は通信不要です。
export/import、検索は未実装でUIにダミー項目を出しません。FREE navigationはPhase008.5Dで追加済みです。
Phase009の[Detour/Rejoin](detour-rejoin.md)はstable prescribedRouteId、元route snapshot、保存VehicleProfileを保持します。迂回をrecordに保存しません。renameはsessionを維持し、route内容/profile変更通知は旧sessionを無効化します。案内中編集・削除禁止の既存policyも維持します。
## Phase008.5D FREE との関係

NavigationMode.FREE が通常ナビとして有効になりました。保存済みレコードは PRESCRIBED と stable UUID を維持し、FREE 計算/開始/終了は repository に書き込みません。activePrescribedRouteId から mode を推測しません。
案内中にライブラリへ移る操作は終了確認を経由します。確認キャンセルで元の session を保持し、FREE 終了後に前の所定経路を勝手に復元しません。保存経路の「開く」は preview、「ナビに使用」は明示開始です。[Free Navigation](free-navigation.md) を参照してください。
