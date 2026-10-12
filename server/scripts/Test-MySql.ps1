$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
Push-Location (Join-Path $PSScriptRoot '..')
try {
    Invoke-CampusHubEnvironment {
        & mvn -B -ntp "-Dmaven.repo.local=$((Get-Location).Path)/.m2-cache" -Pmysql-it verify
        if ($LASTEXITCODE -ne 0) { throw 'MySQL verification failed. See target/failsafe-reports.' }
    }
}
finally { Pop-Location }
