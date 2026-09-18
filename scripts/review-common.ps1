Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-ReviewRepositoryRoot {
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
    $gitDirectory = Join-Path $candidate ".git"
    if (-not (Test-Path -LiteralPath $gitDirectory)) {
        throw "Unable to resolve the repository root from $PSScriptRoot"
    }

    return $candidate
}

function ConvertTo-NativeArgument {
    param([AllowEmptyString()][string]$Value)

    if ($Value -notmatch '[\s"]') {
        return $Value
    }

    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append('"')
    $backslashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '\') {
            $backslashes++
            continue
        }

        if ($character -eq '"') {
            [void]$builder.Append(('\' * (($backslashes * 2) + 1)))
            [void]$builder.Append('"')
            $backslashes = 0
            continue
        }

        if ($backslashes -gt 0) {
            [void]$builder.Append(('\' * $backslashes))
            $backslashes = 0
        }
        [void]$builder.Append($character)
    }

    if ($backslashes -gt 0) {
        [void]$builder.Append(('\' * ($backslashes * 2)))
    }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function Invoke-CapturedCommand {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )

    $resolvedFile = $FilePath
    if (-not [System.IO.Path]::IsPathRooted($resolvedFile)) {
        $command = Get-Command $resolvedFile -ErrorAction SilentlyContinue
        if ($null -eq $command) {
            throw "Command not found: $FilePath"
        }
        $resolvedFile = $command.Source
    }

    $displayArguments = @($Arguments | ForEach-Object { ConvertTo-NativeArgument $_ })
    $displayCommand = (ConvertTo-NativeArgument $resolvedFile)
    if ($displayArguments.Count -gt 0) {
        $displayCommand += " " + ($displayArguments -join " ")
    }

    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.WorkingDirectory = $WorkingDirectory
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true

    $extension = [System.IO.Path]::GetExtension($resolvedFile)
    if (($extension -ieq ".bat") -or ($extension -ieq ".cmd")) {
        $processInfo.FileName = $env:ComSpec
        $batchCommand = 'call ' + (ConvertTo-NativeArgument $resolvedFile)
        if ($displayArguments.Count -gt 0) {
            $batchCommand += " " + ($displayArguments -join " ")
        }
        $processInfo.Arguments = '/d /s /c "' + $batchCommand + '"'
    }
    else {
        $processInfo.FileName = $resolvedFile
        $processInfo.Arguments = $displayArguments -join " "
    }

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $processInfo
    $startedUtc = [DateTime]::UtcNow
    if (-not $process.Start()) {
        throw "Failed to start command: $displayCommand"
    }

    $standardOutputTask = $process.StandardOutput.ReadToEndAsync()
    $standardErrorTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit()
    $standardOutput = $standardOutputTask.Result
    $standardError = $standardErrorTask.Result
    $endedUtc = [DateTime]::UtcNow

    return [PSCustomObject]@{
        Command = $displayCommand
        StartUtc = $startedUtc
        EndUtc = $endedUtc
        ExitCode = $process.ExitCode
        StandardOutput = $standardOutput
        StandardError = $standardError
    }
}

function Write-Utf8File {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [AllowEmptyString()][string]$Content
    )

    $parent = Split-Path -Parent $Path
    if (($parent.Length -gt 0) -and (-not (Test-Path -LiteralPath $parent))) {
        [void](New-Item -ItemType Directory -Path $parent -Force)
    }
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Format-CommandResult {
    param(
        [Parameter(Mandatory = $true)]$Result,
        [Parameter(Mandatory = $true)][ValidateSet("PASS", "FAIL", "SKIP")][string]$Status,
        [string]$Reason
    )

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("COMMAND: $($Result.Command)")
    $lines.Add("START UTC: $($Result.StartUtc.ToString('o'))")
    $lines.Add("END UTC: $($Result.EndUtc.ToString('o'))")
    $lines.Add("EXIT CODE: $($Result.ExitCode)")
    $lines.Add("")
    if (-not [string]::IsNullOrEmpty($Result.StandardOutput)) {
        $lines.Add("STANDARD OUTPUT:")
        $lines.Add($Result.StandardOutput.TrimEnd())
        $lines.Add("")
    }
    if (-not [string]::IsNullOrEmpty($Result.StandardError)) {
        $lines.Add("STANDARD ERROR:")
        $lines.Add($Result.StandardError.TrimEnd())
        $lines.Add("")
    }
    if (-not [string]::IsNullOrEmpty($Reason)) {
        $lines.Add("REASON: $Reason")
    }
    $lines.Add("RESULT: $Status")
    return ($lines -join [Environment]::NewLine) + [Environment]::NewLine
}

function Get-GitCommandPath {
    $command = Get-Command git -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "git is required but was not found on PATH"
    }
    return $command.Source
}

function Invoke-GitCapture {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    return Invoke-CapturedCommand -FilePath (Get-GitCommandPath) -Arguments $Arguments -WorkingDirectory $RepositoryRoot
}

function Get-GitText {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $result = Invoke-GitCapture -RepositoryRoot $RepositoryRoot -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        throw "git command failed: $($result.Command)`n$($result.StandardError)"
    }
    return $result.StandardOutput.TrimEnd("`r", "`n")
}

function Get-ReviewGitContext {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

    $headSha = Get-GitText -RepositoryRoot $RepositoryRoot -Arguments @("rev-parse", "HEAD")
    $headShortSha = Get-GitText -RepositoryRoot $RepositoryRoot -Arguments @("rev-parse", "--short=12", "HEAD")
    $headSubject = Get-GitText -RepositoryRoot $RepositoryRoot -Arguments @("log", "-1", "--format=%s", "HEAD")
    $parentResult = Invoke-GitCapture -RepositoryRoot $RepositoryRoot -Arguments @("rev-parse", "HEAD^")
    if ($parentResult.ExitCode -eq 0) {
        $diffBase = $parentResult.StandardOutput.Trim()
    }
    else {
        $diffBase = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
    }

    return [PSCustomObject]@{
        HeadSha = $headSha
        HeadShortSha = $headShortSha
        HeadSubject = $headSubject
        DiffBase = $diffBase
    }
}

function Get-ReviewCheckStatus {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return "MISSING"
    }
    $matches = Select-String -LiteralPath $Path -Pattern '^RESULT: (PASS|FAIL|SKIP)$' -AllMatches
    if ($null -eq $matches) {
        return "UNKNOWN"
    }
    $lastMatch = @($matches)[-1]
    return $lastMatch.Matches[0].Groups[1].Value
}

function Assert-ReviewChecks {
    param([Parameter(Mandatory = $true)][string]$ChecksDirectory)

    $requiredPassLogs = @(
        "git-diff-check.txt",
        "gradle-test.txt",
        "gradle-lint.txt",
        "gradle-assemble-debug.txt",
        "gradle-assemble-android-test.txt",
        "artifact-inventory.txt",
        "review-check-summary.txt"
    )

    foreach ($logName in $requiredPassLogs) {
        $path = Join-Path $ChecksDirectory $logName
        $status = Get-ReviewCheckStatus -Path $path
        if ($status -ne "PASS") {
            throw "Required review check is not PASS: $logName ($status)"
        }
    }

    $connectedLog = Join-Path $ChecksDirectory "connected-debug-android-test.txt"
    $connectedStatus = Get-ReviewCheckStatus -Path $connectedLog
    if (($connectedStatus -ne "PASS") -and ($connectedStatus -ne "SKIP")) {
        throw "Connected Android test must be PASS or SKIP: connected-debug-android-test.txt ($connectedStatus)"
    }
}

function Get-RepositoryRelativePath {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$FullPath
    )

    $root = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd('\', '/')
    $path = [System.IO.Path]::GetFullPath($FullPath)
    if (-not $path.StartsWith($root + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside repository root: $FullPath"
    }
    return $path.Substring($root.Length + 1).Replace('\', '/')
}

function Test-ProhibitedReviewPath {
    param(
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [switch]$AllowGradleWrapperJar
    )

    $normalized = $RelativePath.Replace('\', '/').TrimStart('/')
    if ($AllowGradleWrapperJar -and ($normalized -ieq "gradle/wrapper/gradle-wrapper.jar" -or $normalized -ieq "repo/gradle/wrapper/gradle-wrapper.jar")) {
        return $false
    }

    $segments = @($normalized -split '/')
    foreach ($segment in $segments) {
        if ($segment -match '^(?i:\.git|\.gradle|build|\.idea|secrets?|private)$') {
            return $true
        }
    }

    $name = [System.IO.Path]::GetFileName($normalized)
    if ($name -ieq "local.properties") { return $true }
    if ($name -match '^(?i:\.env)(\..*)?$') { return $true }
    if ($name -match '(?i:credential|secret|password)') { return $true }
    if ($name -match '(?i:\.(apk|aab|class|dex|log|db|sqlite|sqlite3|zip|tmp|jks|keystore|p12|pfx|pem|key))$') { return $true }
    if ($name -match '(?i:\.tar\.gz)$') { return $true }
    return $false
}

function Test-BinaryFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $length = [Math]::Min(8192, $stream.Length)
        $buffer = New-Object byte[] $length
        $read = $stream.Read($buffer, 0, $length)
        for ($index = 0; $index -lt $read; $index++) {
            if ($buffer[$index] -eq 0) {
                return $true
            }
        }
        return $false
    }
    finally {
        $stream.Dispose()
    }
}

function Copy-SafeReviewFile {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination,
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [switch]$AllowBinary,
        [switch]$AllowGradleWrapperJar
    )

    if (Test-ProhibitedReviewPath -RelativePath $RelativePath -AllowGradleWrapperJar:$AllowGradleWrapperJar) {
        return "excluded: prohibited path"
    }
    if ((-not $AllowBinary) -and (Test-BinaryFile -Path $Source)) {
        return "excluded: binary file"
    }

    $parent = Split-Path -Parent $Destination
    if (-not (Test-Path -LiteralPath $parent)) {
        [void](New-Item -ItemType Directory -Path $parent -Force)
    }
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
    return "included"
}

function Copy-ReviewChecks {
    param(
        [Parameter(Mandatory = $true)][string]$ChecksDirectory,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    [void](New-Item -ItemType Directory -Path $Destination -Force)
    Get-ChildItem -LiteralPath $ChecksDirectory -Filter "*.txt" -File | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $Destination $_.Name) -Force
    }
}

