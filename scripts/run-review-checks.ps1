[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "review-common.ps1")

$repositoryRoot = Resolve-ReviewRepositoryRoot
$checksDirectory = Join-Path $repositoryRoot "build\review-checks"
[void](New-Item -ItemType Directory -Path $checksDirectory -Force)

$knownLogs = @(
    "git-diff-check.txt",
    "gradle-test.txt",
    "gradle-lint.txt",
    "gradle-assemble-debug.txt",
    "gradle-assemble-android-test.txt",
    "adb-devices.txt",
    "connected-debug-android-test.txt",
    "artifact-inventory.txt",
    "review-check-summary.txt"
)
foreach ($knownLog in $knownLogs) {
    $knownLogPath = Join-Path $checksDirectory $knownLog
    if (Test-Path -LiteralPath $knownLogPath) {
        Remove-Item -LiteralPath $knownLogPath -Force
    }
}

$gitContext = Get-ReviewGitContext -RepositoryRoot $repositoryRoot
$statuses = @{}
$runUtc = [DateTime]::UtcNow

function Invoke-LoggedCheck {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$LogName
    )

    Write-Host "Running $Name..."
    $result = Invoke-CapturedCommand -FilePath $FilePath -Arguments $Arguments -WorkingDirectory $repositoryRoot
    if ($result.ExitCode -eq 0) {
        $status = "PASS"
    }
    else {
        $status = "FAIL"
    }
    Write-Utf8File -Path (Join-Path $checksDirectory $LogName) -Content (Format-CommandResult -Result $result -Status $status)
    $statuses[$Name] = $status
    return $result
}

$gitPath = Get-GitCommandPath
[void](Invoke-LoggedCheck -Name "Git diff check" -FilePath $gitPath -Arguments @("diff", "--check", $gitContext.DiffBase, "HEAD") -LogName "git-diff-check.txt")

$gradleWrapper = Join-Path $repositoryRoot "gradlew.bat"
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper is missing: $gradleWrapper"
}

