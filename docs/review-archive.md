# Review Archive Workflow

## Phase scope and portable ZIP entries

At the start of each Codex implementation task, record the current commit:

```powershell
$base = git rev-parse HEAD
```

Pass that value to archive commands after final validation:

```powershell
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
self-checks reject raw entry names containing `\`.
BusNav のレビュー資料は Windows 11 上の PowerShell で生成する。通常のレビューでは差分中心の軽量版を共有し、差分だけで判断できない場合に限り、Git 追跡済みのリポジトリ一式を含む Full 版を共有する。

## 通常の開発サイクル

1. ChatGPT が仕様と実装プロンプトを作成する。
2. Codex が実装する。
3. Codex が最終コードに対し test、lint、各 build をそれぞれ1 successful run、connected tests を Emulator / Physical 各1 successful run まで実行し、ログを保存する。
4. Codex が実装をコミットする。
5. Review に端末別 attempts / result / test count を記録し、検証済みコードと最終 HEAD の対応を summary に記録する。docs-only commit の場合はコードに差分がないことを確認し、成功済みテストを再実行しない。
6. `make-review-archive.ps1 -BaseRef $base -SkipChecks` で軽量版を生成する。Full 版も同じ BaseRef と -SkipChecks を使う。
7. `busnav-review-latest.zip` を ChatGPT へ渡す。
8. ChatGPT がアーカイブを査読する。
9. 必要なら修正プロンプトを作成する。
10. Codex が修正し、検証から繰り返す。

## Test execution と archive generation の分離（今後の全 Phase）

Review archive 生成は test execution と分離する。connected tests は最終実装検証で
1 successful run/device。archive generation では rerun しない。
PASS 後の再実行は行わず、FAIL / timeout / install failure / device disconnect /
infrastructure failure またはその原因修正時だけ、必要な範囲を再実行する。

既に個別に検証済みなら `run-review-checks.ps1` を後から実行しない。このスクリプトは
static checks と adb / connectedDebugAndroidTest を実行するため、証跡収集だけの用途には使えない。
両 archive scripts は `-SkipChecks` を省略するとこの runner を呼ぶ。
`-SkipChecks` は既存ログを検査して使用し、テストを呼ばない。
ZIP self-check と Android review signals はファイル / Git の読み取りだけで、adb を呼ばない。

`build/review-checks/` には各 command の結果・時刻・exit code と required logs を保存する。
summary の HEAD SHA / Diff base は生成対象と一致させる。docs-only commit に証跡を引き継ぐ場合は、
tested code の SHA または tree hash とコード差分がないことを明記する。
未実施・失敗・古い結果を PASS に置き換えない。Review には Emulator / Physical の
attempts・final result・test count と `Archive connected tests rerun: NO` を記載する。

## 検証ログ

`scripts/run-review-checks.ps1` は、常にリポジトリルートを基準に次を実行し、`build/review-checks/` に再現可能なログを保存する。

- `git diff --check <BaseRef（省略時は HEAD の親）> HEAD`
- `.\gradlew.bat test --console=plain`
- `.\gradlew.bat lint --console=plain`
- `.\gradlew.bat assembleDebug --console=plain`
- `.\gradlew.bat assembleRelease --console=plain`
- `.\gradlew.bat assembleDebugAndroidTest --console=plain`
- `adb devices` と、オンライン端末がある場合だけ `.\gradlew.bat connectedDebugAndroidTest --console=plain`
- debug APK と androidTest APK の存在、サイズ、更新日時の一覧

adb がない場合、または `adb devices` が正常終了してオンライン端末がない場合、connected test は理由付きの `SKIP` となる。adb を発見済みなのに `adb devices` が異常終了した場合は、ADB check と connected test を `FAIL` としてスクリプト全体を失敗させる。判定理由は `review-check-summary.txt` に記録する。

## 通常版

`scripts/make-review-archive.ps1` は、HEAD の差分、変更ファイルの安全なスナップショット、未コミット差分、Git メタ情報、検証ログ、Android レビューシグナルを収録する。APK や Gradle の生成物は含めない。

Windows PowerShell 5.1:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\make-review-archive.ps1 -BaseRef $base -SkipChecks
powershell -ExecutionPolicy Bypass -File scripts\make-review-archive.ps1 -BaseRef $base -SkipChecks -ExpectedHeadSubject "chore: add Windows review archive tooling"
```

PowerShell 7+:

```powershell
pwsh -File scripts\make-review-archive.ps1 -BaseRef $base -SkipChecks
```

検証済みログを再利用する archive 生成では必ず `-SkipChecks` を付ける。ただし、必須ログが揃い、すべての必須結果が `PASS`、connected test が `PASS` または `SKIP` でなければ生成しない。

成功時に次を生成する。

- `busnav-review-<HEAD short SHA>-<UTC timestamp>.zip`
- `busnav-review-latest.zip`

## Full 版

`scripts/make-full-review-archive.ps1` は `git archive HEAD` を使用し、Git 追跡済みファイルを `repo/` に収録する。作業ツリーの untracked ファイル、`.git/`、ビルド生成物は取得元の時点で含まれない。Gradle bootstrap に必要な `gradle/wrapper/gradle-wrapper.jar` は例外として保持する。

```powershell
powershell -ExecutionPolicy Bypass -File scripts\make-full-review-archive.ps1 -BaseRef $base -SkipChecks
pwsh -File scripts\make-full-review-archive.ps1 -BaseRef $base -SkipChecks
```

成功時に次を生成する。

- `busnav-full-review-<HEAD short SHA>-<UTC timestamp>.zip`
- `busnav-full-review-latest.zip`

## ZIP の自己検査

各スクリプトは ZIP entry を .NET で読み返し、必須ディレクトリと禁止ファイルを検査する。検査に失敗した ZIP は正式名へ移動せず、`latest.zip` も更新しない。成功時の自己検査結果は `meta/archive-self-check.txt` に記録する。

## セキュリティ

アーカイブには `local.properties`、`.env`、keystore、秘密鍵、credential/secret/password を示すファイル名、APK/AAB、データベース、ログ、ZIP、`build/`、`.gradle/`、`.idea/`、`.git/` を含めない。軽量版の変更ファイルスナップショットではバイナリも除外し、除外理由を `meta/excluded-files.txt` に記録する。

`checks/android-review-signals.txt` は位置情報権限、MapLibre、WebView、平文 HTTP、秘密情報を示す語、TODO/FIXME などを検索するレビュー補助であり、文字列が見つかっただけでは失敗にしない。
