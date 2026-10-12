import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {PresentationFile, FileBlob} from 'file:///C:/Users/wangc/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs';

const root='C:/Users/wangc/Desktop/chengyuanbang-mp';
const work=path.join(root,'.codex-build/project-report-20261008');
const archive=path.join(root,'archive/项目汇报调整前/20261008');
const out=path.join(root,'deliverables/G04-项目计划');
const input=path.join(archive,'城院帮项目计划G04.pptx');
const original=path.join(out,'城院帮项目计划G04.pptx');
const official=path.join(out,'城院帮项目汇报G04.pptx');
const skill='C:/Users/wangc/.codex/plugins/cache/openai-primary-runtime/presentations/26.1007.11041/skills/presentations';
process.env.RUNTIME_NODE_MODULES='C:/Users/wangc/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
process.env.RUNTIME_NODE=process.execPath;
await fs.mkdir(work,{recursive:true}); await fs.mkdir(archive,{recursive:true});
try{await fs.access(input);}catch{await fs.copyFile(original,input);}
const hash=crypto.createHash('sha256').update(await fs.readFile(input)).digest('hex');
if(hash!=='1a33e0623e1b42c552a2322c7763a4f6742432f669ca68b9512911bf07302ad3')throw Error('Source has changed; inspect before authoring');
const p=await PresentationFile.importPptx(await FileBlob.load(input));
const source=[...p.slides.items];
const snap=await p.inspect({kind:'slide,shape,textbox,image,table,layout',maxChars:2500000});
await fs.writeFile(path.join(work,'source.ndjson'),snap.ndjson);
const records=snap.ndjson.split('\n').filter(x=>x.startsWith('{')).map(x=>JSON.parse(x)).map(r=>({...r,slide:r.slide??(Number.isInteger(r.slideIndex)?r.slideIndex+1:undefined)}));
const anchors=new Map(records.filter(r=>r.name&&r.slide).map(r=>[`${r.slide}:${r.name}`,r]));
const F='Noto Sans SC', C={ink:'#1D2938',muted:'#5D6879',teal:'#227482',blue:'#355F9D',purple:'#7656A3',line:'#DDE4EF'};
function edit(n,id,value,pt,bbox){
 const rec=anchors.get(`${n}:AutoShape ${id}`); if(!rec)throw Error(`Missing ${n}/${id}`);
 const s=p.resolve(rec.id); s.text=value;
 if(pt)s.text.style={typeface:F,fontSize:pt*4/3,wrap:'square',autoFit:'none',verticalAlignment:n===2?'middle':'top'};
 if(bbox)s.position={left:bbox[0],top:bbox[1],width:bbox[2],height:bbox[3]};
 return s;
}
function text(sl,name,value,x,y,w,h,pt=20,color=C.ink,bold=false){
 const s=sl.shapes.add({geometry:'textbox',name,position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});
 s.text=value; s.text.style={typeface:F,fontSize:pt*4/3,color,bold,wrap:'square',autoFit:'none',verticalAlignment:'top'}; return s;
}
function rule(sl,name,x,y,w,color=C.line){sl.shapes.add({geometry:'rect',name,position:{left:x,top:y,width:w,height:1},fill:color,line:{fill:'none',width:0}});}
const added=new Map();
function page(key,title,notes){
 const sl=p.slides.add(); sl.background.fill='#FFFFFF';
 text(sl,'Title',title,75,54,1130,73,32,C.ink,true);
 rule(sl,'Title accent',75,123,100,C.teal);
 rule(sl,'Footer rule',75,648,1130);
 text(sl,'Deck footer','城院帮项目汇报 G04',75,658,875,29,11,C.muted);
 text(sl,'Page marker','',1115,658,90,29,11,C.muted);
 sl.speakerNotes.textFrame.setText(notes);
 added.set(key,sl); return sl;
}
function table(sl,name,values,widths,{top=190,height=360,pt=17}={}){
 const tb=sl.tables.add({rows:values.length,columns:values[0].length,left:75,top,width:1130,height,columnWidths:widths,values});
 tb.styleOptions={headerRow:false,bandedRows:false};
 tb.borders.assign({style:'solid',fill:C.line,width:1});
 tb.cells.block({row:0,column:0,rowCount:values.length,columnCount:values[0].length}).assign({
  margins:{left:14,right:14,top:10,bottom:10},anchor:'center',
  textStyle:{typeface:F,fontSize:pt*4/3,color:C.ink,wrap:'square',autoFit:'none'}
 });
 for(let r=0;r<values.length;r++){
  tb.rows[r].height=r===0?52:(height-52)/(values.length-1);
  for(let c=0;c<values[0].length;c++){
   const cell=tb.getCell(r,c); cell.fill=r===0?C.teal:r%2?'#FFFFFF':'#F5F7FA';
   cell.text.style={typeface:F,fontSize:pt*4/3,color:r===0?'#FFFFFF':C.ink,bold:r===0,wrap:'square',autoFit:'none'};
  }
 }
 return tb;
}

