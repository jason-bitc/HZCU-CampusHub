function Invoke-CampusHubEnvironment {
    param([scriptblock]$Action)
    $localFile = Join-Path $PSScriptRoot '../.env.local.json'
    $saved = @{}
    try {
        if (Test-Path -LiteralPath $localFile) {
            $config = Get-Content -LiteralPath $localFile -Raw | ConvertFrom-Json
            foreach ($entry in $config.PSObject.Properties) {
                if ($entry.Name -notmatch '^CAMPUSHUB_(DB_(HOST|PORT|NAME|USERNAME|PASSWORD)|TEST_DB_(USERNAME|PASSWORD))$') {
                    throw 'Unknown local configuration key.'
                }
                $saved[$entry.Name] = [Environment]::GetEnvironmentVariable($entry.Name, 'Process')
                [Environment]::SetEnvironmentVariable($entry.Name, [string]$entry.Value, 'Process')
            }
        }
        & $Action
    }
    finally {
        foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    }
}
