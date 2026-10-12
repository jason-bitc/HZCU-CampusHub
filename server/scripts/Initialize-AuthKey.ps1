$ErrorActionPreference = 'Stop'
$keyPath = Join-Path $PSScriptRoot '../.env.auth-key.txt'
if (Test-Path -LiteralPath $keyPath) { Write-Output 'Existing local authentication key preserved.'; return }
$bytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
[System.IO.File]::WriteAllText($keyPath, [Convert]::ToBase64String($bytes))
Write-Output 'Local authentication key created in Git-ignored configuration. Preserve it across restarts.'
