param([string]$Config = "$PSScriptRoot/../.env.ops.json")
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    if (!(Test-Path -LiteralPath $Config)) { throw 'Create .env.ops.json following OPS_API.md.' }
    if (!(Test-Path -LiteralPath 'target/campushub-server-0.1.0.jar')) { throw 'Run mvn package before starting operations.' }
    & java '-Dloader.main=cn.edu.hzcu.ops.OpsApplication' -cp target/campushub-server-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --config $Config
    if ($LASTEXITCODE -ne 0) { throw 'Operations process stopped with an error.' }
} finally { Pop-Location }
