param([int]$Port = 8080, [ValidateSet('demo','schema-only')][string]$Profile = 'demo')
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
Push-Location (Join-Path $PSScriptRoot '..')
try { Invoke-CampusHubEnvironment {
    $jar = Join-Path $PSScriptRoot '../target/campushub-server-0.1.0.jar'
    if (!(Test-Path -LiteralPath $jar)) { throw 'Build first: mvn package' }
    & java -jar $jar "--server.port=$Port" "--spring.profiles.active=$Profile"
    if ($LASTEXITCODE -ne 0) { throw 'Backend did not start successfully; inspect configuration and migrations.' }
} } finally { Pop-Location }
