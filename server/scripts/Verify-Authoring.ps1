param([int]$Port = 18082)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Use-LocalConfig.ps1"
$serverRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$target = Join-Path $serverRoot 'target'
$email = '32401211@stu.hzcu.edu.cn'
function Invoke-WritingApi($method, $path, $body = $null, $headers = @{}) {
    $args = @{ Uri = "http://127.0.0.1:$Port/api/v1$path"; Method = $method; Headers = $headers; TimeoutSec = 20 }
    if ($null -ne $body) { $args.ContentType = 'application/json; charset=utf-8'; $args.Body = $body | ConvertTo-Json -Depth 12 -Compress }
    try { return Invoke-RestMethod @args }
    catch { $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }; throw "Authoring verification API failed with HTTP $status. Sensitive values omitted." }
}
function Draft-Body($revision, $body) {
    return @{ expectedRevision=$revision; title='【DEMO】G04图文创作接口验证'; summary='用于后端保存、发布和图片访问验证的演示内容'; body=$body; jobIds=@(1); majorIds=@(11); companyIds=@(); topicIds=@(31); experienceStage='DEMO演示阶段'; sourceType='DEMO'; sourceNote='后端验证示例，不是本校亲历或已核验求职经历'; sourceLinks=@(@{label='Java官方资料';url='https://dev.java/learn/'}) }
}
Invoke-CampusHubEnvironment {
    $portCheck = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback,$Port)
    try { $portCheck.Start() } catch { throw 'Verification port is occupied; no existing process was modified.' } finally { $portCheck.Stop() }
    try { $account = Get-Content -LiteralPath (Join-Path $serverRoot '.env.account-test.json') -Raw | ConvertFrom-Json } catch { throw 'Local application password file is invalid; values omitted.' }
    if (!$account.password) { throw 'Existing application password is required.' }
    $jar = Join-Path $target 'campushub-server-0.1.0.jar'
    $process = Start-Process -FilePath (Get-Command java).Source -ArgumentList @('-jar', "`"$jar`"", "--server.port=$Port", '--campushub.db.name=campushub_dev', '--spring.profiles.active=demo') -WorkingDirectory $serverRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $target 'authoring.stdout.log') -RedirectStandardError (Join-Path $target 'authoring.stderr.log') -PassThru
    $headers = $null
    try {
        $ready = $false
        for ($attempt=0; $attempt -lt 100; $attempt++) {
            if ($process.HasExited) { throw 'Own authoring validation process exited before readiness.' }
            try { $health = Invoke-RestMethod "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 2; if ($health.data.status -eq 'UP') { $ready=$true; break } } catch { }
            Start-Sleep -Milliseconds 200
        }
        if (!$ready) { throw 'Own authoring validation readiness timed out.' }
        $login = Invoke-WritingApi 'POST' '/auth/login' @{ email=$email; password=$account.password }
        $headers = @{ Authorization='Bearer '+$login.data.accessToken }
        $profileBefore = (Invoke-WritingApi 'GET' '/me/profile' $null $headers).data | ConvertTo-Json -Depth 10 -Compress
        $seedBefore = (Invoke-WritingApi 'GET' '/articles/1001').data | ConvertTo-Json -Depth 15 -Compress
        $requestId = [Guid]::NewGuid().ToString()
        $created = Invoke-WritingApi 'POST' '/me/articles' @{ requestId=$requestId } $headers
        $article = $created.data.articleId
        $repeat = Invoke-WritingApi 'POST' '/me/articles' @{ requestId=$requestId } $headers
        if ($repeat.data.articleId -ne $article) { throw 'Create retry produced another article.' }
        $second = Invoke-WritingApi 'POST' '/me/articles' @{ requestId=[Guid]::NewGuid().ToString() } $headers
        $fixture = Get-Item -LiteralPath (Join-Path $target 'authoring-fixture.png')
        try { $upload = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/me/articles/$article/images" -Method Post -Headers $headers -Form @{ requestId=[Guid]::NewGuid().ToString();file=$fixture } -TimeoutSec 20 }
        catch { throw 'Real image upload failed; sensitive response omitted.' }
        $imageUrl = $upload.data.url
        $private = Invoke-WebRequest -Uri "http://127.0.0.1:$Port$imageUrl" -SkipHttpErrorCheck -TimeoutSec 5
        if ([int]$private.StatusCode -ne 404) { throw 'Private upload was accessible to guests.' }
        $body = "# DEMO图文验证`n`n本篇仅用于G04后端接口验证，不代表真实求职或实习经历。`n`n![DEMO测试图片]($imageUrl)`n`nMarkdown原文和来源链接由后端保存，页面由后续前端实现。"
        [void](Invoke-WritingApi 'PUT' "/me/articles/$article/draft" (Draft-Body 0 $body) $headers)
        $publishRequest = [Guid]::NewGuid().ToString()
        $published = Invoke-WritingApi 'POST' "/me/articles/$article/publish" @{ expectedRevision=1;requestId=$publishRequest } $headers
        $repeated = Invoke-WritingApi 'POST' "/me/articles/$article/publish" @{ expectedRevision=1;requestId=$publishRequest } $headers
        if ($published.data.versionId -ne $repeated.data.versionId) { throw 'Publication retry produced another public version.' }
        $detail = Invoke-WritingApi 'GET' "/articles/$article"
        if ($detail.data.body -cne $body -or $detail.data.counts.origin -ne 'NONE' -or $detail.data.counts.effectiveCoins -ne 0) { throw 'Published content/count contract did not match.' }
        $public = Invoke-WebRequest -Uri "http://127.0.0.1:$Port$imageUrl" -TimeoutSec 5
        if ([int]$public.StatusCode -ne 200 -or ($public.Headers['Cache-Control'] -join ',') -notmatch 'no-store') { throw 'Published image response did not match.' }
        $filtered = Invoke-WritingApi 'GET' '/articles?jobId=1&majorId=11&sort=latest'
        if (!(@($filtered.data.items | Where-Object id -EQ $article).Count)) { throw 'Published article not found with existing filters.' }
        [void](Invoke-WritingApi 'PUT' "/me/articles/$article/draft" (Draft-Body 2 ($body+"`n`n仅保存在草稿中的未发布修改。")) $headers)
        if ((Invoke-WritingApi 'GET' "/articles/$article").data.body -cne $body) { throw 'Draft editing changed the public body.' }
        $profileAfter = (Invoke-WritingApi 'GET' '/me/profile' $null $headers).data | ConvertTo-Json -Depth 10 -Compress
        $seedAfter = (Invoke-WritingApi 'GET' '/articles/1001').data | ConvertTo-Json -Depth 15 -Compress
        if ($profileBefore -cne $profileAfter -or $seedBefore -cne $seedAfter) { throw 'Original profile or demonstration article changed.' }
        $result = @{ status='PASS'; database='campushub_dev'; articleId=$article; sourceType='DEMO'; countOrigin='NONE'; guestPrivateImageStatus=404; publishedImageStatus=200; createRetrySameId=$true; publishRetrySameVersion=$true; independentDrafts=$true; draftEditDidNotChangePublicVersion=$true; originalProfilePreserved=$true; originalDemoDetailPreserved=$true; checkedAt=[DateTimeOffset]::Now.ToString('o') }
        $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $target 'authoring-result.json') -Encoding utf8
        @{ articleId=$article; otherDraftId=$second.data.articleId; imageId=$upload.data.imageId } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $serverRoot '.env.authoring-verification.json') -Encoding utf8
        Write-Output "Developer authoring loop passed: DEMO article $article published, guest image 200, private image 404, draft isolation preserved."
    }
    finally {
        if ($headers -and !$process.HasExited) { try { [void](Invoke-WritingApi 'POST' '/auth/logout' $null $headers) } catch { } }
        if (!$process.HasExited) { Stop-Process -InputObject $process; $process.WaitForExit() }
        $process.Dispose();$account=$null;$headers=$null;$login=$null
    }
}
