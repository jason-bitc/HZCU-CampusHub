from pathlib import Path
from datetime import datetime,date,timedelta
import json,csv,math,subprocess,xml.etree.ElementTree as E
from PIL import Image,ImageDraw,ImageFont
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import landscape,A3
from pypdf import PdfReader,PdfWriter
ROOT=Path(r'C:\Users\wangc\Desktop\chengyuanbang-mp');OUT=ROOT/'deliverables/G04-项目计划';CH=OUT/'图表与排期';WORK=ROOT/'.codex-build/fused-qa';WORK.mkdir(exist_ok=True)
tasks=json.loads((CH/'任务数据.json').read_text(encoding='utf8'));subs=json.loads((CH/'子任务数据.json').read_text(encoding='utf8'))
HOL={date(2026,10,d) for d in range(1,8)}|{date(2027,1,1)};BASE=date(2026,10,8)
def off(s):
 x=datetime.fromisoformat(s);d=BASE;n=0
 while d<x.date():
  if d.weekday()<5 and d not in HOL:n+=1
  d+=timedelta(days=1)
 if x.weekday()<5 and x.date() not in HOL:n+=(min(4,max(0,x.hour+x.minute/60-9))+min(4,max(0,x.hour+x.minute/60-14)))/8
 return n
idx={t['code']:t for t in tasks};native={t['WBS']:t for t in csv.DictReader((CH/'Project实际任务核查.csv').open(encoding='utf-8-sig'))}
globalend=off('2027-01-06T18:00:00')
deadlines={'3.4':off('2026-10-23T18:00:00'),'4.5':off('2026-11-05T18:00:00'),'5.12':off('2026-11-27T18:00:00'),'6.7':off('2026-12-04T18:00:00'),'8.3':off('2026-12-24T18:00:00'),'8.5':globalend}
deadlines.update({c:off(d+'T18:00:00') for c,d in {'1.3':'2026-10-15','6.8':'2026-12-08','7.1':'2026-12-10','7.2':'2026-12-11','7.4':'2026-12-15','8.4':'2027-01-04'}.items()})
todo=set(idx);order=[]
while todo:
 ready=sorted((c for c in todo if not any(p in todo for p in idx[c]['preds'])),key=lambda c:[int(s) for s in c.split('.')]);assert ready
 order+=ready;todo-=set(ready)
es={};ef={};ls={};lf={}
for c in order:es[c]=max([off(idx[c]['start'])]+[ef[p] for p in idx[c]['preds']]);ef[c]=es[c]+idx[c]['duration']
for c in reversed(order):
 nexts=[n for n in order if c in idx[n]['preds']]
 lf[c]=min([deadlines.get(c,globalend)]+[ls[n] for n in nexts]);ls[c]=lf[c]-idx[c]['duration']
audit=[]
for t in tasks:
 c=t['code'];n=native[c]
 assert n['Start']==t['start'].replace('T',' ')[:16],(c,'start',n['Start'],t['start'])
 assert n['Finish']==t['finish'].replace('T',' ')[:16],(c,'finish',n['Finish'],t['finish'])
 assert int(n['DurationMinutes'])==t['duration']*480,(c,'duration')
 tf=round(ls[c]-es[c],4);nt=float(n['TotalSlackMinutes'])/480
 assert abs(tf-nt)<1e-6,(c,tf,nt)
 t.update(ES=es[c],EF=ef[c],LS=ls[c],LF=lf[c],float=tf,critical=tf==0)
 audit.append(dict(code=c,start=t['start'],finish=t['finish'],duration=t['duration'],float=tf,native_float=nt,match=True))
for q in subs:
 n=native[q['code']];assert n['Start']==q['start'].replace('T',' ')[:16] and n['Finish']==q['finish'].replace('T',' ')[:16],(q['code'],n)
events={};arcs=[]
for i,t in enumerate(tasks):
 a,b=i*2+1,i*2+2;events[a]=dict(EET=t['ES'],LET=t['LS']);events[b]=dict(EET=t['EF'],LET=t['LF']);t['a']=a;t['b']=b
 arcs.append(dict(code=t['code'],a=a,b=b,duration=t['duration'],float=t['float'],critical=t['critical'],kind='activity'))
for t in tasks:
 for p in t['preds']:arcs.append(dict(code='虚 '+p+'→'+t['code'],a=idx[p]['b'],b=t['a'],duration=0,float=round(events[t['a']]['LET']-events[idx[p]['b']]['EET'],4),critical=events[t['a']]['LET']==events[idx[p]['b']]['EET'],kind='dummy'))
