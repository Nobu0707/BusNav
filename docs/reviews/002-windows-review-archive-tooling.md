# Review 002: Windows Review Archive Tooling

## 1. 実装概要

BusNav 専用の Windows 11 / PowerShell レビューアーカイブ基盤を追加した。検証ログ生成、差分中心の軽量版 ZIP、Git 追跡済みリポジトリを含む Full 版 ZIP、ZIP entry の自己検査、成功時だけの latest 更新を一連の処理として実装した。

## 2. 変更ファイル一覧

- `.gitignore`
- `scripts/review-common.ps1`
- `scripts/run-review-checks.ps1`
- `scripts/make-review-archive.ps1`
- `scripts/make-full-review-archive.ps1`
- `docs/review-archive.md`
- `docs/reviews/002-windows-review-archive-tooling.md`

## 3. SeasonRanking 版からの設計変更点

Paper/Java プラグイン固有の JAR、`plugin.yml`、Bukkit/Paper シグナル、SeasonRanking 固有パスを廃止した。Android 向けに Gradle Wrapper の test/lint/APK build、adb 接続確認、debug/androidTest APK inventory、位置情報・MapLibre・WebView・秘密情報候補のレビューシグナルへ置き換えた。

## 4. Windows/PowerShell 対応方針

Windows PowerShell 5.1 を下限とし、PowerShell 7+ でも動く構文だけを使用する。リポジトリルートは `$PSScriptRoot` から解決するため、任意のサブディレクトリから呼び出せる。Gradle は常に `gradlew.bat` を使用し、Bash、sed、awk、find、tar、grep へ依存しない。外部コマンドは .NET `Process` で標準出力・標準エラー・exit code を捕捉する。

## 5. run-review-checks.ps1 の仕様

HEAD の親を diff base として `git diff --check` を実行する。親がない場合は Git empty tree を使用する。Gradle の4チェックを個別ログ化し、adb が利用可能でオンライン端末がある場合だけ connected test を実行する。adb または端末がない場合は connected test を理由付きで `SKIP` とする。APK inventory は debug APK と androidTest APK の両方を要求する。

## 6. 通常レビューアーカイブの仕様

`meta/`、`diff/`、`file-diffs/`、`files/`、`working-file-diffs/`、`working-files/`、`checks/` を収録する。HEAD の変更ファイルは `git archive` でバイト列を保ったまま取得し、軽量版に不要なバイナリと禁止パスを除外する。未コミットの tracked/untracked ファイルは削除・変更せず、レビュー対象として安全なものだけをコピーする。

## 7. Full レビューアーカイブの仕様

`git archive HEAD` を一時 ZIP として展開し、禁止パスを再検査して `repo/` に収録する。`.git/`、untracked `local.properties`、ビルド生成物は自然に除外される。`gradle/wrapper/gradle-wrapper.jar` は Git 追跡済み bootstrap ファイルとして保持する。

## 8. 除外/機密保護方針

`.git/`、`.gradle/`、`build/`、`.idea/`、`local.properties`、`.env*`、secret/private ディレクトリ、credential/secret/password を示すファイル名、APK/AAB、class/dex、ログ、DB、既存アーカイブ、鍵・証明書・keystore を除外する。正式 ZIP 作成前後に entry 名を再走査し、禁止 entry があれば失敗させて latest を更新しない。

## 9. 実行した検証

- Windows PowerShell 5.1 parser による全スクリプト構文確認
- リポジトリのサブディレクトリからの起動
- `ExpectedHeadSubject` 不一致時の non-zero exit
- 必須ログ欠損時の non-zero exit
- 失敗時に ZIP が作成されないこと
- Gradle test/lint/assembleDebug/assembleDebugAndroidTest
- adb/connected test 判定
- 通常版および Full 版の生成
- ZIP entry の禁止ファイル検査
- Full 版の Gradle wrapper JAR 確認

## 10. 各コマンド結果

最終結果は実装コミット後に更新する。詳細ログは `build/review-checks/` と生成 ZIP の `checks/` に収録する。

## 11. 実際に生成したアーカイブ名

実装コミットに対する検証アーカイブ名を最終検証後に更新する。最終 HEAD の共有対象はリポジトリルートの `busnav-review-latest.zip` とする。

## 12. アーカイブ自己検査結果

最終検証後に entry 数、禁止 entry 数、必須構成、Gradle wrapper JAR の確認結果を更新する。

## 13. 未解決事項

実機または起動済み emulator がない場合、`connectedDebugAndroidTest` の実行結果は得られず `SKIP` となる。

## 14. 既知の制限

ファイル名とパスに基づく禁止ファイル検査は、ファイル本文に埋め込まれた未知の秘密情報を完全には検出できない。Android review signals は補助検索であり、最終的な内容確認はレビュー担当者が行う。軽量版の作業ツリースナップショットでは NUL byte を含むファイルをバイナリとして除外する。

## 15. 次フェーズへの推奨事項

Windows CI または専用 emulator を用意した段階で、同じスクリプトを CI から実行し、connected test を定期的に PASS させる。秘密情報検出要件が増えた場合は、リポジトリ固有 allowlist を持つ専用 scanner の導入を検討する。

## 16. コミットハッシュ

実装コミット作成後に更新する。レビュー文書自身の最終化コミットは循環参照を避け、実装コミットとは分離する。
