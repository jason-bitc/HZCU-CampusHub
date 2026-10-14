param([int]$Port = 18080, [switch]$Authoring)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
$serverRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$target = Join-Path $serverRoot 'target'
$jar = Join-Path $target 'campushub-server-0.1.0.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Build first.' }
Invoke-CampusHubEnvironment {
    if ($env:CAMPUSHUB_DB_NAME -and $env:CAMPUSHUB_DB_NAME -ne 'campushub_dev') { throw 'Smoke uses campushub_dev only.' }
    $portCheck = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,$Port)
    try { $portCheck.Start() } catch { throw 'Smoke port is occupied; existing process preserved.' } finally { $portCheck.Stop() }
    if ($Authoring) {
        try {
            $authoringIds = Get-Content -LiteralPath (Join-Path $serverRoot '.env.authoring-verification.json') -Raw | ConvertFrom-Json
            $account = Get-Content -LiteralPath (Join-Path $serverRoot '.env.account-test.json') -Raw | ConvertFrom-Json
        } catch { throw 'Local retained authoring configuration is invalid; values omitted.' }
        if (!$account.password -or !$authoringIds.articleId -or !$authoringIds.otherDraftId -or !$authoringIds.imageId) { throw 'Retained authoring verification needs its local IDs and application password.' }
    }
    $snapshots = @()
    for ($round = 1; $round -le 2; $round++) {
        $stdout = Join-Path $target "smoke-$round.stdout.log"
        $stderr = Join-Path $target "smoke-$round.stderr.log"
        $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', "`"$jar`"", "--server.port=$Port", '--campushub.db.name=campushub_dev', '--spring.profiles.active=demo') -WorkingDirectory $serverRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
        $sessionHeaders = $null
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
            if ($list.code -ne 'OK' -or $list.data.total -lt 6 -or $detail.data.bodyFormat -ne 'MARKDOWN') { throw 'Smoke response assertions failed.' }
            $snapshotData = @{ catalog = $catalog.data; list = $list.data; detail = $detail.data }
            if ($Authoring) {
                try {
                    $public = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/articles/$($authoringIds.articleId)" -TimeoutSec 5
                    $image = Invoke-WebRequest "http://127.0.0.1:$Port/api/v1/media/images/$($authoringIds.imageId)" -TimeoutSec 5
                    $loginBody = @{ email='32401211@stu.hzcu.edu.cn';password=$account.password } | ConvertTo-Json -Compress
                    $login = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 5
                    $sessionHeaders = @{Authorization='Bearer '+$login.data.accessToken}
                    $draft = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/me/articles/$($authoringIds.articleId)/draft" -Headers $sessionHeaders -TimeoutSec 5
                    $other = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/me/articles/$($authoringIds.otherDraftId)/draft" -Headers $sessionHeaders -TimeoutSec 5
                    $profile = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/me/profile" -Headers $sessionHeaders -TimeoutSec 5
                } catch { throw 'Retained authoring request failed; sensitive response omitted.' }
                if ($public.data.sourceType -ne 'DEMO' -or $draft.data.revision -ne 3 -or $other.data.revision -ne 0 -or $draft.data.content.body -ceq $public.data.body -or $image.StatusCode -ne 200) { throw 'Retained drafts/public image assertions failed.' }
                $snapshotData.authoring = @{ public=$public.data;draft=$draft.data;otherDraft=$other.data;profile=$profile.data;imageHash=[Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData([byte[]]$image.Content)) }
            }
            $snapshot = $snapshotData | ConvertTo-Json -Depth 20 -Compress
            $snapshots += $snapshot
            Write-Output "Developer JAR startup round $round passed: database health UP, public articles=$($list.data.total), detail=1001."
        }
        finally {
            if ($sessionHeaders -and !$process.HasExited) { try { [void](Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/auth/logout" -Method Post -Headers $sessionHeaders -TimeoutSec 5) } catch { } }
            # Only the process created by this script; no service or unrelated process is stopped.
            if (!$process.HasExited) { Stop-Process -InputObject $process; $process.WaitForExit() }
            $process.Dispose()
        }
    }
    if ($snapshots[0] -cne $snapshots[1]) { throw 'Developer database responses changed after restart.' }
    $result = @{ status = 'PASS'; database = 'campushub_dev'; port = $Port; startups = 2; publicArticles = $list.data.total; snapshotPreserved = $true; retainedAuthoringChecked = [bool]$Authoring; checkedAt = [DateTimeOffset]::Now.ToString('o') }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $target 'smoke-result.json') -Encoding utf8
    Write-Output 'Developer restart preserved catalogue, public list, counts and current public detail. Own processes stopped.'
    $account=$null;$login=$null;$loginBody=$null;$sessionHeaders=$null
}
