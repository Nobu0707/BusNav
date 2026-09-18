# Review 003: Review Archive Hardening

## 1. Review findings

Review 002 found Windows-only ZIP entry separators, single-commit scope in
lightweight archives, reusable stale PASS logs, exact-only HEAD subject checks,
and adb discovery limited to PATH.

## 2. Portable ZIP entries

`New-ZipFromDirectory` now creates each `ZipArchive` entry explicitly with a
repository-relative name normalized to `/`. File streams are copied directly,
including binary content. Archive validation inspects raw `Entry.FullName`
values and fails on any backslash. Self-check metadata records both separator
counts.

## 3. BaseRef and phase range

`run-review-checks.ps1`, `make-review-archive.ps1`, and
`make-full-review-archive.ps1` accept `-BaseRef`. The reference is resolved
as a commit. Lightweight metadata, full-phase diff, per-file diffs, and safe
HEAD-side snapshots cover `BaseRef..HEAD`. Full archives retain the complete
tracked HEAD tree under `repo/` and add phase metadata.

The baseline for this review is
`cea09e708f7994de28b49a9fe6a1145e93307710`.

## 4. Stale check prevention

`Assert-ReviewChecks` now requires the PASS summary's `HEAD SHA` and
`Diff base` to equal the current resolved Git context. A summary from the same
HEAD but a different BaseRef was verified to fail.

## 5. ExpectedHeadSubject substring

Expected subjects use a case-sensitive ordinal substring match. The substring
`Windows review` passed for `fix: harden Windows review archives`; a
nonexistent substring failed as expected. Empty or omitted values skip the
check.

## 6. adb discovery

adb discovery checks PATH, `ANDROID_SDK_ROOT`, `ANDROID_HOME`, then
`local.properties` `sdk.dir`. Java-properties escaping is decoded.
`local.properties` remains prohibited from every archive.

This environment found:

- discovery: `local.properties`
- path: `C:\Users\Yoshi\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- online devices: none

## 7. Executed tests

Against BaseRef `cea09e7`:

| Check | Result |
| --- | --- |
| `git diff --check <base> HEAD` | PASS |
| `gradlew.bat test` | PASS |
| `gradlew.bat lint` | PASS |
| `gradlew.bat assembleDebug` | PASS |
| `gradlew.bat assembleDebugAndroidTest` | PASS |
| `adb devices` | PASS |
| `connectedDebugAndroidTest` | SKIP (no online device/emulator) |
| APK artifact inventory | PASS |
| ZIP binary byte preservation | PASS |
| stale BaseRef rejection | PASS |

## 8. Generated archives

- `busnav-review-latest.zip`
- `busnav-full-review-latest.zip`

The final aliases are regenerated at the final documentation HEAD with
`-BaseRef cea09e7`.

## 9. Archive self-check

Both archive types reported zero raw backslash entry names and zero prohibited
entries. The lightweight archive includes the four PowerShell scripts,
`diff/phase-full-diff.txt`, and phase changed-file metadata. The full archive
includes `repo/gradle/wrapper/gradle-wrapper.jar`.

## 10. Known limitations

`connectedDebugAndroidTest` cannot run without an online emulator or physical
device. Filename/path security filters cannot detect every secret embedded in
otherwise allowed text; human review remains required.

## 11. Commit history

- implementation: `aa8e33a58381bb9d09a89bf2c243a59b47fbb765`
  (`fix: harden Windows review archives`)
- documentation finalization: recorded by the final Git HEAD and archive
  `meta/review-info.txt`