function Write-GitOutputFile {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    $result = Invoke-GitCapture -RepositoryRoot $RepositoryRoot -Arguments $Arguments
    $content = $result.StandardOutput
    if (-not [string]::IsNullOrEmpty($result.StandardError)) {
        if (-not [string]::IsNullOrEmpty($content)) {
            $content += [Environment]::NewLine
        }
        $content += $result.StandardError
    }
    Write-Utf8File -Path $OutputPath -Content $content
    if ($result.ExitCode -ne 0) {
        throw "git command failed while creating $OutputPath`: $($result.Command)"
    }
}

function Write-AndroidReviewSignals {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$OutputPath
    )

    $patterns = @(
        "ACCESS_BACKGROUND_LOCATION",
        "ACCESS_FINE_LOCATION",
        "ACCESS_COARSE_LOCATION",
        "MapLibre",
        "LocationManager",
        "FusedLocationProvider",
        "TODO",
        "FIXME",
        "HACK",
        "XXX",
        "http://",
        "WebView",
        "API key",
        "token",
        "secret",
        "password",
        "keystore",
        "System.out",
        "printStackTrace",
        "deprecated",
        "@Suppress"
    )
    $arguments = New-Object System.Collections.Generic.List[string]
    $arguments.Add("grep")
    $arguments.Add("-n")
    $arguments.Add("-I")
    $arguments.Add("-i")
    foreach ($pattern in $patterns) {
        $arguments.Add("-e")
        $arguments.Add($pattern)
    }
    $arguments.Add("--")

    $result = Invoke-GitCapture -RepositoryRoot $RepositoryRoot -Arguments $arguments.ToArray()
    if (($result.ExitCode -ne 0) -and ($result.ExitCode -ne 1)) {
        throw "git grep failed while creating Android review signals: $($result.StandardError)"
    }
    $header = @(
        "Android review signals (informational only)",
        "Generated UTC: $([DateTime]::UtcNow.ToString('o'))",
        "RESULT: INFORMATIONAL",
        ""
    ) -join [Environment]::NewLine
    $body = $result.StandardOutput
    if ([string]::IsNullOrWhiteSpace($body)) {
        $body = "No matching tracked-file signals were found." + [Environment]::NewLine
    }
    Write-Utf8File -Path $OutputPath -Content ($header + $body)
}

