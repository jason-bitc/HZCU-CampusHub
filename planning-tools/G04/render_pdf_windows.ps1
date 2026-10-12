param([string]$PdfPath,[string]$OutputDir,[int]$StartPage=1,[int]$EndPage=0,[int]$RasterWidth=1400)
$ErrorActionPreference='Stop'
$PdfPath=[IO.Path]::GetFullPath($PdfPath)
$OutputDir=[IO.Path]::GetFullPath($OutputDir)
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null=[Windows.Storage.StorageFile,Windows.Storage,ContentType=WindowsRuntime]
$null=[Windows.Data.Pdf.PdfDocument,Windows.Data.Pdf,ContentType=WindowsRuntime]
$null=[Windows.Storage.Streams.InMemoryRandomAccessStream,Windows.Storage.Streams,ContentType=WindowsRuntime]
$null=[Windows.Data.Pdf.PdfPageRenderOptions,Windows.Data.Pdf,ContentType=WindowsRuntime]
$null=[Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime]
function Await-Operation($Operation,[type]$ResultType) {
    $method=[System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    } | Select-Object -First 1
    $task=$method.MakeGenericMethod($ResultType).Invoke($null,@($Operation))
    $task.GetAwaiter().GetResult()
}
function Await-Action($Action) {
    $method=[System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and -not $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction'
    } | Select-Object -First 1
    $task=$method.Invoke($null,@($Action))
    $task.GetAwaiter().GetResult()
}
[IO.Directory]::CreateDirectory($OutputDir) | Out-Null
$file=Await-Operation ([Windows.Storage.StorageFile]::GetFileFromPathAsync($PdfPath)) ([Windows.Storage.StorageFile])
$document=Await-Operation ([Windows.Data.Pdf.PdfDocument]::LoadFromFileAsync($file)) ([Windows.Data.Pdf.PdfDocument])
Write-Output ('Pages='+$document.PageCount)
if($EndPage -eq 0 -or $EndPage -gt $document.PageCount){$EndPage=$document.PageCount}
for($pageIndex=$StartPage-1;$pageIndex -lt $EndPage;$pageIndex++) {
    $page=$document.GetPage($pageIndex)
    $stream=New-Object Windows.Storage.Streams.InMemoryRandomAccessStream
    $options=New-Object Windows.Data.Pdf.PdfPageRenderOptions
    $options.DestinationWidth=if($page.Size.Width -gt $page.Size.Height){[int]($RasterWidth*9/7)}else{$RasterWidth}
    Await-Action ($page.RenderToStreamAsync($stream,$options))
    $stream.Seek(0)
    $reader=New-Object Windows.Storage.Streams.DataReader($stream.GetInputStreamAt(0))
    $count=Await-Operation ($reader.LoadAsync([uint32]$stream.Size)) ([uint32])
    $bytes=New-Object byte[] $count
    $reader.ReadBytes($bytes)
    [IO.File]::WriteAllBytes((Join-Path $OutputDir ('page-{0:D2}.png' -f ($pageIndex+1))),$bytes)
    $reader.Dispose();$stream.Dispose();$page.Dispose()
}
Write-Output ('Rendered='+($EndPage-$StartPage+1))
