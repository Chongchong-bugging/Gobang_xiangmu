[CmdletBinding()]
param()

$ErrorActionPreference = 'Continue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logPath = Join-Path $projectRoot 'wsl-install.log'

Start-Transcript -LiteralPath $logPath -Force | Out-Null

try {
    Write-Output 'Installing the Windows Subsystem for Linux package...'
    & "$env:SystemRoot\System32\wsl.exe" --install --no-distribution --web-download
    $wslExitCode = $LASTEXITCODE
    Write-Output "WSL_INSTALL_EXIT=$wslExitCode"
    exit $wslExitCode
}
finally {
    Stop-Transcript | Out-Null
}