function New-ReviewStagingDirectory {
    param([Parameter(Mandatory = $true)][string]$Kind)

    $path = Join-Path ([System.IO.Path]::GetTempPath()) ("busnav-{0}-{1}" -f $Kind, [Guid]::NewGuid().ToString("N"))
    [void](New-Item -ItemType Directory -Path $path)
    return $path
}

function Remove-ReviewStagingDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)

    $resolvedPath = [System.IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
    $leafName = [System.IO.Path]::GetFileName($resolvedPath)
    if ((-not $resolvedPath.StartsWith($temporaryRoot + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) -or
        ($leafName -notmatch '^busnav-(review|full)-[0-9a-f]{32}$')) {
        throw "Refusing to remove unexpected staging directory: $Path"
    }
    if (Test-Path -LiteralPath $resolvedPath) {
        Remove-Item -LiteralPath $resolvedPath -Recurse -Force
    }
}

function Initialize-ZipSupport {
    Add-Type -AssemblyName System.IO.Compression -ErrorAction SilentlyContinue
    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
}

function Assert-ReviewArchive {
    param(
        [Parameter(Mandatory = $true)][string]$ArchivePath,
        [Parameter(Mandatory = $true)][ValidateSet("review", "full")][string]$Kind
    )

    Initialize-ZipSupport
    $archive = [System.IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        $requiredPrefixes = @("meta/", "checks/")
        if ($Kind -eq "review") {
            $requiredPrefixes += "diff/"
        }
        else {
            $requiredPrefixes += "repo/"
        }

        foreach ($prefix in $requiredPrefixes) {
            if (@($entryNames | Where-Object { $_.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) }).Count -eq 0) {
                throw "Archive is missing required content: $prefix"
            }
        }

        $prohibited = New-Object System.Collections.Generic.List[string]
        foreach ($entryName in $entryNames) {
            if ([string]::IsNullOrEmpty($entryName) -or $entryName.EndsWith("/")) {
                continue
            }
            if (Test-ProhibitedReviewPath -RelativePath $entryName -AllowGradleWrapperJar:($Kind -eq "full")) {
                $prohibited.Add($entryName)
            }
        }
        if ($prohibited.Count -gt 0) {
            throw "Archive contains prohibited entries:`n$($prohibited -join [Environment]::NewLine)"
        }

        if (($Kind -eq "full") -and (-not ($entryNames -contains "repo/gradle/wrapper/gradle-wrapper.jar"))) {
            throw "Full archive is missing repo/gradle/wrapper/gradle-wrapper.jar"
        }

        return [PSCustomObject]@{
            EntryCount = $entryNames.Count
            ProhibitedEntryCount = 0
            RequiredContent = "PASS"
        }
    }
    finally {
        $archive.Dispose()
    }
}