# Merge identical chain events; this preserves date/CPM values while removing needless zero arcs.
alias={n:n for n in events}
def canon(n):
 while alias[n]!=n:n=alias[n]
 return n
for t in tasks:
 if len(t['preds'])==1:
  p=idx[t['preds'][0]];followers=[q for q in tasks if p['code'] in q['preds']]
  if len(followers)==1 and events[t['a']]==events[p['b']]:alias[t['a']]=canon(p['b'])
for t in tasks:t['a']=canon(t['a']);t['b']=canon(t['b'])
for a in arcs:a['a']=canon(a['a']);a['b']=canon(a['b'])
arcs=[a for a in arcs if a['a']!=a['b']]
critical={t['code'] for t in tasks if t['critical']};succ={c:[t['code'] for t in tasks if c in t['preds'] and t['critical']] for c in critical}
starts=[c for c in critical if not any(p in critical for p in idx[c]['preds'])];paths=[]
def walk(c,p):
 if not succ[c]:paths.append(p+[c])
 else:
  for n in succ[c]:walk(n,p+[c])
for c in sorted(starts):walk(c,[])
(CH/'工程网络计算.json').write_text(json.dumps(dict(origin='2026-10-08',finish='2027-01-06',total_workdays=globalend,calendar_release_constraints=True,deadline_constraints=deadlines,events=events,activities=arcs,critical_chains=paths,verification=audit),ensure_ascii=False,indent=2),encoding='utf8')
(CH/'关键路径说明.md').write_text('# 工程网络与关键路径核查\n\n排期从10月8日至1月6日，共'+str(globalend)+'个日历工作日。此为日期跨度，不是人员投入。\n\n事件值按工作日历、计划最早启动日期和内部交付截止计算；每项总时差已与Project逐项核对。课堂日期约束可能使独立活动成为关键控制点。\n\n'+ '\n'.join('- '+' → '.join(p) for p in paths)+'\n\n红色为零总时差活动，蓝色为有时差活动；虚活动仅表示依赖。5.4→5.5→5.9→5.10→5.11的连续安排包含刘景浩资源顺序，不全是技术强依赖。\n',encoding='utf8')
F=r'C:\Windows\Fonts\msyh.ttc';B=r'C:\Windows\Fonts\msyhbd.ttc';C={'王晨':'#236F79','刘景浩':'#355E9B','王晨、刘景浩':'#72549B'}
def font(n,b=False):return ImageFont.truetype(B if b else F,n)
def txt(d,xy,s,n=28,color='#172D3E',b=False,anchor=None):d.text(xy,s,font=font(n,b),fill=color,anchor=anchor)
def arrow(d,points,color,width=4,dash=False):
 for a,b in zip(points,points[1:]):
  if dash:
   L=math.dist(a,b)
   if L:
    for z in range(0,int(L),20):
     u=min(z+11,L);d.line((a[0]+(b[0]-a[0])*z/L,a[1]+(b[1]-a[1])*z/L,a[0]+(b[0]-a[0])*u/L,a[1]+(b[1]-a[1])*u/L),fill=color,width=width)
  else:d.line([a,b],fill=color,width=width)
 if len(points)>1:
  a,b=points[-2:];ang=math.atan2(b[1]-a[1],b[0]-a[0]);d.polygon([b,(b[0]-17*math.cos(ang-.45),b[1]-17*math.sin(ang-.45)),(b[0]-17*math.cos(ang+.45),b[1]-17*math.sin(ang+.45))],fill=color)