let sl=page('A','项目背景与问题','依据[11]本组前期校友圈观察与需求讨论，以及[15]可行性报告§2。这里是用户问题与观察，不是全校需求统计。');
text(sl,'Situation','大三大四准备投递与面试时，希望找到与自己背景接近的亲历经验。',75,170,1130,83,25,C.teal,true);
for(const [y,label,body] of [
 [285,'资料难筛选','经验分散在校园社区和社交平台，读者需要判断岗位、时间和个人背景是否适用。'],
 [395,'交流难延续','零散求问依赖当时是否有人看到，有价值的回复较难沉淀为持续可检索的经验。'],
 [505,'联系渠道有限','不认识有相关经历的学长学姐，或不便开口时，获取进一步解释的渠道较少。']]){
 text(sl,`Label ${y}`,label,75,y,250,40,22,C.ink,true);
 text(sl,`Body ${y}`,body,345,y,860,83,20,C.muted);
}

sl=page('B','现状观察与项目定位','依据[11]本组校友圈界面截图与需求讨论、[15]可行性报告§2.1。已有社区有搜索、认证、招聘等功能，不能说这些能力缺失。下表是小样本观察与拟改进方式，不代表正式竞品全量功能审计。');
text(sl,'Observed','已有校园社区提供搜索、身份认证和招聘入口，本项目重点改善经验的组织与复用。',75,165,1130,76,22,C.teal,true);
table(sl,'Observed gaps',[
 ['现有获取方式','本组观察到的问题','城院帮拟改善的环节'],
 ['按新帖浏览综合社区','经验与求问混杂，优质内容容易被更新覆盖','岗位入口，同专业与公司筛选'],
 ['发帖等待他人回复','回答零散，是否得到回复存在不确定性','经验文章沉淀，评论继续交流'],
 ['社交平台与熟人渠道','亲历背景及适用范围需要反复判断','显示专业、经历阶段与认证状态']
],[280,455,395],{top:260,height:277,pt:18});
text(sl,'Evidence limit','证据来自前期截图和需求讨论，尚无系统访谈或全校需求统计。',75,573,1130,60,18,C.muted);

sl=page('C','项目总体目标','依据[14]项目计划§1、§2、§8、§9及[15]可行性报告§2.2。用户效益为待验证目标，课程交付按核心流程和证据验收。');
text(sl,'User goal label','用户目标',75,181,1100,49,24,C.teal,true);
text(sl,'User goal','持续找到同岗位、相近背景的亲历经验\n围绕文章交流投递与面试中的具体问题',75,242,1130,109,23);
text(sl,'Course goal label','课程交付目标',75,390,1100,49,24,C.blue,true);
text(sl,'Course goal','交付小程序、读者作者网页、社区管理网页和独立运维网页。\n完成阅读检索、认证互动、双端创作、举报处理和部署恢复等核心流程。\n用需求、设计、测试及维护材料说明实现与验收过程。',75,450,1130,150,20);
text(sl,'Initial content','平台结构面向全校，首批演示内容集中在计算机相关专业。',75,607,1130,32,17,C.muted);