function New-ZipFromDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$SourceDirectory,
        [Parameter(Mandatory = $true)][string]$DestinationPath
    )

    Initialize-ZipSupport
    if (Test-Path -LiteralPath $DestinationPath) {
        throw "Refusing to overwrite existing archive: $DestinationPath"
    }
    [System.IO.Compression.ZipFile]::CreateFromDirectory(
        $SourceDirectory,
        $DestinationPath,
        [System.IO.Compression.CompressionLevel]::Optimal,
        $false
    )
}

function Update-LatestArchive {
    param(
        [Parameter(Mandatory = $true)][string]$SourceArchive,
        [Parameter(Mandatory = $true)][string]$LatestPath
    )

    $operationId = [Guid]::NewGuid().ToString("N")
    $temporaryLatest = $LatestPath + ".tmp-" + $operationId
    $backupLatest = $LatestPath + ".backup-" + $operationId
    Copy-Item -LiteralPath $SourceArchive -Destination $temporaryLatest
    try {
        if (Test-Path -LiteralPath $LatestPath) {
            [System.IO.File]::Replace($temporaryLatest, $LatestPath, $backupLatest, $true)
        }
        else {
            [System.IO.File]::Move($temporaryLatest, $LatestPath)
        }
    }
    finally {
        if (Test-Path -LiteralPath $temporaryLatest) {
            Remove-Item -LiteralPath $temporaryLatest -Force
        }
        if (Test-Path -LiteralPath $backupLatest) {
            Remove-Item -LiteralPath $backupLatest -Force
        }
    }
}

function Expand-ZipArchiveSafe {
    param(
        [Parameter(Mandatory = $true)][string]$ArchivePath,
        [Parameter(Mandatory = $true)][string]$DestinationDirectory
    )

    Initialize-ZipSupport
    [System.IO.Compression.ZipFile]::ExtractToDirectory($ArchivePath, $DestinationDirectory)
}