groups=[('分析设计_计划与需求',[t for t in tasks if t['code'][0] in '123' and t['code']!='3.5']),('分析设计_设计与复审',[t for t in tasks if t['code']=='3.5' or t['code'][0]=='4' and t['code']!='4.7']),('实现测试_模块实现',[t for t in tasks if t['code'][0]=='5' and t['code']!='5.12']),('实现测试_联调与验证',[t for t in tasks if t['code'] in ['4.7','5.12'] or t['code'][0]=='6']),('维护收尾_部署维护',[t for t in tasks if t['code'][0]=='7']),('维护收尾_总结交付',[t for t in tasks if t['code'][0]=='8'])]
js="import { instance } from '../../.codex-build/node_modules/@viz-js/viz/dist/viz.js';\nimport fs from 'node:fs';\nconst viz=await instance();\nconst input=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));\nfs.writeFileSync(process.argv[3],JSON.stringify(viz.renderJSON(input)));\n"
# Standalone utility remains local; no frontend project code is changed.
(WORK/'layout.mjs').write_text(js.replace("../../.codex-build/node_modules","../node_modules"),encoding='utf8')
mx=E.Element('mxfile',host='app.diagrams.net')
for name,group in groups:
 ids={t['a'] for t in group}|{t['b'] for t in group}
 edges=[a for a in arcs if a['a'] in ids and a['b'] in ids]
 outside=[a for a in arcs if a['b'] in ids and a['a'] not in ids]
 for a in outside:ids.add(a['a']);edges.append(a)
 dot=['digraph G { rankdir=LR; graph [nodesep=0.5,ranksep=0.25,splines=polyline]; node [shape=circle,width=0.55,height=0.55,fixedsize=true,label=""]; edge [fontsize=13];']
 for n in ids:dot.append(f'n{n};')
 for a in edges:
  label=f'{a["code"]}\\n{a["duration"]:g}d\\nTF={a["float"]:g}' if a['kind']=='activity' else ''
  dot.append(f'n{a["a"]} -> n{a["b"]} [label="{label}",minlen=1];')
 dot.append('}');src=WORK/(name+'.dot.json');src.write_text(json.dumps('\n'.join(dot)),encoding='utf8')
 layout=WORK/(name+'.layout.json')
 subprocess.run([r'C:\Users\wangc\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe',str(WORK/'layout.mjs'),str(src),str(layout)],check=True,capture_output=True)
 g=json.loads(layout.read_text(encoding='utf8'));bb=[float(x) for x in g['bb'].split(',')];S=2.9;W=int(bb[2]*S+180);H=int(bb[3]*S+260);im=Image.new('RGB',(W,H),'white');d=ImageDraw.Draw(im)
 txt(d,(55,24),name.replace('_',' '),36,b=True);txt(d,(55,82),'活动箭线标编号、工期、总时差；圆左为事件号，右上EET、右下LET；基准10月8日',24)
 obj={o['_gvid']:o for o in g['objects']};positions={}
 def pt(v):x,y=map(float,v.split(','));return (x*S+80,(bb[3]-y)*S+145)
 dia=E.SubElement(mx,'diagram',name=name);model=E.SubElement(dia,'mxGraphModel',page='1',pageWidth=str(W),pageHeight=str(H),grid='1');rt=E.SubElement(model,'root');E.SubElement(rt,'mxCell',id='0');E.SubElement(rt,'mxCell',id='1',parent='0')
 for a,e in zip(edges,g['edges']):
  # Graphviz preserves edge statement order in this directed graph.
  aa=int(obj[e['tail']]['name'][1:]);bbid=int(obj[e['head']]['name'][1:]);a=next(v for v in edges if v['a']==aa and v['b']==bbid)
  coords=[];end=None
  for v in e['pos'].split():
   if v.startswith('e,'):end=pt(v[2:])
   elif v.startswith('s,'):coords.append(pt(v[2:]))
   else:coords.append(pt(v))
  if end:coords.append(end)
  color='#B33D45' if a['critical'] else '#355E78';arrow(d,coords,color,4,a['kind']=='dummy')
  value=''
  if a['kind']=='activity':
   x,y=pt(e['lp']);t=idx[a['code']];label=[t['code']+' '+t['name'][:6],f'{t["duration"]:g}日  时差{t["float"]:g}日'];
   for z,line in enumerate(label):
    box=d.textbbox((0,0),line,font=font(24,True));w=box[2]-box[0];d.rectangle((x-w/2-5,y-27+z*30,x+w/2+5,y+2+z*30),fill='white');txt(d,(x,y-20+z*30),line,24,color,True,'mm')
   value='\n'.join(label)
  cell=E.SubElement(rt,'mxCell',id='e'+str(len(rt)),value=value,edge='1',parent='1',source='n'+str(aa),target='n'+str(bbid),style=f'endArrow=block;strokeColor={color};dashed={int(a["kind"]=="dummy")};fontSize=24;');E.SubElement(cell,'mxGeometry',relative='1',**{'as':'geometry'})
 for o in g['objects']:
  n=int(o['name'][1:]);x,y=pt(o['pos']);r=54;positions[n]=(x,y);d.ellipse((x-r,y-r,x+r,y+r),outline='#27495D',width=4);d.line((x,y-r,x,y+r),fill='#27495D',width=2);d.line((x,y,x+r,y),fill='#27495D',width=2)
  txt(d,(x-r/2,y),str(n),24,b=True,anchor='mm');txt(d,(x+r/2,y-24),f'{events[n]["EET"]:g}',21,anchor='mm');txt(d,(x+r/2,y+24),f'{events[n]["LET"]:g}',21,anchor='mm')
  cell=E.SubElement(rt,'mxCell',id='n'+str(n),value=f'{n} | {events[n]["EET"]:g}\n{events[n]["LET"]:g}',vertex='1',parent='1',style='ellipse;whiteSpace=wrap;html=0;fontSize=24;');E.SubElement(cell,'mxGeometry',x=str(x-r),y=str(y-r),width=str(2*r),height=str(2*r),**{'as':'geometry'})
 txt(d,(55,H-60),'红：零时差活动  蓝：有时差活动  虚线：0日依赖；日期锚点与内部截止参与计算；跨页同号事件一致',23)
 im.save(WORK/(name+'.png'));im.save(CH/(name+'.png'))