sl=page('D','读者使用场景','依据[14]项目计划§2、§8、[15]可行性报告§8.1。Java后端场景为说明产品流程的假设示例，不是已经发生的个人实习经历，也不是旧原型的真实运行截图。');
text(sl,'Scenario','示例：准备投递Java后端实习，希望参考同专业同学的面试经历。',75,164,1130,75,23,C.teal,true);
const reader=[
 ['选择岗位','进入岗位经验目录，按“与我相同专业”筛选，公司可选。'],
 ['阅读经验','了解当时基础、准备方式和面试流程，判断是否适用。'],
 ['认证后交流','收藏以后查阅，评论追问细节，点赞或投币表达帮助程度。'],
 ['自愿联系作者','认证后访问公开主页，按作者意愿获得站外联系方式。']
];
reader.forEach(([h,b],i)=>{const y=262+i*88;text(sl,`Step ${i+1}`,String(i+1),75,y,64,59,30,C.teal,true);text(sl,`Action ${i+1}`,h,160,y,310,49,22,C.ink,true);text(sl,`Detail ${i+1}`,b,490,y,715,75,19);});

sl=page('E','作者创作与激励','依据[14]项目计划§2、§8及[15]可行性报告§8.1–8.2。非现金激励不折算收益；未确认作者数量和持续写作承诺，不推定已经形成供给。');
text(sl,'Creator group','认证用户可以分享亲历，重点邀请已有实习或工作经历的人。',75,164,1130,71,23,C.teal,true);
text(sl,'Creation label','降低创作负担',75,265,535,46,23,C.ink,true);
text(sl,'Creation body','只填写少量必要背景\n正文写作提示可以跳过\n网页Markdown适合长文\n小程序支持文字、图片与草稿\n资料可通过网盘链接分享',75,323,535,253,20);
text(sl,'Motivation label','让分享得到反馈',685,265,520,46,23,C.ink,true);
text(sl,'Motivation body','岗位目录让经验持续被找到\n评论为作者提供交流反馈\n点赞表示认可，投币表示特别有帮助\n累计获币形成称号与勋章\n作者自愿公开站外联系方式',685,323,520,253,20);
text(sl,'Limit','文章免费阅读，币不充值、不兑换现金。作者参与和持续供给仍需真实反馈验证。',75,603,1130,35,16,C.muted);

sl=page('F','可行性分析与方案比较','依据[15]可行性报告§3。方案须覆盖既定终端和核心流程，并能纳入两人与课程周期；失败替代须评估范围变化，不是默许删减交付。');
table(sl,'Options',[
 ['方案','实现与维护考虑','当前选择'],
 ['uni-app双端与Java单体','共享接口，部署组件较少，需验证编辑差异','推荐，优先验证关键风险'],
 ['原生小程序与独立网页','双前端重复实现，维护与测试负担较大','双端框架失败时再比较'],
 ['仅网页社区','实现路径较短，未覆盖小程序交付','须确认范围变更'],
 ['微服务平台','增加服务治理、部署和故障排查工作','当前规模不采用'],
 ['沿用已有校园社区','无需新建，难控制经验组织与创作流程','作为不开发的比较基准']
],[300,500,330],{top:175,height:397,pt:17.5});
text(sl,'Selection','选择依据是交付范围与维护负担。技术路线明确，完整功能仍需实现后验证。',75,601,1130,38,17,C.muted);

