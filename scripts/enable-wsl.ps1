[CmdletBinding()]
param(
    [switch]$Elevated
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logPath = Join-Path $projectRoot 'docker-wsl-setup.log'

$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
$isAdministrator = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdministrator) {
    $arguments = @(
        '-NoProfile'
        '-ExecutionPolicy', 'Bypass'
        '-File', ('"{0}"' -f $PSCommandPath)
        '-Elevated'
    )

    $process = Start-Process `
        -FilePath 'powershell.exe' `
        -Verb RunAs `
        -WindowStyle Hidden `
        -Wait `
        -PassThru `
        -ArgumentList $arguments

    exit $process.ExitCode
}

Start-Transcript -LiteralPath $logPath -Force | Out-Null

try {
    $featureNames = @(
        'Microsoft-Windows-Subsystem-Linux'
        'VirtualMachinePlatform'
    )

    $restartNeeded = $false

    foreach ($featureName in $featureNames) {
        $feature = Get-WindowsOptionalFeature -Online -FeatureName $featureName

        if ($feature.State -eq 'Enabled') {
            Write-Output "$featureName is already enabled."
            continue
        }

        Write-Output "Enabling $featureName..."
        $result = Enable-WindowsOptionalFeature `
            -Online `
            -FeatureName $featureName `
            -All `
            -NoRestart

        $restartNeeded = $restartNeeded -or $result.RestartNeeded
        Write-Output "$featureName state: $($result.State)"
    }

    if ($restartNeeded) {
        Write-Output 'RESTART_REQUIRED=true'
    }
    else {
        Write-Output 'RESTART_REQUIRED=false'
    }
}
catch {
    Write-Error $_
    exit 1
}
finally {
    Stop-Transcript | Out-Null
}