E.ElementTree(mx).write(CH/'工程网络图.drawio',encoding='utf-8',xml_declaration=True)
for section in ['分析设计','实现测试','维护收尾']:
 pieces=[Image.open(WORK/(name+'.png')) for name,_ in groups if name.startswith(section)]
 width=4000;heights=[int(p.height*width/p.width) for p in pieces];im=Image.new('RGB',(width,sum(heights)+80),'white');y=0
 for p,h in zip(pieces,heights):im.paste(p.resize((width,h),Image.Resampling.LANCZOS),(0,y));y+=h+40
 im.save(CH/('工程网络图_'+section+'.png'))

# Readable overall Gantt keeps complete dates in the native detailed attachment.
im=Image.new('RGB',(3000,1450),'white');d=ImageDraw.Draw(im);txt(d,(75,35),'城院帮项目阶段与成员任务总览',46,b=True);txt(d,(75,105),'10月8日执行排期至1月6日收尾；详细任务、依赖及资源以Microsoft Project文件为准',28)
stages=['项目计划与评审','可行性研究','需求分析','总体与详细设计','编码与单元测试','综合测试','部署与维护','总结与收尾'];first=date(2026,10,5);days=93;left=490;span=2360;top=260;rh=115
for i in range(14):
 x=left+i*7/days*span;d.line((x,top-40,x,top+rh*8+30),fill='#E0E5EA',width=2);txt(d,(x+4,top-75),(first+timedelta(days=i*7)).strftime('%m/%d'),23)
for i,s in enumerate(stages):
 y=top+i*rh;txt(d,(75,y+30),str(i+1)+' '+s,30,b=True)
 for lane,who in enumerate(['王晨','刘景浩']):
  for t in tasks:
   if t['code'].startswith(str(i+1)+'.') and who in t['owner']:
    a=datetime.fromisoformat(t['start']).date();b=datetime.fromisoformat(t['finish']).date();x1=left+(a-first).days/days*span;x2=left+((b-first).days+1)/days*span
    d.rounded_rectangle((x1,y+lane*36,x2,y+lane*36+30),radius=5,fill=C[who]);
txt(d,(75,1245),'王晨  组长与项目负责人',28,C['王晨'],True);txt(d,(830,1245),'刘景浩  开发、验证与文档协作',28,C['刘景浩'],True)
txt(d,(75,1310),'计划说明：双方共同文档会同时出现在两条成员行；组长持续管理与交叉复核已单列进入Project资源检查。',26)
im.save(CH/'阶段甘特总览.png')
old=ROOT/'archive/项目计划历史/20261001/deliverables/G04-project-plan-20261001/图表与排期/架构与部署.png'
(CH/'架构与部署.png').write_bytes(old.read_bytes())
c=canvas.Canvas(str(OUT/'工程网络与阶段图表G04.pdf'),pagesize=landscape(A3));pw,ph=landscape(A3)
for name in ['阶段甘特总览']+[name for name,_ in groups]+['架构与部署']:
 image=Image.open(CH/(name+'.png'));w,h=image.size;scale=min((pw-35)/w,(ph-40)/h);c.drawImage(str(CH/(name+'.png')),(pw-w*scale)/2,(ph-h*scale)/2,width=w*scale,height=h*scale);c.showPage()
c.save()
writer=PdfWriter()
for name in ['Project阶段总览G04.pdf','Project详细甘特图G04.pdf','Project资源核查G04.pdf']:writer.append(str(CH/name))
with (CH/'Project甘特图与资源核查G04.pdf').open('wb') as f:writer.write(f)
print(json.dumps({'verification':'48 packages and 95 subtasks match native Project','project_span_workdays':globalend,'chart_pages':8,'project_pdf_pages':len(writer.pages)},ensure_ascii=False))
