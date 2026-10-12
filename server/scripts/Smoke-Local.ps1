param([int]$Port = 18080)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
$serverRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$target = Join-Path $serverRoot 'target'
$jar = Join-Path $target 'campushub-server-0.1.0.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Build first.' }
Invoke-CampusHubEnvironment {
    if ($env:CAMPUSHUB_DB_NAME -and $env:CAMPUSHUB_DB_NAME -ne 'campushub_dev') { throw 'Smoke uses campushub_dev only.' }
    $snapshots = @()
    for ($round = 1; $round -le 2; $round++) {
        $stdout = Join-Path $target "smoke-$round.stdout.log"
        $stderr = Join-Path $target "smoke-$round.stderr.log"
        $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', "`"$jar`"", "--server.port=$Port", '--campushub.db.name=campushub_dev', '--spring.profiles.active=demo') -WorkingDirectory $serverRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
        try {
            $ready = $false
            for ($attempt = 0; $attempt -lt 100; $attempt++) {
                if ($process.HasExited) { throw 'Own backend process exited before readiness. See target/smoke stderr log.' }
                try {
                    $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2
                    if ($health.code -eq 'OK' -and $health.data.status -eq 'UP') { $ready = $true; break }
                } catch { }
                Start-Sleep -Milliseconds 200
            }
            if (!$ready) { throw 'Own backend readiness check timed out.' }
            $catalog = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/catalog" -TimeoutSec 5
            $list = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/articles" -TimeoutSec 5
            $detail = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/articles/1001" -TimeoutSec 5
            if ($list.code -ne 'OK' -or $list.data.total -ne 6 -or $detail.data.bodyFormat -ne 'MARKDOWN') { throw 'Smoke response assertions failed.' }
            $snapshot = @{ catalog = $catalog.data; list = $list.data; detail = $detail.data } | ConvertTo-Json -Depth 20 -Compress
            $snapshots += $snapshot
            Write-Output "Developer JAR startup round $round passed: database health UP, public articles=6, detail=1001."
        }
        finally {
            # Only the process created by this script; no service or unrelated process is stopped.
            if (!$process.HasExited) { Stop-Process -InputObject $process; $process.WaitForExit() }
            $process.Dispose()
        }
    }
    if ($snapshots[0] -cne $snapshots[1]) { throw 'Developer database responses changed after restart.' }
    $result = @{ status = 'PASS'; database = 'campushub_dev'; port = $Port; startups = 2; publicArticles = 6; snapshotPreserved = $true; checkedAt = [DateTimeOffset]::Now.ToString('o') }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $target 'smoke-result.json') -Encoding utf8
    Write-Output 'Developer restart preserved catalogue, public list, counts and current public detail. Own processes stopped.'
}
