[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logPath = Join-Path $projectRoot 'wsl-winget-install.log'

Start-Transcript -LiteralPath $logPath -Force | Out-Null

try {
    $winget = (Get-Command winget.exe -ErrorAction Stop).Source
    Write-Output "Using winget: $winget"

    & $winget install `
        --id Microsoft.WSL `
        --exact `
        --source winget `
        --accept-source-agreements `
        --accept-package-agreements `
        --disable-interactivity

    $wingetExitCode = $LASTEXITCODE
    Write-Output "WINGET_INSTALL_EXIT=$wingetExitCode"
    exit $wingetExitCode
}
catch {
    Write-Error $_
    exit 1
}
finally {
    Stop-Transcript | Out-Null
}
