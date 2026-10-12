$ErrorActionPreference='Stop'
$interopPath='C:\Windows\assembly\GAC_MSIL\Microsoft.Office.Interop.MSProject\15.0.0.0__71e9bce111e9429c\Microsoft.Office.Interop.MSProject.dll'
Add-Type -Path $interopPath
Add-Type -ReferencedAssemblies @($interopPath,'System.Xml.dll') -TypeDefinition @"
using System;
using System.IO;
using System.Text;
using System.Xml;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using Microsoft.Office.Interop.MSProject;
[ComImport, Guid("00000016-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IOleMessageFilter {
 [PreserveSig] int HandleInComingCall(int a,IntPtr b,int c,IntPtr d);
 [PreserveSig] int RetryRejectedCall(IntPtr a,int tickCount,int rejectType);
 [PreserveSig] int MessagePending(IntPtr a,int b,int c);
}
class G04FusedMessageFilter : IOleMessageFilter {
 [DllImport("ole32.dll")] static extern int CoRegisterMessageFilter(IOleMessageFilter next,out IOleMessageFilter previous);
 public static void Register(){IOleMessageFilter old;CoRegisterMessageFilter(new G04FusedMessageFilter(),out old);}
 public int HandleInComingCall(int a,IntPtr b,int c,IntPtr d){return 0;}
 public int RetryRejectedCall(IntPtr a,int ticks,int type){return (type==1 || type==2) && ticks<60000 ? 500 : -1;}
 public int MessagePending(IntPtr a,int b,int c){return 2;}
}
public static class G04FusedNativePlan {
 static string Q(object v){return "\""+Convert.ToString(v).Replace("\"","\"\"")+"\"";}
 public static string Export(string xml,string dir) {
  Directory.CreateDirectory(dir);
  G04FusedMessageFilter.Register();
  Console.WriteLine("Connecting native Project"); Application a=(Application)new ApplicationClass();
  Console.WriteLine("Suppress alerts"); a.DisplayAlerts=false;
  Console.WriteLine("Open XML"); if(a.Projects.Count>0 && String.Equals(a.ActiveProject.FullName,Path.Combine(dir,"城院帮项目计划G04.mpp"),StringComparison.OrdinalIgnoreCase)) a.FileClose(PjSaveType.pjDoNotSave); bool ok=false;
  if(!ok)ok=a.FileOpenEx(Name:xml,ReadOnly:false,Merge:0,NoAuto:true,FormatID:"MSProject.xml",IgnoreReadOnlyRecommended:true);
  if(!ok) throw new System.Exception("Project XML import returned false");
  Console.WriteLine("Read active project"); Project p=a.ActiveProject;
  p.Calendar.Years[2027].Months[1].Days[1].set_Working(false);
  for(int day=1;day<=7;day++)p.Calendar.Years[2026].Months[10].Days[day].set_Working(false);
  foreach(Resource r in p.Resources)if(r!=null){r.Calendar.Years[2027].Months[1].Days[1].set_Working(false);for(int day=1;day<=7;day++)r.Calendar.Years[2026].Months[10].Days[day].set_Working(false);}
  var source=new XmlDocument();source.Load(xml);var ns=new XmlNamespaceManager(source.NameTable);ns.AddNamespace("p","http://schemas.microsoft.com/project");
  foreach(Task t in p.Tasks) if(t!=null && !Convert.ToBoolean(t.Summary)){var n=source.SelectSingleNode("/p:Project/p:Tasks/p:Task[p:UID='"+t.ID+"']/p:Duration",ns);if(n!=null)t.Duration=(int)XmlConvert.ToTimeSpan(n.InnerText).TotalMinutes;t.Estimated=false;var c=source.SelectSingleNode("/p:Project/p:Tasks/p:Task[p:UID='"+t.ID+"']/p:FixedCost",ns);if(c!=null)t.SetField(PjField.pjTaskFixedCost,c.InnerText);}
  a.CalculateProject();
  foreach(Task t in p.Tasks)if(t!=null)t.Text2=(Convert.ToDouble(t.Duration)/480).ToString("0.##")+"日";
  Console.WriteLine("Export task audit");
  var b=new StringBuilder("TaskID,Name,WBS,Summary,Start,Finish,DurationMinutes,Resources,Predecessors,TotalSlackMinutes,Critical,EarlyStart,EarlyFinish,LateStart,LateFinish,FixedCost,Cost,FixedCostDisplay\r\n");
  int count=0;
  foreach(Task t in p.Tasks) if(t!=null) {
   if(!Convert.ToBoolean(t.Summary))count++;
   object[] fields={t.ID,t.Name,t.WBS,t.Summary,Convert.ToDateTime(t.Start).ToString("yyyy-MM-dd HH:mm"),Convert.ToDateTime(t.Finish).ToString("yyyy-MM-dd HH:mm"),t.Duration,t.ResourceNames,t.Predecessors,t.TotalSlack,t.Critical,Convert.ToDateTime(t.EarlyStart).ToString("yyyy-MM-dd HH:mm"),Convert.ToDateTime(t.EarlyFinish).ToString("yyyy-MM-dd HH:mm"),Convert.ToDateTime(t.LateStart).ToString("yyyy-MM-dd HH:mm"),Convert.ToDateTime(t.LateFinish).ToString("yyyy-MM-dd HH:mm"),t.FixedCost,t.Cost,t.GetField(PjField.pjTaskFixedCost)};
   for(int i=0;i<fields.Length;i++){if(i>0)b.Append(',');b.Append(Q(fields[i]));}b.Append("\r\n");
  }
  File.WriteAllText(Path.Combine(dir,"Project实际任务核查.csv"),b.ToString(),new UTF8Encoding(true));
  var views=new StringBuilder();foreach(View v in p.Views)views.AppendLine(v.Name);File.WriteAllText(Path.Combine(dir,"Project视图清单.txt"),views.ToString(),new UTF8Encoding(true));
  a.ViewApply(Name:"甘特图");
  a.OutlineShowAllTasks();
  foreach(Task t in p.Tasks) if(t!=null) { string note=Convert.ToString(t.Notes); int begin=note.IndexOf("任务负责人："); if(begin>=0) { string text=note.Substring(begin+6); int end=text.IndexOf("；"); t.Text1=end>=0?text.Substring(0,end):text; } else t.Text1=""; } Console.WriteLine("Create readable task table");
  a.TableEditEx(Name:"G04正式任务计划",TaskTable:true,Create:true,OverwriteExisting:true,NewFieldName:a.FieldConstantToFieldName(PjField.pjTaskID),Width:7,ShowInMenu:true,RowHeight:2,ShowAddNewColumn:false);
  PjField[] fieldIds={PjField.pjTaskName,PjField.pjTaskText2,PjField.pjTaskStart,PjField.pjTaskFinish,PjField.pjTaskPredecessors,PjField.pjTaskText1,PjField.pjTaskCost};
  int[] widths={38,7,12,12,12,23,7};
  for(int i=0;i<fieldIds.Length;i++) a.TableEditEx(Name:"G04正式任务计划",TaskTable:true,NewFieldName:a.FieldConstantToFieldName(fieldIds[i]),Width:widths[i],ColumnPosition:-1,Title:fieldIds[i]==PjField.pjTaskText1?"任务负责人":fieldIds[i]==PjField.pjTaskText2?"工期":a.FieldConstantToFieldName(fieldIds[i]),WrapText:true,HeaderTextWrap:true,DateFormat:PjDateFormat.pjDate_mm_dd_yy);
  a.TableApply(Name:"G04正式任务计划");
  Console.WriteLine("Format task bars");
  foreach(Task t in p.Tasks) if(t!=null && !Convert.ToBoolean(t.Summary))a.GanttBarFormat(TaskID:t.ID,RightText:a.FieldConstantToFieldName(PjField.pjTaskText1),MiddleColor:t.ResourceNames.Contains("王晨") && t.ResourceNames.Contains("刘景浩")?PjColor.pjPurple:t.ResourceNames.Contains("王晨")?PjColor.pjGreen:PjColor.pjBlue);
  a.ZoomTimescale(Entire:true);
  a.TimescaleEdit(MajorUnits:PjTimescaleUnit.pjTimescaleMonths,MinorUnits:PjTimescaleUnit.pjTimescaleWeeks,MinorCount:2,Enlarge:50,TierCount:2);
  a.FilePageSetupPage(Name:"甘特图",Portrait:false,PagesTall:0,PagesWide:1,PaperSize:PjPaperSize.pjPaperA3);
  a.FilePageSetupView(Name:"甘特图",AllSheetColumns:true,RepeatColumns:2,PrintBlankPages:false,BestPageFitTimescale:true);
  a.FilePageSetupLegend(Name:"甘特图",LegendOn:PjLegend.pjNoLegend);
  a.FilePageSetupHeader(Name:"甘特图",Alignment:PjAlignment.pjCenter,Text:"城院帮 G04 项目计划 | Microsoft Project 原生甘特图 | 青色王晨 蓝色刘景浩 紫色共同完成");
  if(String.Equals(p.FullName,Path.Combine(dir,"城院帮项目计划G04.mpp"),StringComparison.OrdinalIgnoreCase)) a.FileSave(); else a.FileSaveAs(Name:Path.Combine(dir,"城院帮项目计划G04.mpp"));
  Console.WriteLine("Export native Gantt PDF");
  a.DocumentExport(Filename:Path.Combine(dir,"Project详细甘特图G04.pdf"),FileType:PjDocExportType.pjPDF,IncludeDocumentProperties:false,IncludeDocumentMarkup:false,FromDate:new DateTime(2026,10,1),ToDate:new DateTime(2027,1,7));
  a.OutlineShowTasks(OutlineNumber:(PJTaskOutlineShowLevel)1);
  a.DocumentExport(Filename:Path.Combine(dir,"Project阶段总览G04.pdf"),FileType:PjDocExportType.pjPDF,IncludeDocumentProperties:false,IncludeDocumentMarkup:false,FromDate:new DateTime(2026,10,1),ToDate:new DateTime(2027,1,7));
  a.OutlineShowAllTasks();
  var resources=new StringBuilder("成员,最大分配比例,是否过载,日历\r\n");
  foreach(Resource r in p.Resources)if(r!=null)resources.AppendLine(Q(r.Name)+","+Q(r.MaxUnits)+","+Q(r.Overallocated)+","+Q(r.BaseCalendar));
  File.WriteAllText(Path.Combine(dir,"Project实际资源核查.csv"),resources.ToString(),new UTF8Encoding(true));
  a.ViewApply(Name:"资源工作表");
  Console.WriteLine("Create resource table");
  a.TableEditEx(Name:"G04正式资源核查",TaskTable:false,Create:true,OverwriteExisting:true,NewFieldName:a.FieldConstantToFieldName(PjField.pjResourceName),Width:12,ShowInMenu:true,RowHeight:2,ShowAddNewColumn:false);
  PjField[] resourceFields={PjField.pjResourceMaxUnits,PjField.pjResourceOverallocated,PjField.pjResourceBaseCalendar};
  foreach(PjField field in resourceFields)a.TableEditEx(Name:"G04正式资源核查",TaskTable:false,NewFieldName:a.FieldConstantToFieldName(field),Width:24,ColumnPosition:-1,WrapText:true);
  a.TableApply(Name:"G04正式资源核查");
  a.FilePageSetupPage(Name:"资源工作表",Portrait:false,PagesTall:1,PagesWide:1,PaperSize:PjPaperSize.pjPaperA4);
  Console.WriteLine("Export resource PDF");
  a.FilePageSetupHeader(Name:"资源工作表",Alignment:PjAlignment.pjCenter,Text:"G04 Microsoft Project 资源核查 | 包含管理与复核任务 | 占用参数不代表贡献比例");
  a.DocumentExport(Filename:Path.Combine(dir,"Project资源核查G04.pdf"),FileType:PjDocExportType.pjPDF,IncludeDocumentProperties:false,IncludeDocumentMarkup:false);
  a.ViewApply(Name:"甘特图");a.TableApply(Name:"G04正式任务计划");a.OutlineShowAllTasks();
  a.FileSave();
  a.Visible=true;
  return "Project "+a.Version+"; leaf tasks="+count+"; resources="+p.Resources.Count+"; saved native MPP and PDF";
 }
}
"@
$xmlPath='C:\Users\wangc\Desktop\chengyuanbang-mp\deliverables\G04-项目计划\图表与排期\MicrosoftProject任务源.xml'
$xml=New-Object Xml.XmlDocument;$xml.Load($xmlPath);$ns=New-Object Xml.XmlNamespaceManager($xml.NameTable);$ns.AddNamespace('p','http://schemas.microsoft.com/project')
$calendars=$xml.SelectSingleNode('/p:Project/p:Calendars',$ns)
foreach($resource in $xml.SelectNodes('/p:Project/p:Resources/p:Resource',$ns)) {
 $uid=[int]$resource.SelectSingleNode('p:UID',$ns).InnerText+2
 $calendar=$xml.SelectSingleNode('/p:Project/p:Calendars/p:Calendar[p:UID="'+$uid+'"]',$ns)
 if(-not $calendar){
  $calendar=$xml.CreateElement('Calendar',$ns.LookupNamespace('p'))
  foreach($pair in @(@('UID',[string]$uid),@('Name',$resource.SelectSingleNode('p:Name',$ns).InnerText),@('IsBaseCalendar','0'),@('BaseCalendarUID','1'))){$e=$xml.CreateElement($pair[0],$ns.LookupNamespace('p'));$e.InnerText=$pair[1];$calendar.AppendChild($e)|Out-Null}
  $calendars.AppendChild($calendar)|Out-Null
 }
 $entry=$resource.SelectSingleNode('p:CalendarUID',$ns);if(-not $entry){$entry=$xml.CreateElement('CalendarUID',$ns.LookupNamespace('p'));$resource.AppendChild($entry)|Out-Null};$entry.InnerText=[string]$uid
}
$xml.Save($xmlPath)
[G04FusedNativePlan]::Export($xmlPath,'C:\Users\wangc\Desktop\chengyuanbang-mp\deliverables\G04-项目计划\图表与排期')
