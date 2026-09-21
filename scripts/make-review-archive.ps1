[CmdletBinding()]
param(
    [string]$ExpectedHeadSubject,
    [string]$BaseRef,
    [switch]$SkipChecks
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "review-common.ps1")

$repositoryRoot = Resolve-ReviewRepositoryRoot
$gitContext = Get-ReviewGitContext -RepositoryRoot $repositoryRoot -BaseRef $BaseRef
Assert-ExpectedHeadSubject -HeadSubject $gitContext.HeadSubject -ExpectedHeadSubject $ExpectedHeadSubject

$checksDirectory = Join-Path $repositoryRoot "build\review-checks"
if (-not $SkipChecks) {
    Write-Host "Running review checks before archive creation..."
    & (Join-Path $PSScriptRoot "run-review-checks.ps1") -BaseRef $BaseRef
}
Assert-ReviewChecks -ChecksDirectory $checksDirectory -GitContext $gitContext

$timestamp = [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssfffZ")
$archiveName = "busnav-review-$($gitContext.HeadShortSha)-$timestamp.zip"
$archivePath = Join-Path $repositoryRoot $archiveName
$latestPath = Join-Path $repositoryRoot "busnav-review-latest.zip"
$temporaryArchive = Join-Path $repositoryRoot (".{0}.tmp-{1}" -f $archiveName, [Guid]::NewGuid().ToString("N"))
$stagingDirectory = New-ReviewStagingDirectory -Kind "review"

try {
    $metaDirectory = Join-Path $stagingDirectory "meta"
    $diffDirectory = Join-Path $stagingDirectory "diff"
    $fileDiffsDirectory = Join-Path $stagingDirectory "file-diffs"
    $filesDirectory = Join-Path $stagingDirectory "files"
    $workingDiffsDirectory = Join-Path $stagingDirectory "working-file-diffs"
    $workingFilesDirectory = Join-Path $stagingDirectory "working-files"
    $archiveChecksDirectory = Join-Path $stagingDirectory "checks"
    foreach ($directory in @($metaDirectory, $diffDirectory, $fileDiffsDirectory, $filesDirectory, $workingDiffsDirectory, $workingFilesDirectory, $archiveChecksDirectory)) {
        [void](New-Item -ItemType Directory -Path $directory -Force)
    }

    $reviewInfo = New-Object System.Collections.Generic.List[string]
    $reviewInfo.Add("Project: BusNav")
    $reviewInfo.Add("Archive name: $archiveName")
    $reviewInfo.Add("Created UTC: $([DateTime]::UtcNow.ToString('o'))")
    $reviewInfo.Add("Repository root: $repositoryRoot")
    $reviewInfo.Add("HEAD SHA: $($gitContext.HeadSha)")
    $reviewInfo.Add("HEAD short SHA: $($gitContext.HeadShortSha)")
    $reviewInfo.Add("HEAD subject: $($gitContext.HeadSubject)")
    $reviewInfo.Add("Diff base: $($gitContext.DiffBase)")
    $reviewInfo.Add("BaseRef input: $(if ([string]::IsNullOrWhiteSpace($BaseRef)) { 'not specified' } else { $BaseRef })")
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
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("diff", "--name-status", "--no-renames", $gitContext.DiffBase, "HEAD") -OutputPath (Join-Path $metaDirectory "phase-name-status.txt")
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("diff", "--name-only", "--no-renames", $gitContext.DiffBase, "HEAD") -OutputPath (Join-Path $metaDirectory "phase-changed-files.txt")
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("diff", "--name-only", "--no-renames", $gitContext.DiffBase, "HEAD") -OutputPath (Join-Path $metaDirectory "changed-files.txt")
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("status", "--short", "--untracked-files=all") -OutputPath (Join-Path $metaDirectory "working-tree-files.txt")

    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("diff", "--no-ext-diff", "--no-renames", $gitContext.DiffBase, "HEAD") -OutputPath (Join-Path $diffDirectory "phase-full-diff.txt")
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("diff", "--no-ext-diff", "--no-renames", $gitContext.DiffBase, "HEAD") -OutputPath (Join-Path $diffDirectory "head-full-diff.txt")
    Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("diff", "HEAD", "--no-ext-diff", "--no-renames") -OutputPath (Join-Path $diffDirectory "working-tree-diff.txt")

    $excludedFiles = New-Object System.Collections.Generic.List[string]
    $changedFilesText = Get-GitText -RepositoryRoot $repositoryRoot -Arguments @("diff", "--name-only", "--no-renames", "--diff-filter=ACMRTUXB", $gitContext.DiffBase, "HEAD")
    $changedFiles = @($changedFilesText -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    foreach ($changedFile in $changedFiles) {
        $safeRelative = $changedFile.Replace(':', '_')
        $individualDiffPath = Join-Path $fileDiffsDirectory ($safeRelative.Replace('/', '\') + ".diff.txt")
        Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("diff", $gitContext.DiffBase, "HEAD", "--", $changedFile) -OutputPath $individualDiffPath
    }

    if ($changedFiles.Count -gt 0) {
        $headFilesArchive = Join-Path $stagingDirectory "head-files-source.zip"
        $archiveArguments = New-Object System.Collections.Generic.List[string]
        foreach ($value in @("archive", "--format=zip", "--output=$headFilesArchive", "HEAD", "--")) {
            $archiveArguments.Add($value)
        }
        foreach ($changedFile in $changedFiles) {
            $archiveArguments.Add($changedFile)
        }
        $gitArchiveResult = Invoke-GitCapture -RepositoryRoot $repositoryRoot -Arguments $archiveArguments.ToArray()
        if ($gitArchiveResult.ExitCode -ne 0) {
            throw "git archive failed while collecting changed files: $($gitArchiveResult.StandardError)"
        }
        $headFilesExtracted = Join-Path $stagingDirectory "head-files-extracted"
        [void](New-Item -ItemType Directory -Path $headFilesExtracted)
        Expand-ZipArchiveSafe -ArchivePath $headFilesArchive -DestinationDirectory $headFilesExtracted
        Get-ChildItem -LiteralPath $headFilesExtracted -File -Recurse | ForEach-Object {
            $relative = Get-RepositoryRelativePath -RepositoryRoot $headFilesExtracted -FullPath $_.FullName
            $destination = Join-Path $filesDirectory $relative.Replace('/', '\')
            $copyResult = Copy-SafeReviewFile -Source $_.FullName -Destination $destination -RelativePath $relative
            if ($copyResult -ne "included") {
                $excludedFiles.Add("files/$relative - $copyResult")
            }
        }
        Remove-Item -LiteralPath $headFilesArchive -Force
        Remove-Item -LiteralPath $headFilesExtracted -Recurse -Force
    }

    $workingTrackedText = Get-GitText -RepositoryRoot $repositoryRoot -Arguments @("diff", "HEAD", "--name-only")
    $workingTracked = @($workingTrackedText -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $untrackedText = Get-GitText -RepositoryRoot $repositoryRoot -Arguments @("ls-files", "--others", "--exclude-standard")
    $untrackedFiles = @($untrackedText -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $workingFiles = @($workingTracked + $untrackedFiles | Sort-Object -Unique)
    foreach ($workingFile in $workingFiles) {
        if (Test-ProhibitedReviewPath -RelativePath $workingFile) {
            $excludedFiles.Add("working-files/$workingFile - prohibited path")
            continue
        }
        $source = Join-Path $repositoryRoot $workingFile.Replace('/', '\')
        $safeRelative = $workingFile.Replace(':', '_')
        $workingDiffPath = Join-Path $workingDiffsDirectory ($safeRelative.Replace('/', '\') + ".diff.txt")
        if ($workingTracked -contains $workingFile) {
            Write-GitOutputFile -RepositoryRoot $repositoryRoot -Arguments @("diff", "HEAD", "--no-ext-diff", "--no-renames", "--", $workingFile) -OutputPath $workingDiffPath
        }
        else {
            Write-Utf8File -Path $workingDiffPath -Content ("UNTRACKED FILE: $workingFile" + [Environment]::NewLine)
        }
        if (Test-Path -LiteralPath $source -PathType Leaf) {
            $destination = Join-Path $workingFilesDirectory $workingFile.Replace('/', '\')
            $copyResult = Copy-SafeReviewFile -Source $source -Destination $destination -RelativePath $workingFile
            if ($copyResult -ne "included") {
                $excludedFiles.Add("working-files/$workingFile - $copyResult")
            }
        }
    }
    $deletedWorkingText = Get-GitText -RepositoryRoot $repositoryRoot -Arguments @("diff", "HEAD", "--name-only", "--diff-filter=D")
    Write-Utf8File -Path (Join-Path $workingFilesDirectory "deleted-files.txt") -Content ($deletedWorkingText + [Environment]::NewLine)

    if ($excludedFiles.Count -eq 0) {
        $excludedFiles.Add("No candidate files were excluded.")
    }
    Write-Utf8File -Path (Join-Path $metaDirectory "excluded-files.txt") -Content (($excludedFiles -join [Environment]::NewLine) + [Environment]::NewLine)

    Copy-ReviewChecks -ChecksDirectory $checksDirectory -Destination $archiveChecksDirectory
    Write-AndroidReviewSignals -RepositoryRoot $repositoryRoot -OutputPath (Join-Path $archiveChecksDirectory "android-review-signals.txt")

    New-ZipFromDirectory -SourceDirectory $stagingDirectory -DestinationPath $temporaryArchive
    $initialInspection = Assert-ReviewArchive -ArchivePath $temporaryArchive -Kind "review"
    Remove-Item -LiteralPath $temporaryArchive -Force
    $inspectionText = @(
        "Archive self-check",
        "Checked UTC: $([DateTime]::UtcNow.ToString('o'))",
        "Initial entry count: $($initialInspection.EntryCount)",
        "Backslash entry names: $($initialInspection.BackslashEntryCount)",
        "Forward-slash entry names: $($initialInspection.ForwardSlashEntryCount)",
        "Required content: $($initialInspection.RequiredContent)",
        "Prohibited entries: $($initialInspection.ProhibitedEntryCount)",
        "RESULT: PASS"
    ) -join [Environment]::NewLine
    Write-Utf8File -Path (Join-Path $metaDirectory "archive-self-check.txt") -Content ($inspectionText + [Environment]::NewLine)

    New-ZipFromDirectory -SourceDirectory $stagingDirectory -DestinationPath $temporaryArchive
    $finalInspection = Assert-ReviewArchive -ArchivePath $temporaryArchive -Kind "review"
    if (Test-Path -LiteralPath $archivePath) {
        throw "Refusing to overwrite existing archive: $archivePath"
    }
    [System.IO.File]::Move($temporaryArchive, $archivePath)
    Update-LatestArchive -SourceArchive $archivePath -LatestPath $latestPath

    Write-Host "Review archive created: $archivePath"
    Write-Host "Latest archive updated: $latestPath"
    Write-Host "Archive entries: $($finalInspection.EntryCount); backslash entries: $($finalInspection.BackslashEntryCount); prohibited entries: $($finalInspection.ProhibitedEntryCount)"
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