sl=page('G','技术可行性与关键验证','依据[15]可行性报告§5、附录C。FV01–FV08分别覆盖双端、邮箱、编辑、币、独立运维、恢复、治理安全和性能。2.1早期样例核查与5.x、6.x完整验收分开，当前状态待验证。');
table(sl,'Technical evidence',[
 ['关键问题','验证内容','证据与状态'],
 ['双端构建与图文创作','共同接口，保存重开，图片失败与跨端冲突','构建与操作记录，待验证'],
 ['身份认证与治理权限','发信异常，资料保护，举报定位与直接接口越权','认证与权限记录，待验证'],
 ['非现金币一致性','事务与幂等，同一业务不重复扣发，余额不负','数据库断言，待验证'],
 ['独立运维与备份恢复','业务停止后入口可用，备份含数据库与图片','日志与前后核对，待验证'],
 ['性能与压力','固定数据和JMeter脚本，记录P95与容量边界','JTL与资源趋势，待验证']
],[290,530,310],{top:175,height:397,pt:17.5});
text(sl,'Verification boundary','早期核查确认路线能否走通，完整功能与性能按后续真实版本验收。',75,600,1130,39,17,C.muted);

sl=page('H','经济与资源可行性','依据[15]可行性报告§6与[14]项目计划§10。沿用2026-10-01预算基准，4个月各1份订阅。Plus 80美元×6.7351=538.81元；豆包38元/月为小组提供价格。总额为估算，实际按账单记录。来源[9][10]见参考资料，不重定价或推定支出已发生。');
text(sl,'Financial conclusion','现有设备和限定范围下，计划现金投入可承担。',75,166,1130,66,24,C.teal,true);
table(sl,'Budget',[
 ['预算项','数量与口径','4个月费用（元）'],
 ['ChatGPT Plus','1份，20美元/月，按80美元折算','538.81'],
 ['豆包订阅','1份，38元/月，小组提供单价','152.00'],
 ['其他新增费用','设备与可选服务器复用，工具用现有条件','0.00'],
 ['合计','2026年10月至2027年1月','690.81']
],[280,590,260],{top:245,height:300,pt:18});
text(sl,'Financial boundary','本项目采用免费阅读与非现金激励，不设收入或投资回收承诺。\n课堂版本不依赖新增云资源采购，长期运营成本需要另行评估。',75,575,1130,69,18,C.muted);

sl=page('I','操作与持续运营可行性','依据[15]可行性报告§8–§11。说明的是课程版本的操作和维护安排，不宣称已经取得长期运营、真实用户供给或上线条件。资料引用与认证隐私边界沿用已确认范围。');
table(sl,'Operations',[
 ['对象','课程版本中的安排','需要进一步确认'],
 ['读者与作者','先浏览后认证，少量背景，双端图文创作','真实试用是否容易完成'],
 ['社区管理','先发布后管理，举报定位原文评论，保存理由','真实巡查与持续管理责任'],
 ['部署与维护','本地课堂演示，独立网页执行固定运维动作','冷启动、失败保护与交接记录']
],[270,555,305],{top:188,height:303,pt:18});
text(sl,'Content types','初期内容区分原创亲历、公开链接引用与合成演示资料。\n付费参考资料的学习权限不直接作为转载依据。\n真实邮箱与认证材料仅供有权角色访问。',75,517,1130,88,19);
text(sl,'Operating limit','作者供给仍待验证。课堂演示的可行性需要与长期运营结论分别判断。',75,617,1130,27,15.5,C.muted);

sl=page('J','项目管理与变更控制','依据[14]项目计划§7、§10–§11和[15]可行性报告§10。学生是负责人，AI是辅助工具。管理方法覆盖范围、进度、成本、风险、质量、沟通和配置，未推定实际会议已发生。');
for(const [i,h,b] of [
 [0,'每周进度检查','王晨组织检查产物、阻塞与预测完成日期，刘景浩同步模块状态，双方记录交接。'],
 [1,'范围与风险控制','新需求先分析范围、日期、预算及验收影响，双方确认后同步任务和相关材料。'],
 [2,'AI使用责任','两人按分工使用Codex、豆包和WorkBuddy，人工审查、集成并解释各自模块。'],
 [3,'质量与版本基线','用真实测试证据关闭缺陷，保留文档和代码版本，延期时保护必要验收。']]){
 const y=185+i*111;text(sl,`Control ${i}`,h,75,y,310,48,22,C.teal,true);text(sl,`Control explanation ${i}`,b,410,y,795,88,20);
}

