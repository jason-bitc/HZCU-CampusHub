$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
Push-Location (Join-Path $PSScriptRoot '..')
$adminPassword = $null
$adminConfirmation = $null
try {
    New-Item -ItemType Directory -Path target -Force | Out-Null
    $adminLogin = Read-Host 'Community admin login (lowercase letters, digits, dot, dash, underscore; 3-64)'
    $adminSecret = Read-Host 'New admin password (8-64 characters)' -AsSecureString
    $adminRepeat = Read-Host 'Repeat new password' -AsSecureString
    $adminPassword = [System.Net.NetworkCredential]::new('', $adminSecret).Password
    $adminConfirmation = [System.Net.NetworkCredential]::new('', $adminRepeat).Password
    if ($adminPassword -cne $adminConfirmation -or $adminPassword -match '[\r\n]') { throw 'Admin passwords differ or contain a newline.' }
    Invoke-CampusHubEnvironment {
        & mvn -B -ntp "-Dmaven.repo.local=$((Get-Location).Path)/.m2-cache" -DskipTests package *> target/admin-setup-build.log
        if ($LASTEXITCODE -ne 0) { throw 'Admin setup build failed. No credentials were sent.' }
        @($adminLogin, $adminPassword, $adminConfirmation) | & java '-Dloader.main=cn.edu.hzcu.campushub.tools.InitializeCommunityAdmin' -cp target/campushub-server-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher
        if ($LASTEXITCODE -ne 0) { throw 'Admin local setup failed.' }
    }
}
finally {
    $adminPassword = $null
    $adminConfirmation = $null
    if ($adminSecret) { $adminSecret.Dispose() }
    if ($adminRepeat) { $adminRepeat.Dispose() }
    Pop-Location
}
