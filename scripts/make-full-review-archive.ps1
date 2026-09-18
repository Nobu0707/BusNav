[CmdletBinding()]
param(
    [string]$ExpectedHeadSubject,
    [switch]$SkipChecks
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "review-common.ps1")

$repositoryRoot = Resolve-ReviewRepositoryRoot
$gitContext = Get-ReviewGitContext -RepositoryRoot $repositoryRoot
if ($PSBoundParameters.ContainsKey("ExpectedHeadSubject") -and ($gitContext.HeadSubject -cne $ExpectedHeadSubject)) {
    throw "HEAD subject mismatch. Expected '$ExpectedHeadSubject' but found '$($gitContext.HeadSubject)'."
}

$checksDirectory = Join-Path $repositoryRoot "build\review-checks"
if (-not $SkipChecks) {
    Write-Host "Running review checks before full archive creation..."
    & (Join-Path $PSScriptRoot "run-review-checks.ps1")
}
Assert-ReviewChecks -ChecksDirectory $checksDirectory

$timestamp = [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssfffZ")
$archiveName = "busnav-full-review-$($gitContext.HeadShortSha)-$timestamp.zip"
$archivePath = Join-Path $repositoryRoot $archiveName
$latestPath = Join-Path $repositoryRoot "busnav-full-review-latest.zip"
$temporaryArchive = Join-Path $repositoryRoot (".{0}.tmp-{1}" -f $archiveName, [Guid]::NewGuid().ToString("N"))
$stagingDirectory = New-ReviewStagingDirectory -Kind "full"

try {
    $metaDirectory = Join-Path $stagingDirectory "meta"
    $repoDirectory = Join-Path $stagingDirectory "repo"
    $archiveChecksDirectory = Join-Path $stagingDirectory "checks"
    $rawRepoDirectory = Join-Path $stagingDirectory "raw-repo"
    foreach ($directory in @($metaDirectory, $repoDirectory, $archiveChecksDirectory, $rawRepoDirectory)) {
        [void](New-Item -ItemType Directory -Path $directory -Force)
    }

    $reviewInfo = New-Object System.Collections.Generic.List[string]
    $reviewInfo.Add("Project: BusNav")
    $reviewInfo.Add("Archive type: full")
    $reviewInfo.Add("Archive name: $archiveName")
    $reviewInfo.Add("Created UTC: $([DateTime]::UtcNow.ToString('o'))")
    $reviewInfo.Add("Repository root: $repositoryRoot")
    $reviewInfo.Add("HEAD SHA: $($gitContext.HeadSha)")
    $reviewInfo.Add("HEAD short SHA: $($gitContext.HeadShortSha)")
    $reviewInfo.Add("HEAD subject: $($gitContext.HeadSubject)")
    $reviewInfo.Add("Diff base: $($gitContext.DiffBase)")
    if ($PSBoundParameters.ContainsKey("ExpectedHeadSubject")) {
        $reviewInfo.Add("Expected subject: $ExpectedHeadSubject")
    }
    else {
        $reviewInfo.Add("Expected subject: not specified")
    }
    $reviewInfo.Add("PowerShell version: $($PSVersionTable.PSVersion.ToString()) ($($PSVersionTable.PSEdition))")
    Write-Utf8File -Path (Join-Path $metaDirectory "review-info.txt") -Content (($reviewInfo -join [Environment]::NewLine) + [Environment]::NewLine)
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("log", "--oneline", "-n", "50") -OutputPath (Join-Path $metaDirectory "git-log-oneline.txt")
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("status", "--short", "--untracked-files=all") -OutputPath (Join-Path $metaDirectory "git-status-short.txt")
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("show", "--stat", "--format=fuller", "HEAD") -OutputPath (Join-Path $metaDirectory "head-stat.txt")
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("diff-tree", "--root", "--no-commit-id", "--name-status", "-r", "HEAD") -OutputPath (Join-Path $metaDirectory "head-name-status.txt")

    $sourceArchive = Join-Path $stagingDirectory "head-source.zip"
    $gitArchiveResult = Invoke-GitCapture -RepositoryRoot $repositoryRoot -Arguments @("archive", "--format=zip", "--output=$sourceArchive", "HEAD")
    if ($gitArchiveResult.ExitCode -ne 0) {
        throw "git archive HEAD failed: $($gitArchiveResult.StandardError)"
    }
    Expand-ZipArchiveSafe -ArchivePath $sourceArchive -DestinationDirectory $rawRepoDirectory

    $excludedFiles = New-Object System.Collections.Generic.List[string]
    Get-ChildItem -LiteralPath $rawRepoDirectory -File -Recurse | ForEach-Object {
        $relative = Get-RepositoryRelativePath -RepositoryRoot $rawRepoDirectory -FullPath $_.FullName
        $destination = Join-Path $repoDirectory $relative.Replace('/', '\')
        $copyResult = Copy-SafeReviewFile -Source $_.FullName -Destination $destination -RelativePath $relative -AllowBinary -AllowGradleWrapperJar
        if ($copyResult -ne "included") {
            $excludedFiles.Add("repo/$relative - $copyResult")
        }
    }
    Remove-Item -LiteralPath $sourceArchive -Force
    Remove-Item -LiteralPath $rawRepoDirectory -Recurse -Force
    if ($excludedFiles.Count -eq 0) {
        $excludedFiles.Add("No tracked files were excluded.")
    }
    Write-Utf8File -Path (Join-Path $metaDirectory "excluded-files.txt") -Content (($excludedFiles -join [Environment]::NewLine) + [Environment]::NewLine)

    Copy-ReviewChecks -ChecksDirectory $checksDirectory -Destination $archiveChecksDirectory
    Write-AndroidReviewSignals -RepositoryRoot $repositoryRoot -OutputPath (Join-Path $archiveChecksDirectory "android-review-signals.txt")

    New-ZipFromDirectory -SourceDirectory $stagingDirectory -DestinationPath $temporaryArchive
    $initialInspection = Assert-ReviewArchive -ArchivePath $temporaryArchive -Kind "full"
    Remove-Item -LiteralPath $temporaryArchive -Force
    $inspectionText = @(
        "Archive self-check",
        "Checked UTC: $([DateTime]::UtcNow.ToString('o'))",
        "Initial entry count: $($initialInspection.EntryCount)",
        "Required content: $($initialInspection.RequiredContent)",
        "Prohibited entries: $($initialInspection.ProhibitedEntryCount)",
        "Gradle wrapper JAR: included",
        "RESULT: PASS"
    ) -join [Environment]::NewLine
    Write-Utf8File -Path (Join-Path $metaDirectory "archive-self-check.txt") -Content ($inspectionText + [Environment]::NewLine)

    New-ZipFromDirectory -SourceDirectory $stagingDirectory -DestinationPath $temporaryArchive
    $finalInspection = Assert-ReviewArchive -ArchivePath $temporaryArchive -Kind "full"
    if (Test-Path -LiteralPath $archivePath) {
        throw "Refusing to overwrite existing archive: $archivePath"
    }
    [System.IO.File]::Move($temporaryArchive, $archivePath)
    Update-LatestArchive -SourceArchive $archivePath -LatestPath $latestPath

    Write-Host "Full review archive created: $archivePath"
    Write-Host "Latest full archive updated: $latestPath"
    Write-Host "Archive entries: $($finalInspection.EntryCount); prohibited entries: $($finalInspection.ProhibitedEntryCount)"
}
catch {
    if (Test-Path -LiteralPath $temporaryArchive) {
        Remove-Item -LiteralPath $temporaryArchive -Force
    }
    throw
}
finally {
    Remove-ReviewStagingDirectory -Path $stagingDirectory
}