// Existing slides keep their editable objects and reviewed layout.
edit(1,4,'城院帮项目汇报',44,[96,211,780,91]);
edit(1,8,'项目介绍、可行性分析与实施计划',19.5);
edit(1,2,'SOFTWARE ENGINEERING 课程项目',14);
edit(2,10,'项目介绍',22);edit(2,11,'背景、现状、目标用户与核心功能',19);
edit(2,15,'可行性分析',22);edit(2,16,'方案、技术、经济与运营条件',19);
edit(2,20,'实施计划与分工',22);edit(2,21,'工程方法、工具、WBS及排期',19);
edit(2,25,'质量与运行维护',22);edit(2,26,'测试验收、独立运维与异常处理',19);
edit(2,30,'项目管理与交付',22);edit(2,31,'风险、变更、配置、评价与资料',19);
edit(2,33,'正文31页，附录保留95项子任务、14项周管理、补充网络图和16项评审对照。',15);
edit(19,2,'可行性结论与课程交付',32);
edit(19,7,'依据 [14][15]',11);
source[18].speakerNotes.textFrame.setText('依据[14]项目计划§6和[15]可行性报告§1、§5、§11。限定范围及本地演示条件下有条件可行，产品技术证据当前待验证。课堂节点与前周日22时的邮件截止分别处理，不把计划日期当作实际提交记录。');

const order=[source[0],source[1],added.get('A'),added.get('B'),added.get('C'),source[2],source[3],source[4],added.get('D'),added.get('E'),added.get('F'),added.get('G'),added.get('H'),added.get('I'),source[18],source[5],source[6],source[7],source[8],source[9],source[10],source[11],source[12],source[13],source[14],source[15],source[16],source[17],added.get('J'),source[19],source[20],...source.slice(21)];
if(order.length!==58)throw Error('Unexpected deck size');
order.forEach((s,i)=>s.moveTo(i));
const sourceMap=Object.fromEntries(source.map((s,i)=>[i+1,order.indexOf(s)+1]));
const replaceNumbers=s=>s.replace(/第(\d+(?:[、,–—]\d+)*)页/g,(m,sequence)=>
 '第'+sequence.replace(/\d+/g,n=>String(sourceMap[n]??n))+'页');
for(const r of records.filter(r=>r.kind==='textbox')){
 const s=p.resolve(r.id);let value=String(s.text);
 if(r.text==='城院帮项目计划 · G04 · 计划阶段评审')value='城院帮项目汇报 G04';
 if(/^\d{2} \/ 48$/.test(r.text))value=`${String(sourceMap[r.slide]).padStart(2,'0')} / 58`;
 if(r.slide===47||r.slide===48)value=replaceNumbers(value);
 if(r.slide===47&&r.text==='本PPT第1–48页及实际导出')value='本PPT第1–58页及实际导出';
 if(r.slide===47&&r.text.startsWith('可行性报告§5、附录C'))value='可行性报告§3–11、附录C；本PPT第11–15页';
 if(r.slide===48&&r.text.startsWith('项目计划报告§10；本PPT第18、23页'))value='项目计划报告§10；本PPT第13、28、33页';
 if(value!==String(s.text))s.text=value;
}
for(const [key,newSlide] of added){
 const sh=newSlide.shapes.items.find(s=>s.name==='Page marker');
 sh.text=`${String(order.indexOf(newSlide)+1).padStart(2,'0')} / 58`;
}
// Add the reports themselves to the reference list, with stable identifiers.
const refSlide=source[20];
for(const s of refSlide.shapes.items)if(s.name==='Reference list'){s.text='';s.fill='none';s.line={fill:'none',width:0};}
text(refSlide,'References left',[
 '[1] 第13章 软件项目管理，课程课件，97页',
 '[2] 第1章 软件工程学概述，课程课件，78页',
 '[3] 计划阶段16项评审表与PPT7项评审表',
 '[4] 课程计划、课程工具及团队作业通知',
 '[5] Microsoft Project关键路径官方说明',
 '[6] Apache JMeter官方用户手册',
 '[7] Spring Boot Testing',
 '[8] Bugzilla Features'
].join('\n'),75,166,560,455,17);
text(refSlide,'References right',[
 '[9] OpenAI Pricing',
 '[10] 中国银行牌价，预算基准2026/10/1',
 '[11] 本组校友圈观察及需求讨论资料',
 '[12] 张海藩、牟永敏 软件工程导论 第6版',
 '清华大学出版社，2013',
 '[13] Kathy Schwalbe IT项目管理',
 '机械工业出版社，版次待核对',
 '[14] 城院帮项目计划G04，45页',
 '[15] 城院帮可行性分析报告G04，26页'
].join('\n'),680,166,525,455,17);
refSlide.speakerNotes.textFrame.setText('[5] https://support.microsoft.com/en-US/project/show-the-critical-path-of-your-project-in-project\n[6] https://jmeter.apache.org/usermanual/generating-dashboard.html\n[7] https://docs.spring.io/spring-boot/reference/testing/\n[8] https://www.bugzilla.org/about/features.html\n[9] https://learn.chatgpt.com/docs/pricing\n[10] https://www.boc.cn/sourcedb/whpj/\n[12] ISBN 9787302330981 https://www.tup.com.cn/booksCenter/book_05016406.html\n[13] https://www.cmpedu.com/books/book/2052625.htm 仅目录参考，课程版次待核对\n[14] deliverables/G04-项目计划/城院帮项目计划G04.pdf\n[15] deliverables/G04-项目计划/城院帮可行性分析报告G04.pdf\n[11] 前期校友圈截图和需求讨论，不构成全校统计。教材仅核对已取得材料及公开目录，不声称完整阅读。');

