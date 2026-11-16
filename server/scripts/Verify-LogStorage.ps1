param([int]$Port=18084)
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
$serverRoot=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$target=Join-Path $serverRoot 'target'
Invoke-CampusHubEnvironment {
    if (!$env:CAMPUSHUB_TEST_DB_USERNAME -or !$env:CAMPUSHUB_TEST_DB_PASSWORD) { throw 'Test database credentials required.' }
    $portCheck=[System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,$Port)
    try { $portCheck.Start() } catch { throw 'Verification port occupied; existing process preserved.' } finally { $portCheck.Stop() }
    $privateRoot=Join-Path $target ('batch4-log-failure-'+[Guid]::NewGuid().ToString())
    [void](New-Item -ItemType Directory -Path $privateRoot)
    $blocked=Join-Path $privateRoot 'blocked-log-directory'
    [System.IO.File]::WriteAllText($blocked,'preserve-existing-test-file')
    $oldUser=$env:CAMPUSHUB_DB_USERNAME;$oldPassword=$env:CAMPUSHUB_DB_PASSWORD
    $process=$null
    try {
        $env:CAMPUSHUB_DB_USERNAME=$env:CAMPUSHUB_TEST_DB_USERNAME;$env:CAMPUSHUB_DB_PASSWORD=$env:CAMPUSHUB_TEST_DB_PASSWORD
        $jar=Join-Path $target 'campushub-server-0.1.0.jar'
        $process=Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar',"`"$jar`"","--server.port=$Port",'--campushub.db.name=campushub_test','--spring.profiles.active=demo','--campushub.logs.enabled=true',"`"--campushub.logs.root=$blocked`"","`"--campushub.media.root=$privateRoot/media`"") -WorkingDirectory $serverRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $privateRoot 'stdout.log') -RedirectStandardError (Join-Path $privateRoot 'stderr.log') -PassThru
        if (!$process.WaitForExit(25000)) { throw 'Invalid log storage did not stop the own process in time.' }
        if ($process.ExitCode -eq 0) { throw 'Invalid log storage incorrectly started successfully.' }
        if ([System.IO.File]::ReadAllText($blocked) -cne 'preserve-existing-test-file') { throw 'Original test file changed.' }
        $output=[System.IO.File]::ReadAllText((Join-Path $privateRoot 'stdout.log'))+[System.IO.File]::ReadAllText((Join-Path $privateRoot 'stderr.log'))
        if (!$output.Contains('LOG_STORAGE_UNAVAILABLE')) { throw 'Log storage failure category missing.' }
        $up=$false
        try { $health=Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2; $up=$health.data.status -eq 'UP' } catch { }
        if ($up) { throw 'Failed own process unexpectedly reported readiness.' }
        @{status='PASS';database='campushub_test';exitCode=$process.ExitCode;healthUp=$false;blockingFilePreserved=$true;isolatedStorage=$true;checkedAt=[DateTimeOffset]::Now.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $target 'batch4-log-storage-result.json') -Encoding utf8
        Write-Output 'Packaged JAR rejected blocked log directory with nonzero exit; test file preserved, health not ready.'
    }
    finally {
        if ($process) { if (!$process.HasExited) { Stop-Process -InputObject $process; $process.WaitForExit() }; $process.Dispose() }
        $env:CAMPUSHUB_DB_USERNAME=$oldUser;$env:CAMPUSHUB_DB_PASSWORD=$oldPassword;$oldPassword=$null;$output=$null
    }
}
