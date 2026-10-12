$ErrorActionPreference='Stop'
$path=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\deliverables\G04-项目计划\城院帮可行性分析报告G04.docx'))
$word=New-Object -ComObject Word.Application
$word.Visible=$false
$word.DisplayAlerts=0
try {
    $doc=$word.Documents.Open($path,$false,$false)
    foreach($styleName in @('Title','标题')) {
        try { $style=$doc.Styles.Item($styleName); foreach($border in $style.ParagraphFormat.Borders){$border.LineStyle=0}; $style.Font.Color=0 } catch {}
    }
    foreach($border in $doc.Paragraphs.Item(1).Borders){$border.LineStyle=0}
    foreach($styleName in @('TOC 1','目录 1')) {
        try { $style=$doc.Styles.Item($styleName); $style.Font.NameFarEast='微软雅黑'; $style.Font.Name='Microsoft YaHei'; $style.Font.Size=12; $style.ParagraphFormat.SpaceAfter=3; $style.ParagraphFormat.LineSpacingRule=0 } catch {}
    }
    $doc.Fields.Update() | Out-Null
    foreach($toc in $doc.TablesOfContents){$toc.Update()}
    $doc.Repaginate()
    foreach($toc in $doc.TablesOfContents){$toc.UpdatePageNumbers()}
    $doc.Save()
    $pdf=[IO.Path]::ChangeExtension($path,'.pdf')
    $doc.ExportAsFixedFormat($pdf,17,$false,0,0,1,1,0,$true,$true,1,$true,$true,$false)
    Write-Output ('Feasibility pages='+$doc.ComputeStatistics(2))
    $doc.Close(0)
} finally {$word.Quit()}
