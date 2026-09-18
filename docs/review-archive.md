# Review Archive Workflow

## Phase scope and portable ZIP entries

At the start of each Codex implementation task, record the current commit:

```powershell
$base = git rev-parse HEAD
```

Pass that value to every final check and archive command:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-review-checks.ps1 -BaseRef $base
powershell -ExecutionPolicy Bypass -File scripts\make-review-archive.ps1 -BaseRef $base -SkipChecks
powershell -ExecutionPolicy Bypass -File scripts\make-full-review-archive.ps1 -BaseRef $base -SkipChecks
```

`-BaseRef` defines the whole review phase, so a lightweight archive remains
complete when implementation, fixes, and documentation use multiple commits.
When omitted, the scripts retain the previous behavior of using `HEAD^` (or
Git's empty tree for an initial commit).

The lightweight archive stores `BaseRef..HEAD` metadata, a
`diff/phase-full-diff.txt`, per-file diffs, and safe HEAD-side snapshots.
`diff/head-full-diff.txt` is retained as a compatibility alias and contains
the same phase-range diff. Full archives still store the complete tracked
`HEAD` tree under `repo/`.

All ZIP entry names use `/`, including archives produced on Windows. Archive
self-checks reject raw entry names containing `\`. `-SkipChecks` accepts
BusNav のレビュー資料は Windows 11 上の PowerShell で生成する。通常のレビューでは差分中心の軽量版を共有し、差分だけで判断できない場合に限り、Git 追跡済みのリポジトリ一式を含む Full 版を共有する。

## 通常の開発サイクル

1. ChatGPT が仕様と実装プロンプトを作成する。
2. Codex が実装する。
3. Codex が test、lint、build を実行する。
4. Codex が実装をコミットする。
5. `run-review-checks.ps1` で最終 HEAD を検証する。
6. `make-review-archive.ps1` で軽量版を生成する。
7. `busnav-review-latest.zip` を ChatGPT へ渡す。
8. ChatGPT がアーカイブを査読する。
9. 必要なら修正プロンプトを作成する。
10. Codex が修正し、検証から繰り返す。

## 検証ログ

`scripts/run-review-checks.ps1` は、常にリポジトリルートを基準に次を実行し、`build/review-checks/` に再現可能なログを保存する。

- `git diff --check <HEAD の親> HEAD`
- `.\gradlew.bat test --console=plain`
- `.\gradlew.bat lint --console=plain`
- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat assembleDebugAndroidTest --console=plain`
- `adb devices` と、オンライン端末がある場合だけ `.\gradlew.bat connectedDebugAndroidTest --console=plain`
- debug APK と androidTest APK の存在、サイズ、更新日時の一覧

adb がない場合、またはオンライン端末がない場合、connected test は理由付きの `SKIP` となる。それ以外の必須チェックが失敗した場合はスクリプト全体が失敗する。

## 通常版

`scripts/make-review-archive.ps1` は、HEAD の差分、変更ファイルの安全なスナップショット、未コミット差分、Git メタ情報、検証ログ、Android レビューシグナルを収録する。APK や Gradle の生成物は含めない。

Windows PowerShell 5.1:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\make-review-archive.ps1
powershell -ExecutionPolicy Bypass -File scripts\make-review-archive.ps1 -ExpectedHeadSubject "chore: add Windows review archive tooling"
```

PowerShell 7+:

```powershell
pwsh -File scripts\make-review-archive.ps1
```

既存のチェックログを再利用する場合は `-SkipChecks` を付けられる。ただし、必須ログが揃い、すべての必須結果が `PASS`、connected test が `PASS` または `SKIP` でなければ生成しない。

成功時に次を生成する。

- `busnav-review-<HEAD short SHA>-<UTC timestamp>.zip`
- `busnav-review-latest.zip`

## Full 版

`scripts/make-full-review-archive.ps1` は `git archive HEAD` を使用し、Git 追跡済みファイルを `repo/` に収録する。作業ツリーの untracked ファイル、`.git/`、ビルド生成物は取得元の時点で含まれない。Gradle bootstrap に必要な `gradle/wrapper/gradle-wrapper.jar` は例外として保持する。

```powershell
powershell -ExecutionPolicy Bypass -File scripts\make-full-review-archive.ps1
pwsh -File scripts\make-full-review-archive.ps1 -SkipChecks
```

成功時に次を生成する。

- `busnav-full-review-<HEAD short SHA>-<UTC timestamp>.zip`
- `busnav-full-review-latest.zip`

## ZIP の自己検査

各スクリプトは ZIP entry を .NET で読み返し、必須ディレクトリと禁止ファイルを検査する。検査に失敗した ZIP は正式名へ移動せず、`latest.zip` も更新しない。成功時の自己検査結果は `meta/archive-self-check.txt` に記録する。

## セキュリティ

アーカイブには `local.properties`、`.env`、keystore、秘密鍵、credential/secret/password を示すファイル名、APK/AAB、データベース、ログ、ZIP、`build/`、`.gradle/`、`.idea/`、`.git/` を含めない。軽量版の変更ファイルスナップショットではバイナリも除外し、除外理由を `meta/excluded-files.txt` に記録する。

`checks/android-review-signals.txt` は位置情報権限、MapLibre、WebView、平文 HTTP、秘密情報を示す語、TODO/FIXME などを検索するレビュー補助であり、文字列が見つかっただけでは失敗にしない。
