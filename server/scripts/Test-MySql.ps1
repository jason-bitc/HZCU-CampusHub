$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
Push-Location (Join-Path $PSScriptRoot '..')
try {
    Invoke-CampusHubEnvironment {
        $savedMysql = $env:CAMPUSHUB_OPS_TEST_MYSQL
        $savedDump = $env:CAMPUSHUB_OPS_TEST_DUMP
        try {
        $env:CAMPUSHUB_OPS_TEST_MYSQL = (Get-Command mysql.exe -ErrorAction Stop).Source
        $env:CAMPUSHUB_OPS_TEST_DUMP = (Get-Command mysqldump.exe -ErrorAction Stop).Source
        & mvn -B -ntp "-Dmaven.repo.local=$((Get-Location).Path)/.m2-cache" -Pmysql-it verify
        if ($LASTEXITCODE -ne 0) { throw 'MySQL verification failed. See target/failsafe-reports.' }
        } finally {
            $env:CAMPUSHUB_OPS_TEST_MYSQL = $savedMysql
            $env:CAMPUSHUB_OPS_TEST_DUMP = $savedDump
        }
    }
}
finally { Pop-Location }
