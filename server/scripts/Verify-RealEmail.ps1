param([ValidateSet('Request','Complete','Stop')][string]$Step = 'Request', [int]$Port = 18081)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
$serverRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$statePath = Join-Path $serverRoot '.env.mail-verification.json'
$evidencePath = Join-Path $serverRoot 'target/real-email-result.json'
$email = '32401211@stu.hzcu.edu.cn' # Only the human user's own mailbox is authorized for live testing.
function Invoke-AccountApi($path, $body, $headers = @{}) {
    try { return Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1$path" -Method Post -ContentType 'application/json' -Body ($body | ConvertTo-Json -Compress) -Headers $headers -TimeoutSec 35 }
    catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        throw "Live verification API failed with HTTP $status. No password, code or token is included in this message."
    }
}
if ($Step -eq 'Stop') {
    if (Test-Path -LiteralPath $statePath) {
        $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        $ownProcess = Get-Process -Id $state.processId -ErrorAction SilentlyContinue
        if ($ownProcess -and $ownProcess.StartTime.ToUniversalTime().Ticks.ToString() -eq $state.processStartedUtcTicks) {
            Stop-Process -InputObject $ownProcess
            Write-Output 'Own live-verification application stopped; MySQL and other services unchanged.'
        }
    }
    return
}
if ($Step -eq 'Request') {
    Invoke-CampusHubEnvironment {
        $jar = Join-Path $serverRoot 'target/campushub-server-0.1.0.jar'
        $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', "`"$jar`"", "--server.port=$Port", '--campushub.db.name=campushub_dev', '--spring.profiles.active=demo') -WorkingDirectory $serverRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $serverRoot 'target/real-email.stdout.log') -RedirectStandardError (Join-Path $serverRoot 'target/real-email.stderr.log') -PassThru
        $state = @{ processId = $process.Id; processStartedUtcTicks = $process.StartTime.ToUniversalTime().Ticks.ToString(); port = $Port; smtpAccepted = $false; recipientConfirmed = $false; codeVerified = $false }
        $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
        $ready = $false
        for ($attempt = 0; $attempt -lt 100; $attempt++) {
            if ($process.HasExited) { throw 'Live-verification backend exited before readiness. Inspect sanitized target logs.' }
            try { $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2; if ($health.data.status -eq 'UP') { $ready = $true; break } } catch { }
            Start-Sleep -Milliseconds 200
        }
        if (!$ready) { throw 'Live-verification backend readiness timed out.' }
        try {
            $reply = Invoke-AccountApi '/auth/registration-codes' @{ email = $email }
            $state.challengeId = $reply.data.challengeId; $state.expiresAt = $reply.data.expiresAt; $state.smtpAccepted = $true; $state.traceId = $reply.traceId
            $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
            @{ stage = 'SMTP_ACCEPTED'; recipientConfirmed = $false; codeVerified = $false; checkedAt = [DateTimeOffset]::Now.ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath $evidencePath -Encoding utf8
            Write-Output 'SMTP server accepted one registration email to the authorized user mailbox. Receipt and code verification are pending.'
            Write-Output ('Code expires at ' + $state.expiresAt)
        } catch {
            @{ stage = 'FAILED'; recipientConfirmed = $false; codeVerified = $false; checkedAt = [DateTimeOffset]::Now.ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath $evidencePath -Encoding utf8
            throw
        }
    }
    return
}
if ($Step -eq 'Complete') {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if (!$state.smtpAccepted -or $state.port -ne $Port) { throw 'No accepted challenge exists for this verification application.' }
    try { $account = Get-Content -LiteralPath (Join-Path $serverRoot '.env.account-test.json') -Raw | ConvertFrom-Json }
    catch { throw 'Local application password JSON is invalid; values are omitted.' }
    $code = [System.IO.File]::ReadAllText((Join-Path $serverRoot '.env.received-code.txt')).Trim()
    if ($code -notmatch '^\d{6}$' -or !$account.password) { throw 'A received six-digit code and locally chosen application password are required.' }
    $registration = Invoke-AccountApi '/auth/register' @{ email = $email; password = $account.password; challengeId = $state.challengeId; code = $code }
    $login = Invoke-AccountApi '/auth/login' @{ email = $email; password = $account.password }
    $headers = @{ Authorization = 'Bearer ' + $login.data.accessToken }
    try { $me = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/me" -Headers $headers -TimeoutSec 5 }
    catch { throw 'Live login status verification failed. Sensitive response omitted.' }
    if ($me.data.role -ne 'USER' -or $me.data.campusCertification -ne 'VALID') { throw 'Unexpected live account state.' }
    [void](Invoke-AccountApi '/auth/logout' @{} $headers)
    $denied = $false
    try { [void](Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/me" -Headers $headers -TimeoutSec 5) }
    catch { if ([int]$_.Exception.Response.StatusCode -eq 401) { $denied = $true } }
    if (!$denied) { throw 'Revoked live session was not rejected.' }
    $state.recipientConfirmed = $true; $state.codeVerified = $true
    $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
    @{ stage = 'REGISTER_LOGIN_LOGOUT_VERIFIED'; recipientConfirmed = $true; codeVerified = $true; normalRole = $true; campusCertification = 'VALID'; logoutRejected = $true; checkedAt = [DateTimeOffset]::Now.ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath $evidencePath -Encoding utf8
    Write-Output 'Received code verified; ordinary campus account created. Password login, own status and session logout passed. No secrets emitted.'
}
