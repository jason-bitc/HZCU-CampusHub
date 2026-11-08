param(
 [switch]$RefreshManifest,
 [switch]$Commit,
 [switch]$Push,
 [string]$Message
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$docs=Join-Path $root 'docs/project'
$manifest=Join-Path $docs 'MANIFEST.sha256.csv'
$git=Get-Command git -ErrorAction Stop
function Run-Git([string[]]$Arguments) {
 & $git.Source -C $root @Arguments
 if($LASTEXITCODE -ne 0){throw ('Git failed: '+($Arguments -join ' '))}
}
$payload=@(Get-ChildItem -LiteralPath $docs -File -Recurse | Where-Object FullName -ne $manifest | Sort-Object FullName)
if($payload.Count -eq 0){throw 'No documents found in docs/project'}
if($RefreshManifest -or $Commit) {
 $payload | ForEach-Object {
  [pscustomobject]@{path=$_.FullName.Substring($docs.Length+1).Replace('\','/');bytes=[string]$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
 } | Export-Csv -LiteralPath $manifest -NoTypeInformation -Encoding UTF8
}
if(!(Test-Path -LiteralPath $manifest)){throw 'Run with -RefreshManifest first'}
$rows=@(Import-Csv -LiteralPath $manifest -Encoding UTF8)
$expected=@{}
foreach($r in $rows){
 if([IO.Path]::IsPathRooted($r.path) -or $r.path -match '(^|/)\.\.(/|$)' -or $r.path.Contains('\')){throw ('Invalid manifest path: '+$r.path)}
 if($expected.ContainsKey($r.path)){throw ('Duplicate path: '+$r.path)}
 $expected[$r.path]=$r
}
if($rows.Count -ne $payload.Count){throw 'Manifest count differs from document count'}
$allowed=@('.md','.pdf','.docx','.pptx','.mpp','.json','.csv','.xml','.txt','.drawio','.dot','.svg','.png','.jpg','.jpeg','.cdm','.ldm','.pdm')
foreach($p in $payload){
 $rel=$p.FullName.Substring($docs.Length+1).Replace('\','/')
 if($p.Name.StartsWith('~$') -or $p.Name.StartsWith('.env') -or $p.Extension.ToLowerInvariant() -notin $allowed){throw ('Disallowed document: '+$rel)}
 if($p.Length -gt 25MB){throw ('Document exceeds project 25 MiB limit: '+$rel)}
 $r=$expected[$rel]
 if($null -eq $r -or [string]$p.Length -ne $r.bytes -or (Get-FileHash -LiteralPath $p.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $r.sha256){throw ('Hash or size mismatch: '+$rel)}
 if($p.Extension.ToLowerInvariant() -in @('.docx','.pptx')) {
  if(!(Test-Path -LiteralPath ([IO.Path]::ChangeExtension($p.FullName,'.pdf')))){throw ('Missing PDF companion: '+$rel)}
  if($p.Extension -eq '.docx' -and !(Test-Path -LiteralPath ([IO.Path]::ChangeExtension($p.FullName,'.md')))){throw ('Missing Markdown companion: '+$rel)}
 }
}
Write-Output ('PASS: '+$payload.Count+' shared documents checked')
if($Commit -or $Push){
 $branch=(& $git.Source -C $root branch --show-current).Trim()
 if(!$branch -or $branch -in @('main','master')){throw 'Create a personal task branch before committing or pushing'}
}
if($Commit){
 if([string]::IsNullOrWhiteSpace($Message)){throw 'Use -Message with a concrete description of this update'}
 $staged=@(& $git.Source -C $root diff --cached --name-only)
 if($LASTEXITCODE -ne 0){throw 'Cannot inspect index'}
 if($staged.Count -gt 0){throw 'Existing staged files found; inspect or unstage them before using this helper'}
 # Only document and collaboration paths; never stage the whole workspace.
 $paths=@('docs/project','docs/GITHUB_COLLABORATION.md','docs/GIT_COLLABORATION.md','docs/FRONTEND_TECH_STACK.md','docs/PROJECT_PLAN.md','docs/REQUIREMENTS.md','scripts/Sync-ProjectDocs.ps1','scripts/check_project_docs.py','.github/workflows/documents.yml','.github/PULL_REQUEST_TEMPLATE.md','README.md','AGENTS.md','.gitattributes','.gitignore')
 Run-Git -Arguments (@('add','--')+$paths)
 & $git.Source -C $root diff --cached --quiet
 if($LASTEXITCODE -eq 1){Run-Git -Arguments @('commit','-m',$Message)}
 elseif($LASTEXITCODE -ne 0){throw 'Cannot inspect staged diff'}
 else{Write-Output 'No document changes to commit'}
}
if($Push){Run-Git -Arguments @('push','--set-upstream','origin',$branch)}