[void](Invoke-LoggedCheck -Name "Gradle test" -FilePath $gradleWrapper -Arguments @("test", "--console=plain") -LogName "gradle-test.txt")
[void](Invoke-LoggedCheck -Name "Gradle lint" -FilePath $gradleWrapper -Arguments @("lint", "--console=plain") -LogName "gradle-lint.txt")
[void](Invoke-LoggedCheck -Name "assembleDebug" -FilePath $gradleWrapper -Arguments @("assembleDebug", "--console=plain") -LogName "gradle-assemble-debug.txt")
[void](Invoke-LoggedCheck -Name "assembleDebugAndroidTest" -FilePath $gradleWrapper -Arguments @("assembleDebugAndroidTest", "--console=plain") -LogName "gradle-assemble-android-test.txt")

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
$adbAvailable = $null -ne $adbCommand
if ($adbAvailable) {
    Write-Host "Checking connected Android devices..."
    $adbResult = Invoke-CapturedCommand -FilePath $adbCommand.Source -Arguments @("devices") -WorkingDirectory $repositoryRoot
    if ($adbResult.ExitCode -eq 0) {
        $adbStatus = "PASS"
    }
    else {
        $adbStatus = "FAIL"
    }
    Write-Utf8File -Path (Join-Path $checksDirectory "adb-devices.txt") -Content (Format-CommandResult -Result $adbResult -Status $adbStatus)

    $onlineDevices = @($adbResult.StandardOutput -split "`r?`n" | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
    if (($adbResult.ExitCode -eq 0) -and ($onlineDevices.Count -gt 0)) {
        [void](Invoke-LoggedCheck -Name "connectedDebugAndroidTest" -FilePath $gradleWrapper -Arguments @("connectedDebugAndroidTest", "--console=plain") -LogName "connected-debug-android-test.txt")
    }
    else {
        if ($adbResult.ExitCode -ne 0) {
            $reason = "adb devices failed; no online device could be confirmed"
        }
        else {
            $reason = "no connected Android device or running emulator"
        }
        $skipResult = [PSCustomObject]@{
            Command = "$gradleWrapper connectedDebugAndroidTest --console=plain"
            StartUtc = [DateTime]::UtcNow
            EndUtc = [DateTime]::UtcNow
            ExitCode = 0
            StandardOutput = ""
            StandardError = ""
        }
        Write-Utf8File -Path (Join-Path $checksDirectory "connected-debug-android-test.txt") -Content (Format-CommandResult -Result $skipResult -Status "SKIP" -Reason $reason)
        $statuses["connectedDebugAndroidTest"] = "SKIP"
    }
}
else {
    $notFoundResult = [PSCustomObject]@{
        Command = "adb devices"
        StartUtc = [DateTime]::UtcNow
        EndUtc = [DateTime]::UtcNow
        ExitCode = 0
        StandardOutput = "adb was not found on PATH."
        StandardError = ""
    }
    Write-Utf8File -Path (Join-Path $checksDirectory "adb-devices.txt") -Content (Format-CommandResult -Result $notFoundResult -Status "SKIP" -Reason "adb is not available on PATH")
    $connectedSkip = [PSCustomObject]@{
        Command = "$gradleWrapper connectedDebugAndroidTest --console=plain"
        StartUtc = [DateTime]::UtcNow
        EndUtc = [DateTime]::UtcNow
        ExitCode = 0
        StandardOutput = ""
        StandardError = ""
    }
    Write-Utf8File -Path (Join-Path $checksDirectory "connected-debug-android-test.txt") -Content (Format-CommandResult -Result $connectedSkip -Status "SKIP" -Reason "adb is not available on PATH; no connected Android device or running emulator could be detected")
    $statuses["connectedDebugAndroidTest"] = "SKIP"
}

$outputsDirectory = Join-Path $repositoryRoot "app\build\outputs"
$apkFiles = @()
if (Test-Path -LiteralPath $outputsDirectory) {
    $apkFiles = @(Get-ChildItem -LiteralPath $outputsDirectory -Filter "*.apk" -File -Recurse)
}
$debugApkPath = Join-Path $repositoryRoot "app\build\outputs\apk\debug\app-debug.apk"
$debugApkFound = Test-Path -LiteralPath $debugApkPath -PathType Leaf
$androidTestApks = @($apkFiles | Where-Object {
    $relative = Get-RepositoryRelativePath -RepositoryRoot $repositoryRoot -FullPath $_.FullName
    ($relative -match '(?i)(^|/)androidTest(/|$)') -or ($_.Name -match '(?i)androidTest\.apk$')
})
$androidTestApkFound = $androidTestApks.Count -gt 0

$inventoryLines = New-Object System.Collections.Generic.List[string]
$inventoryLines.Add("Artifact inventory")
$inventoryLines.Add("Generated UTC: $([DateTime]::UtcNow.ToString('o'))")
$inventoryLines.Add("")
if ($apkFiles.Count -eq 0) {
    $inventoryLines.Add("No APK files were found under app/build/outputs.")
}
else {
    foreach ($apkFile in ($apkFiles | Sort-Object FullName)) {
        $relativePath = Get-RepositoryRelativePath -RepositoryRoot $repositoryRoot -FullPath $apkFile.FullName
        $inventoryLines.Add("PATH: $relativePath")
        $inventoryLines.Add("SIZE: $($apkFile.Length) bytes")
        $inventoryLines.Add("LAST WRITE UTC: $($apkFile.LastWriteTimeUtc.ToString('o'))")
        $inventoryLines.Add("")
    }
}
$inventoryLines.Add("Debug APK present: $debugApkFound")
$inventoryLines.Add("AndroidTest APK present: $androidTestApkFound")
if ($debugApkFound -and $androidTestApkFound -and ($statuses["assembleDebug"] -eq "PASS") -and ($statuses["assembleDebugAndroidTest"] -eq "PASS")) {
    $statuses["APK inventory"] = "PASS"
}
else {
    $statuses["APK inventory"] = "FAIL"
}
$inventoryLines.Add("RESULT: $($statuses['APK inventory'])")
Write-Utf8File -Path (Join-Path $checksDirectory "artifact-inventory.txt") -Content (($inventoryLines -join [Environment]::NewLine) + [Environment]::NewLine)

$javaVersion = "Unavailable"
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if ($null -ne $javaCommand) {
    $javaResult = Invoke-CapturedCommand -FilePath $javaCommand.Source -Arguments @("-version") -WorkingDirectory $repositoryRoot
    $javaVersion = (($javaResult.StandardOutput + $javaResult.StandardError).Trim() -replace "`r?`n", " | ")
}
$gradleVersionResult = Invoke-CapturedCommand -FilePath $gradleWrapper -Arguments @("--version", "--console=plain") -WorkingDirectory $repositoryRoot
if ($gradleVersionResult.ExitCode -eq 0) {
    $gradleVersionLine = @($gradleVersionResult.StandardOutput -split "`r?`n" | Where-Object { $_ -match '^Gradle\s+' } | Select-Object -First 1)
    if ($gradleVersionLine.Count -gt 0) {
        $gradleVersion = $gradleVersionLine[0].Trim()
    }
    else {
        $gradleVersion = ($gradleVersionResult.StandardOutput.Trim() -replace "`r?`n", " | ")
    }
}
else {
    $gradleVersion = "Unavailable: exit code $($gradleVersionResult.ExitCode)"
}

$summaryStatusNames = @(
    "Git diff check",
    "Gradle test",
    "Gradle lint",
    "assembleDebug",
    "assembleDebugAndroidTest",
    "connectedDebugAndroidTest",
    "APK inventory"
)
$overallPass = $true
foreach ($statusName in $summaryStatusNames) {
    if ($statusName -eq "connectedDebugAndroidTest") {
        if (($statuses[$statusName] -ne "PASS") -and ($statuses[$statusName] -ne "SKIP")) {
            $overallPass = $false
        }
    }
    elseif ($statuses[$statusName] -ne "PASS") {
        $overallPass = $false
    }
}

$summaryLines = @(
    "BusNav review check summary",
    "HEAD SHA: $($gitContext.HeadSha)",
    "HEAD subject: $($gitContext.HeadSubject)",
    "Diff base: $($gitContext.DiffBase)",
    "Run UTC: $($runUtc.ToString('o'))",
    "PowerShell version: $($PSVersionTable.PSVersion.ToString()) ($($PSVersionTable.PSEdition))",
    "Java version: $javaVersion",
    "Gradle version: $gradleVersion",
    "adb availability: $adbAvailable",
    "",
    "Git diff check: $($statuses['Git diff check'])",
    "Gradle test: $($statuses['Gradle test'])",
    "Gradle lint: $($statuses['Gradle lint'])",
    "assembleDebug: $($statuses['assembleDebug'])",
    "assembleDebugAndroidTest: $($statuses['assembleDebugAndroidTest'])",
    "connectedDebugAndroidTest: $($statuses['connectedDebugAndroidTest'])",
    "APK inventory: $($statuses['APK inventory'])",
    ""
)
if ($overallPass) {
    $summaryLines += "RESULT: PASS"
}
else {
    $summaryLines += "RESULT: FAIL"
}
Write-Utf8File -Path (Join-Path $checksDirectory "review-check-summary.txt") -Content (($summaryLines -join [Environment]::NewLine) + [Environment]::NewLine)

if (-not $overallPass) {
    throw "One or more required review checks failed. See $checksDirectory"
}

Write-Host "Review checks passed. Logs: $checksDirectory"
