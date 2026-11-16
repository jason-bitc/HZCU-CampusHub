param([string]$Config = "$PSScriptRoot/../.env.ops.json")
$ErrorActionPreference = 'Stop'
$opsPassword = $null
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $settings = Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json
    $opsLogin = Read-Host 'Operations login (3-64 lowercase letters, digits, dot, dash, underscore)'
    $opsSecret = Read-Host 'New operations password (8-64 characters)' -AsSecureString
    $opsRepeat = Read-Host 'Repeat password' -AsSecureString
    $opsPassword = [Net.NetworkCredential]::new('', $opsSecret).Password
    $confirmation = [Net.NetworkCredential]::new('', $opsRepeat).Password
    if ($opsPassword -cne $confirmation -or $opsPassword -match '[\r\n]') { throw 'Passwords differ or contain a newline.' }
    @($opsLogin, $opsPassword, $confirmation) | & java '-Dloader.main=cn.edu.hzcu.ops.OpsApplication' -cp target/campushub-server-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --init $settings.controlRoot
    if ($LASTEXITCODE -ne 0) { throw 'Operations account initialization failed.' }
} finally {
    $opsPassword = $null; $confirmation = $null
    if ($opsSecret) { $opsSecret.Dispose() }; if ($opsRepeat) { $opsRepeat.Dispose() }
    Pop-Location
}