const after=await p.inspect({kind:'slide,textbox,table,notes',maxChars:2500000});
await fs.writeFile(path.join(work,'expanded.ndjson'),after.ndjson);
const manifest={title:'城院帮项目汇报',total:58,body:31,appendix:27,source:input,sourceSha256:hash,sourceSlideMap:sourceMap,
 addedSlides:Object.fromEntries([...added].map(([key,s])=>[key,order.indexOf(s)+1])),
 sections:[{title:'项目介绍',pages:'3–10'},{title:'可行性分析',pages:'11–15'},{title:'实施计划与分工',pages:'16–25'},{title:'质量与运行维护',pages:'26–27'},{title:'项目管理与交付',pages:'28–31'},{title:'详细周计划及评审附件',pages:'32–58'}]};
await fs.writeFile(path.join(work,'manifest.json'),JSON.stringify(manifest,null,2));
const candidate=path.join(work,'candidate.pptx');
await (await PresentationFile.exportPptx(p)).save(candidate);
const {finalizePresentation}=await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
const final=path.join(work,'approved',`project-report-${Date.now()}.pptx`);await fs.mkdir(path.dirname(final),{recursive:true});
const receipt=path.join(work,`validation-${Date.now()}.json`);
const result=await finalizePresentation({workspaceDir:root,candidatePath:candidate,finalPath:final,
 pythonExecutable:'C:/Users/wangc/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',
 integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),
 layoutArgs:['--expected-slide-size-emu','12192000,6858000',...[4,11,12,13,14].flatMap(n=>['--require-native-table-slide',String(n)])],explicitTotalSlideCount:58,requiredNativeTableOwnerSlides:[4,11,12,13,14],requiredNativeChartOwnerSlides:[],
 tableArithmeticContracts:[{slide:13,table:1,label_column:0,total_row:4,value_columns:[2],component_rows:[1,2,3]}],
 fontPolicy:{basis:'reference',families:[F],referencePath:input,referenceSha256:hash},verifyArtifactToolImport:true,receiptPath:receipt});
await fs.copyFile(final,official);await fs.copyFile(receipt,path.join(work,'finalizer-latest.json'));
console.log(JSON.stringify({file:official,slides:58,body:31,appendix:27,warnings:result.presentationLayout.warning_count,hash:result.finalSha256}));
