# Review011b — Phase008.5B Route Editor UX Refresh

## Baseline / scope

- BASE_SHA: 5492aae5e5a4edd2a66a0bccbd9f96ee681211e3
- 開始時に git status/log/HEAD/diff（staged含む）を確認。tracked差分なし。
- 既存 untracked .vscode/ と gradle/gradle-daemon-jvm.properties は保持し、今回のcommitには含めない。
- Phase008.5A COMPLETE の後続。Phase008 matching/guidance/deviation と既存テーマを維持。
- Phase009 Detour/Rejoin・自動再探索は実装しない。

## Implementation

編集開始は active geometry → candidate geometry → plan points の優先順でfitする。
対象なしは保存cameraを復元し、1地点はzoom15で中心へ。要求IDをViewModel/state holderに保持し、
native style・layout・sheet寸法がそろってから実行／消費する。シート開閉・地点追加では再fitしない。
探索成功ごとにcandidateをfitする。探索ボタンでPARTIALへ戻し、そのsheet寸法が通知されるまでfitを待つ。

非対称bounds paddingはsheetと登録controlsを含む。MapLibreのbounds更新はcamera.paddingを保持するため、
fit後は画面中心の座標を保ってpaddingをゼロへ戻す。これにより中央カーソル、pan/zoom、回転復元が
以前のsheet寸法に依存しない。登録はクリック時のnative projectionによるMapView中央pixelの座標そのもの。
表示座標だけを丸め、登録座標をsnap/roundしない。長押しは補助として維持する。

表示名は出発地／目的地／経由地／通過指定。「この付近を通るよう経路を調整」を表示。
縮小中も「経由地を登録」等のボタンで選択種類が分かる。

シートはPEEK/PARTIAL/EXPANDEDの3状態。48dpのhandleで上下drag・tapできる。
完全には隠さず、PEEKでもhandleと探索／完了footerを残す。種類・説明・結果・地点は同じLazyColumn。
一覧端の未消費scrollをNestedScrollでsheetへ渡す。地図のpan領域にsheetのgestureを置かない。
地点は種類／任意名称／補助座標、48dp以上の上下・切替・削除操作。端点削除の確認を維持。
帰属表示はsheetに隠れない位置へ移動する。

RoutePlanEditorViewModelにplan・選択種類・sheet・camera・未消費intentを保持。
探索もRouteCalculationViewModelへ移し、Activity再生成でcandidateと進行中探索を維持する。
戻る／完了は既存どおり探索をcancelする。新たなDB永続化はない。

## Validation

| Check | Result |
| --- | --- |
| Unit (camera priority / exact cursor / labels / sheet / request consumption) | PASS — 全224件、failure/error 0 |
| Existing routing / matching / guidance / deviation unit regression | PASS |
| Compose (Japanese labels / fixed footer / 20 points / swipe / portrait-landscape) | PASS |
| Native (active fit / empty preserve / one-point fit / pan / exact cursor / candidate / overlay) | PASS |
| Rotation (portrait-landscape / recreation / camera / selected type / plan / sheet) | PASS |
| Full connected tests — Emulator / Physical | PASS — 52件ずつ、failure/error/skip 0 |
| test / lint / assembleDebug / assembleRelease / assembleDebugAndroidTest | PASS — lint 0 errors / 12 warnings / 1 hint |
| git diff --check / final HEAD review checks / archive self-check | diff PASS。最終HEAD検証・ZIP自己検査の結果はarchive内 checks/metaを参照 |

## Device smoke / visual review

Pixel 8 Android16 emulator と SOG06 Android14実機を使用。既存のローカルTileServer/Valhallaへ
USB reverseを設定し、runtime test引数で接続先を渡した。端末識別子・ローカル設定はcommitしない。

EditorMapRuntimeTest はKanto地図で route load → editor fit → native pan → 中央登録 →
経由地／通過指定を含む14地点 → 一覧末尾へscroll → 下swipeでPEEK → 上drag → calculate → finishを操作する。
このテストのroute/candidateは決定的な開発fixtureであり、道路沿いの実経路検証は別の
ValhallaUiRuntimeSmokeTestで東京・埼玉・関東中部間と短／長経路の繰り返し探索を行う。
20地点の共通scrollとfooterの不動はComposeテストで比較する。

build/phase0085b-smoke/ に両端末の画像を取得し、地図全体fit、中央カーソル、長い一覧、
PEEKのfooter、candidate表示を目視確認した。操作はInstrumentationによる自動操作で、
人が端末を手で操作する独立したmanual testや実走行ではない。

## Findings addressed

- 編集MapViewへの保存camera受け渡し漏れを修正。
- テストが遷移前MapViewを拾う競合を修正し、新MapViewの生成を待つ。
- camera move通知は非同期のため、回転前snapshotは通知完了を待って検証。
- bounds fitの持続paddingを除去してviewport中心を保持。native testでpadding=0とfit範囲を検証。
- Native GestureDetectorの操作は実時刻のMotionEventで実行。
- 物理端末の一時USB切断後、再接続とserver転送を確認して再実行。
- 一時診断ログは撤去。生成画像・APK・ログ・環境設定はcommit/archive対象外。

## Limits / delivery

process death復元、DB、reverse geocoding、snap-to-road、地点名称編集、Phase009は対象外。
横画面では画面高に応じて表示できる行数が減るが、共通scrollと固定footerは維持する。

UI仕様: [route-plan-editor.md](../ui/route-plan-editor.md)
最終HEADをBASE_SHAからrun-review-checks.ps1で検証後、review/full archiveを生成する。
各archive metadataにBASE・最終HEAD・検証結果・self-checkを保存する。

Phase008.5B: COMPLETE。最終HEADの通常レビュー検証とarchive self-checkの成功を提出条件とする。
