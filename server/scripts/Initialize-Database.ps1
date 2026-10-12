$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
Push-Location (Join-Path $PSScriptRoot '..')
try {
    Invoke-CampusHubEnvironment {
        & mvn -B -ntp "-Dmaven.repo.local=$((Get-Location).Path)/.m2-cache" -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/classpath.txt'
        if ($LASTEXITCODE -ne 0) { throw 'Build dependencies failed.' }
        $classpath = 'target/classes;' + (Get-Content target/classpath.txt -Raw).Trim()
        & java -cp $classpath cn.edu.hzcu.campushub.tools.InitializeDatabase
        if ($LASTEXITCODE -ne 0) { throw 'Project database initialization failed.' }
    }
}
finally { Pop-Location }
