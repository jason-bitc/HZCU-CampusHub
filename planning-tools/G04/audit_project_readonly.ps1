$ErrorActionPreference='Stop'
$interop='C:\Windows\assembly\GAC_MSIL\Microsoft.Office.Interop.MSProject\15.0.0.0__71e9bce111e9429c\Microsoft.Office.Interop.MSProject.dll'
Add-Type -Path $interop
Add-Type -ReferencedAssemblies @($interop) -TypeDefinition @"
using System;
using System.IO;
using System.Text;
using Microsoft.Office.Interop.MSProject;
public static class FeasReadOnlyProject {
 static string Q(object x){return "\""+Convert.ToString(x).Replace("\"","\"\"")+"\"";}
 public static void Run(string path,string output){
  Application a=(Application)new ApplicationClass();
  int before=a.Projects.Count;bool opened=false;bool alerts=a.DisplayAlerts;Project p=null;
  try{
   a.DisplayAlerts=false;
   foreach(Project existing in a.Projects)if(String.Equals(existing.FullName,path,StringComparison.OrdinalIgnoreCase))p=existing;
   if(p==null){if(!a.FileOpenEx(Name:path,ReadOnly:true,NoAuto:true,IgnoreReadOnlyRecommended:true))throw new System.Exception("Read-only open failed");p=a.ActiveProject;opened=true;}
   var b=new StringBuilder("TaskID,Name,WBS,Summary,Start,Finish,DurationMinutes,Resources,Predecessors,TotalSlackMinutes,Critical,FixedCost,Cost\r\n");
   foreach(Task t in p.Tasks)if(t!=null){
    object[] fields={t.ID,t.Name,t.WBS,t.Summary,Convert.ToDateTime(t.Start).ToString("yyyy-MM-dd HH:mm"),Convert.ToDateTime(t.Finish).ToString("yyyy-MM-dd HH:mm"),t.Duration,t.ResourceNames,t.Predecessors,t.TotalSlack,t.Critical,t.FixedCost,t.Cost};
    for(int i=0;i<fields.Length;i++){if(i>0)b.Append(',');b.Append(Q(fields[i]));}b.Append("\r\n");
   }
   File.WriteAllText(Path.Combine(output,"native_tasks_readonly.csv"),b.ToString(),new UTF8Encoding(true));
   b=new StringBuilder("Name,Overallocated\r\n");foreach(Resource r in p.Resources)if(r!=null)b.AppendLine(Q(r.Name)+","+Q(r.Overallocated));
   File.WriteAllText(Path.Combine(output,"native_resources_readonly.csv"),b.ToString(),new UTF8Encoding(true));
  }finally{if(opened)a.FileClose(PjSaveType.pjDoNotSave);a.DisplayAlerts=alerts;if(before==0)a.Quit(PjSaveType.pjDoNotSave);}
 }
}
"@
$path=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\deliverables\G04-项目计划\图表与排期\城院帮项目计划G04.mpp'))
$out=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\.codex-build\feasibility-20261006'))
[FeasReadOnlyProject]::Run($path,$out)
Write-Output 'Read-only Project audit exported without saving the MPP.'
