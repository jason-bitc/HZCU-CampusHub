function Invoke-CampusHubEnvironment {
    param([scriptblock]$Action, [string]$RunModeOverride)
    $localFile = Join-Path $PSScriptRoot '../.env.local.json'
    $saved = @{}
    try {
        if (Test-Path -LiteralPath $localFile) {
            try { $config = Get-Content -LiteralPath $localFile -Raw | ConvertFrom-Json }
            catch { throw 'Local configuration JSON is invalid; values are omitted.' }
            foreach ($entry in $config.PSObject.Properties) {
                if ($entry.Name -notmatch '^CAMPUSHUB_(DB_(HOST|PORT|NAME|USERNAME|PASSWORD)|TEST_DB_(USERNAME|PASSWORD)|MAIL_(HOST|PORT|FROM|USERNAME|PASSWORD|TLS_MODE|TIMEOUT_MS)|AUTH_PEPPER|MEDIA_(ROOT|MAX_BYTES|MAX_PIXELS)|LOG_(ENABLED|ROOT|MAX_BYTES|ARCHIVES|DIAGNOSTICS)|COMMENT_(MAX_CODE_POINTS|JSON_MAX_BYTES)|RUN_MODE|RUNTIME_ENV|MULTIPART_MAX_BYTES|BODY_MAX_BYTES|JSON_MAX_BYTES)$') {
                    throw 'Unknown local configuration key.'
                }
                $saved[$entry.Name] = [Environment]::GetEnvironmentVariable($entry.Name, 'Process')
                [Environment]::SetEnvironmentVariable($entry.Name, [string]$entry.Value, 'Process')
            }
        }
        $secretFiles = @{ CAMPUSHUB_MAIL_PASSWORD = '../.env.smtp.txt'; CAMPUSHUB_AUTH_PEPPER = '../.env.auth-key.txt' }
        foreach ($name in $secretFiles.Keys) {
            $secretPath = Join-Path $PSScriptRoot $secretFiles[$name]
            if (Test-Path -LiteralPath $secretPath) {
                $secret = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $secretPath)).Trim()
                if (!$secret -or $secret -match '\s') { throw 'Local secret file must contain one nonempty value without whitespace.' }
                if (!$saved.ContainsKey($name)) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
                [Environment]::SetEnvironmentVariable($name, $secret, 'Process')
                $secret = $null
            }
        }
        if ($RunModeOverride) {
            if ($RunModeOverride -notmatch '^(?i:debug|release)$') { throw 'RUN_MODE_INVALID' }
            if (!$saved.ContainsKey('CAMPUSHUB_RUN_MODE')) { $saved['CAMPUSHUB_RUN_MODE'] = $env:CAMPUSHUB_RUN_MODE }
            $env:CAMPUSHUB_RUN_MODE = $RunModeOverride.ToLowerInvariant()
        }
        & $Action
    }
    finally {
        foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    }
}
