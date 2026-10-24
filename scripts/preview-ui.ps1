param([switch]$Watch)
$ErrorActionPreference = 'Stop'
$previewWorkspace = Split-Path -Parent $PSScriptRoot
$previewProcess = $null

function Get-PreviewSourceStamp {
    $previewFiles = @(Get-ChildItem -LiteralPath (Join-Path $previewWorkspace 'src') -Recurse -File)
    $previewFiles += Get-Item -LiteralPath (Join-Path $previewWorkspace 'build.gradle')
    return (($previewFiles | Sort-Object FullName | ForEach-Object {
        $_.FullName + ':' + $_.Length + ':' + $_.LastWriteTimeUtc.Ticks
    }) -join '|')
}

function Start-CompiledPreview {
    Push-Location -LiteralPath $previewWorkspace
    try {
        & (Join-Path $previewWorkspace 'gradlew.bat') writePreviewRuntime -PpreviewBuild --project-cache-dir build/preview-harness/gradle-cache --offline --console=plain | Out-Host
        if ($LASTEXITCODE -ne 0) {
            Write-Warning 'Compilation failed. The previous preview stays open. Save a correction to retry.'
            return $false
        }
        $previewRuntime = Get-Content -LiteralPath (Join-Path $previewWorkspace 'build/preview-harness/ui-preview-runtime.json') -Raw | ConvertFrom-Json
        if ($script:previewProcess -and -not $script:previewProcess.HasExited) {
            Stop-Process -Id $script:previewProcess.Id
            $script:previewProcess.WaitForExit()
        }
        $script:previewProcess = Start-Process -FilePath $previewRuntime.java -ArgumentList @(
            '-cp', ('"' + $previewRuntime.classpath + '"'), $previewRuntime.main
        ) -WorkingDirectory $previewWorkspace -WindowStyle Hidden -PassThru
        Write-Host 'Preview opened with synthetic data. Close its window or press Ctrl+C to stop.'
        return $true
    }
    finally { Pop-Location }
}

try {
    $previewStamp = Get-PreviewSourceStamp
    $previewStarted = Start-CompiledPreview
    if (-not $Watch) {
        if (-not $previewStarted) { exit 1 }
        if ($previewProcess) { $previewProcess.WaitForExit() }
        exit
    }
    Write-Host 'Watching source files. Successful compilation restarts the preview; sample state resets.'
    while ($true) {
        if ($previewProcess -and $previewProcess.HasExited) { break }
        Start-Sleep -Milliseconds 750
        $previewNextStamp = Get-PreviewSourceStamp
        if ($previewNextStamp -ne $previewStamp) {
            Start-Sleep -Milliseconds 500
            $previewStamp = Get-PreviewSourceStamp
            $previewStarted = Start-CompiledPreview
        }
    }
}
finally {
    if ($previewProcess -and -not $previewProcess.HasExited) { Stop-Process -Id $previewProcess.Id }
}
