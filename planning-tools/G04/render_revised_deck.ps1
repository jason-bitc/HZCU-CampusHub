param([string]$PresentationPath,[string]$OutputDir,[string]$PdfPath='')
$ErrorActionPreference='Stop'
$PresentationPath=[IO.Path]::GetFullPath($PresentationPath)
$OutputDir=[IO.Path]::GetFullPath($OutputDir)
[IO.Directory]::CreateDirectory($OutputDir) | Out-Null
$app=New-Object -ComObject PowerPoint.Application
$before=$app.Presentations.Count
$deck=$null
try {
    $deck=$app.Presentations.Open($PresentationPath,-1,0,0)
    foreach($slide in $deck.Slides){$slide.Export((Join-Path $OutputDir ('slide-'+$slide.SlideIndex.ToString('00')+'.png')),'PNG',1920,1080)}
    if($PdfPath){$deck.SaveAs([IO.Path]::GetFullPath($PdfPath),32)}
    Write-Output ('Rendered slides='+$deck.Slides.Count)
} finally {
    if($null -ne $deck){$deck.Close()}
    if($before -eq 0){$app.Quit()}
}
