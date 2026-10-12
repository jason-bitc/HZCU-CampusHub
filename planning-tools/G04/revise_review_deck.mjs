import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';
import {PresentationFile,FileBlob} from 'file:///C:/Users/wangc/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs';

const root='C:/Users/wangc/Desktop/chengyuanbang-mp';
const temp=path.join(root,'.codex-build/revise-20261006');
const source=path.join(root,'deck6/exports/城院帮项目计划（G04）.pptx');
const officialPath=path.join(root,'deliverables/G04-项目计划/城院帮项目计划G04.pptx');
const output=path.join(temp,'approved',`城院帮项目计划G04-${Date.now()}.pptx`);
const skill='C:/Users/wangc/.codex/plugins/cache/openai-primary-runtime/presentations/26.904.11930/skills/presentations';
process.env.RUNTIME_NODE_MODULES='C:/Users/wangc/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules';
process.env.RUNTIME_NODE=process.execPath;
await fs.mkdir(temp,{recursive:true});
const p=await PresentationFile.importPptx(await FileBlob.load(source));
console.log('Imported source deck');
const snapshot=await p.inspect({kind:'slide,shape,textbox,image',maxChars:2500000});
await fs.writeFile(path.join(temp,'before.ndjson'),snapshot.ndjson);
const recs=snapshot.ndjson.split('\n').filter(x=>x.startsWith('{')).map(x=>JSON.parse(x));
const byName=new Map();
for(const r of recs)if(r.name&&r.slide)byName.set(`${r.slide}:${r.name}`,r);
const modifications=[];
function shape(n,id){
 const r=byName.get(`${n}:AutoShape ${id}`);
 if(!r)throw Error(`Missing inspected shape ${n}:${id}`);
 return {r,s:p.resolve(r.id)};
}
function edit(n,id,text,size=18,bbox){
 const {r,s}=shape(n,id);
 s.text=text;
 s.text.style={typeface:'Noto Sans SC',fontSize:size*4/3,autoFit:'none',wrap:'square',verticalAlignment:'top'};
 if(bbox)s.position={left:bbox[0],top:bbox[1],width:bbox[2],height:bbox[3]};
 modifications.push({slide:n,name:r.name,text,size,bbox});
}
function box(n,id,bbox){const {s}=shape(n,id);s.position={left:bbox[0],top:bbox[1],width:bbox[2],height:bbox[3]};}
function hide(n,id){const {s}=shape(n,id);s.text='';s.fill='none';s.line={fill:'none',width:0};}

// Increase main-body type while preserving the imported theme and layouts.
for(const r of recs){
 if(r.kind!=='textbox'||r.slide<3||r.slide>20||!r.text||r.bbox[1]<130||r.bbox[1]>640)continue;
 const s=p.resolve(r.id),b=r.bbox;
 let size=18;
 if(r.slide===7)size=b[3]>=80?14.5:16;
 else if(b[3]<36)size=15.5;
 else if(b[2]<350&&b[3]<80)size=15.5;
 else if(r.text.length>150)size=16;
 s.text.style={typeface:'Noto Sans SC',fontSize:size*4/3,autoFit:'none',wrap:'square'};
 if(b[3]<36)s.position={left:b[0],top:b[1],width:b[2],height:Math.max(b[3],32)};
}
edit(3,7,'需求依据 [11]',11);
edit(3,15,'按岗位及同专业筛选经验，免费阅读\n认证后收藏、点赞、投币和评论',18);
edit(3,19,'分享实习求职亲历\n网页Markdown或小程序图文创作，获得交流与荣誉反馈',18);
edit(3,23,'审核认证材料，处理举报反馈，巡查文章并记录处置理由',18);
edit(3,27,'独立运维网页查看状态日志，执行部署、重启、备份恢复',18);
edit(4,7,'',11); // This slide has no source footer; do not add unsupported attribution.
for(const r of recs.filter(r=>r.kind==='textbox'&&r.slide===4&&r.text.includes('uni-app H5')))p.resolve(r.id).text='uni-app H5\nMarkdown创作\n文章管理';

// Locate exact previously inspected wording for smaller flow elements.
for(const r of recs.filter(r=>r.kind==='textbox'&&r.slide===5)){
 const s=p.resolve(r.id);
 if(r.text==='阅读与收藏')s.text='免费阅读';
 if(r.text==='免费阅读、收藏文章')s.text='浏览文章与资料';
 if(r.text==='认证评论')s.text='认证后互动';
 if(r.text==='校园邮箱认证后交流')s.text='收藏、点赞、投币、评论';
 if(r.text==='专业 / 公司筛选')s.text='专业与公司';
 if(r.text==='校园邮箱认证后交流')s.text='收藏与评论\n点赞与投币';
 if(r.text==='网页完整创作，小程序基础图文')s.text='网页完整创作\n小程序基础图文';
 if(r.text==='发布、修改、下架与草稿管理')s.text='发布、修改与下架\n管理草稿';
 if(r.text==='评论投币交流，获得称号与勋章')s.text='评论与投币交流\n积累称号和勋章';
}
for(const r of recs.filter(r=>r.kind==='textbox'&&r.slide===6&&r.text.startsWith('图 1')))p.resolve(r.id).text='图1 系统架构与本地课堂部署';
edit(8,21,'WorkBuddy / HBuilderX / 微信开发者工具',16.5,[96,410,507,32]);
edit(8,22,'小程序开发、双端构建与调试',15,[96,447,507,29]);
edit(8,25,'IDEA / Java / Spring Boot / MySQL',17,[96,490,507,32]);
edit(8,26,'接口代码与SQL',15,[96,527,507,29]);
edit(8,48,'工具对应工程产物；两人审查AI结果并承担验收责任。',16);
// Three aligned lines per tool row, with no dividers passing through text.
for(const [column,x]of[[0,96],[1,672]]){
 for(let row=0;row<5;row++){
  const label=8+column*20+row*4,base=146+row*80;
  const a=shape(8,label).s,b=shape(8,label+1).s,c=shape(8,label+2).s;
  a.position={left:x,top:base,width:507,height:22};a.text.style={typeface:'Noto Sans SC',fontSize:13.5*4/3,autoFit:'none',wrap:'square'};
  b.position={left:x,top:base+24,width:507,height:31};b.text.style={typeface:'Noto Sans SC',fontSize:(row===3&&column===0?16.5:18)*4/3,autoFit:'none',wrap:'square'};
  c.position={left:x,top:base+57,width:507,height:22};c.text.style={typeface:'Noto Sans SC',fontSize:13.5*4/3,autoFit:'none',wrap:'square'};
 }
}
for(const r of recs.filter(r=>r.slide===8&&r.kind==='shape'&&r.bbox&&r.bbox[1]>130&&r.bbox[1]<555&&r.bbox[2]>200&&r.bbox[3]<3)){
 const s=p.resolve(r.id);s.line={fill:'none',width:0};s.position={left:0,top:0,width:1,height:1};
}
edit(9,10,'管理：范围、进度、风险、变更、评审与提交\n开发：后端认证、文章接口、移动创作与激励\n验证：性能压力、集成与部署恢复',18);
edit(9,14,'开发：双端阅读、网页创作、社区API与网页。\n运维：独立执行器及运维网页。\n统筹：功能兼容、回归、原型和文档归档。',18);
edit(9,17,'计划、可行性、需求、设计评审、联调、验收和总结共同完成。',18);
edit(9,19,'每周检查产物、偏差与风险，记录交接和提交。\n实际成果由双方确认，不预设贡献比例。',16);
edit(10,/* inspection ensures target exists */ 7,'依据 [1] 13.1–13.2',11);
edit(11,37,'内部交付与课堂节点分开，工期按工作日安排。\n原生证据：城院帮项目计划G04.mpp及Project甘特图与资源核查G04.pdf。',16);
edit(12,10,'图3 分析设计网络 设计与复审',15,[76,553,735,36]);
edit(12,13,'3.5需求复审：0.5日，时差0\n4.5设计内评：1日，时差0\n其他设计时差1日\n11/4处理反馈，11/5确认设计\nEET右上、LET右下\n虚线为0日依赖',18);

// Restore complete network images. Existing source diagrams remain unchanged.
for(const n of [6,11,12,13,14,44,45,46]){
 const slide=p.slides.items[n-1];
 for(const im of slide.images.items){im.crop={left:0,top:0,right:0,bottom:0};im.fit='contain';}
}
const im13=p.slides.items[12].images.items[0];
im13.frame={left:102,top:170,width:1076,height:414};im13.crop={left:0,top:0,right:0,bottom:0};im13.fit='contain';
box(13,8,[75,140,1130,453]);
edit(13,10,'图4 模块实现网络',15,[75,139,1130,31]);
for(const id of [11,12,14,15,16,17,18,19])hide(13,id);
edit(13,13,'共享接口先行，模拟数据明确标注；网页、社区及运维的串行顺序包含人员安排。',16,[90,602,1100,42]);
const im14=p.slides.items[13].images.items[0];
im14.frame={left:75,top:153,width:1130,height:291};im14.crop={left:0,top:0,right:0,bottom:0};im14.fit='contain';
box(14,8,[64,144,1152,310]);
edit(14,10,'图5 联调与验证网络',15,[75,459,1130,32]);
for(const [bg,head,body,x]of[[11,12,13,75],[14,15,16,459],[17,18,19,843]]){
 box(14,bg,[x,501,363,99]);box(14,head,[x+16,505,331,30]);box(14,body,[x+16,535,331,57]);
}
edit(14,13,'6.1、6.4、6.6、6.7\n功能、安全与回归',16);
edit(14,16,'6.2、6.3、6.6、6.7\n性能、压力与稳定性',16);
edit(14,19,'6.5总时差0.5日\n冷启动与备份恢复',16);
edit(14,20,'核心实现至内部验收为21个工作日；红色活动时差为0，计算与Project核对。',15.5,[75,604,1130,35]);
edit(15,9,'第7周 实现任务节选 11/16–22',18);
edit(15,33,'第9周 测试任务节选 11/30–12/6',18);
edit(15,24,'3 日',18);
edit(15,29,'本周2日\n总计3日',14.5,[457,435,144,67]);
edit(15,30,'11/22提交设计修订；运维11/23收尾。',14.5,[96,509,512,38]);
edit(15,56,'完整95项子任务及14项周管理见附录；工作包完成标准见项目计划报告附录A。',16);
edit(16,29,'30并发，60秒升载\n持续运行10分钟\n读P95≤1秒，写≤2秒\n非预期错误率<1%',17);
edit(16,33,'10、30、50、100并发逐级探测\n每档2分钟，升载30秒\n100并发仅作容量探测',17);
edit(16,37,'10并发持续30分钟\n降至10并发，2分钟内恢复\n无重复扣发、负余额或数据丢失',17);
edit(16,40,'合成数据：200用户、2000文章、5000评论。验证冷启动、部署重启与备份恢复。\n保存JTL、P95、吞吐、错误及资源趋势；端侧体验另行检查。',16);
edit(17,10,'部署：固定目标发布版本\n重启：项目业务服务\n备份：数据库、图片与版本清单\n恢复：校验备份后恢复',17);
edit(17,17,'网页只执行白名单动作，日志屏蔽密码与令牌。\n执行器独立运行，业务重启后入口及任务记录仍可查。',17);
edit(18,21,'当前排期模型两人均无过载，计入开发、文档、管理与复核；实际负担按周核对。',16,[96,263,1088,56]);
box(18,20,[75,256,1130,65]);
for(const [label,amount,detail,x,val]of[[9,10,11,93.3,'538.81 元'],[13,14,15,477.3,'152.00 元'],[17,18,19,861.3,'690.81 元']]){
 const l=shape(18,label).s;l.position={left:x,top:151,width:328,height:24};l.text.style={typeface:'Noto Sans SC',fontSize:13.5*4/3,autoFit:'none',wrap:'square'};
 edit(18,amount,val,24,[x,177,210,43]);
 const d=shape(18,detail).s;d.position={left:x,top:225,width:328,height:24};d.text.style={typeface:'Noto Sans SC',fontSize:13.5*4/3,autoFit:'none',wrap:'square'};
}
for(const [id,text]of[
 [25,'双方负责。变更前核对范围、工期和预算。'],[29,'双方负责。人工审查，按真实结果验证。'],[33,'王晨负责。核查发信条件，失败时评估替代。'],
 [37,'王晨负责验证。刘景浩实现执行器和恢复保护。'],[41,'刘景浩负责。区分演示内容，邀请亲历作者。'],[45,'王晨负责。记录容量边界及整改结果。']])edit(18,id,text,16.5);
edit(19,9,'结论：在限定范围和本地课堂演示条件下有条件可行。',20);
edit(19,11,'关键验证与推进条件',18);
edit(19,12,'2.1–2.3核查双端、邮箱和运维条件。\n编辑、权限、币一致性和恢复随实现验证。\n性能压力在6.2–6.3按真实版本检查。\n预算690.81元；内容供给继续验证。\n核心失败记录原因、替代及范围影响。',16.5);
edit(19,15,'10/14计划；10/21可行性；10/28需求。\n11/4需求复审与SRS修订。\n11/11总体；11/18详细设计。\n11/25设计修订与翻转PPT1。\n12/2代码用例；12/9测试报告。\n12/16测试修订；12/30总结；1/6收尾。',16.5);
edit(19,17,'课堂前周日22时提交。早期条件核查与完整系统验收分别留证；方法及FV清单见可行性报告§5与附录C。',16);
edit(20,11,'Git保存代码、文档和排期\n图表与版本恢复资料已留存\n变更后核对源稿与导出文件\n密码及认证材料独立保存',17);
edit(20,15,'当前状态：尚未确认\n待填写日期、参与人和共同决定\n行动项有负责人、期限和证据\n确认表见计划报告附录D',17);
edit(20,19,'当前状态：尚未确认\n填写两人实际成果与承担部分\n评价质量、及时性及改进\n双方确认，不预设比例或分数',17);
edit(20,21,'第15、16项尚待双方补齐真实记录。现有分工和待填写表不作为已完成会议或成员评价的证据。',16.5);

// Keep reference identifiers stable, add the actual project evidence and books.
const refs=[
 '[1] 软件工程课程课件 第13章 软件项目管理，13.1–13.6，97页。',
 '[2] 软件工程课程课件 第1章 软件工程学概述，78页。',
 '[3] 课程项目计划阶段评审表，2页、16项；课程PPT评审表，7项。',
 '[4] 课程计划、课程工具及团队作业通知。',
 '[5] Microsoft Project关键路径官方说明。',
 '[6] Apache JMeter官方用户手册与统计报告说明。',
 '[7] Spring Boot Testing；[8] Bugzilla Features。',
 '[9] OpenAI Pricing；[10] 中国银行牌价，预算基准2026/10/1。',
 '[11] 本组校友圈观察、需求讨论、计划及验证资料。',
 '[12] 张海藩、牟永敏 软件工程导论 第6版，清华大学出版社，2013。',
 '[13] Kathy Schwalbe IT项目管理，机械工业出版社，版次待核对。'
];
const slide21=p.slides.items[20];
for(let i=8;i<=28;i++)hide(21,i);
const refText=slide21.shapes.add({geometry:'textbox',name:'Reference list',position:{left:90,top:151,width:1105,height:465},fill:'none',line:{fill:'none',width:0}});
refText.text=refs.join('\n');refText.text.style={typeface:'Noto Sans SC',fontSize:17*4/3,color:'#1D2938',autoFit:'none',wrap:'square',verticalAlignment:'top'};

// Resolve report versus slide references throughout the unchanged task appendix.
for(const r of recs.filter(r=>r.kind==='textbox'&&r.slide>=22)){
 let text=r.text;
 text=text.replaceAll('按附录 A','按项目计划报告附录A').replaceAll('附录 A 核对','项目计划报告附录A核对');
 if(r.slide<=43){
  text=text.replace(/^形成.+?对应产物；按项目计划报告附录A\s*核对内容与未决问题。/,'按项目计划报告附录A核对产物与未决问题。');
  text=text.replace(/^完成.+?；记录交叉复核或双方确认结论。/,'完成本项任务并记录交叉复核或双方确认结论。');
 }
 if(text!==r.text)p.resolve(r.id).text=text;
}
const matrixPages=[[47,1,8],[48,9,16]];
const loc={
 1:'本PPT第1–48页及实际导出',2:'项目计划报告§6；本PPT第19页；实际邮件记录',3:'可行性报告§5、附录C；本PPT第19页',4:'项目计划报告12节及附录A–D',
 5:'项目计划报告§7、附录B；本PPT第9、11页',6:'附件MPP、原生甘特PDF及核查CSV',7:'项目计划报告§7、附录A–B；本PPT第9页',8:'项目计划报告§3、§5；本PPT第7、10页',
 9:'项目计划报告附录A–B；本PPT第15、22–43页',10:'项目计划报告附录A–B；本PPT第22–43页',11:'项目计划报告§6；本PPT第19页与周计划',12:'项目计划报告§10；本PPT第18、23页',
 13:'项目计划报告§11；Git历史及恢复bundle',14:'项目计划报告§10；原生资源核查；本PPT第18页',15:'项目计划报告附录D；本PPT第20页',16:'项目计划报告附录D；本PPT第20页'
};
const metric=jsonParse(await fs.readFile(path.join(root,'.codex-build/review-20261006/slides.json'),'utf8'));
function jsonParse(x){return JSON.parse(x);}
for(const [n,start,end] of matrixPages){
 const blocks=metric[n-1].blocks;const k=blocks.indexOf('条目')+4;
 for(let row=0;row<=end-start;row++){
  const num=start+row,old=blocks[k+row*4+2];
  const rr=recs.find(r=>r.kind==='textbox'&&r.slide===n&&r.text===old);
  if(!rr)throw Error(`Missing rubric cell ${n}/${num}`);
  const s=p.resolve(rr.id);s.text=loc[num];s.text.style={fontSize:10.5*4/3,typeface:'Noto Sans SC',autoFit:'none',wrap:'square'};
  if(num===15||num===16){
   const oldCheck=blocks[k+row*4+3],cr=recs.find(r=>r.kind==='textbox'&&r.slide===n&&r.text===oldCheck);
   p.resolve(cr.id).text='当前尚未确认；待双方填写真实记录并确认，不计为已经完成。';
  }
 }
}

const links='[5] https://support.microsoft.com/en-US/project/show-the-critical-path-of-your-project-in-project\n[6] https://jmeter.apache.org/usermanual/generating-dashboard.html\n[7] https://docs.spring.io/spring-boot/reference/testing/\n[8] https://www.bugzilla.org/about/features.html\n[9] https://learn.chatgpt.com/docs/pricing\n[10] https://www.boc.cn/sourcedb/whpj/\n[12] ISBN 9787302330981；出版社目录 https://www.tup.com.cn/booksCenter/book_05016406.html\n[13] https://www.cmpedu.com/books/book/2052625.htm （目录参考，不推定课程版次）';
for(let i=0;i<48;i++){
 const orig=metric[i].notes.replace(/^1\.7\.2013\s*/,'').replace(/‹#›/g,'').trim();
 let add='';
 if(i===2)add='需求与本组观察依据[11]；软件工程方法依据[1][2]。未认证仅阅读检索，认证后互动。';
 if(i===14)add='5.9工期3日，11月16–18日；5.10本周2日、总计3日，11月23日收尾。';
 if(i===17)add='资源核查：当前模型王晨与刘景浩均未过载，计入开发、文档、管理和复核；真实负担按周检查。';
 if(i===18)add='课堂节点包括11月4日需求复审与11月25日设计修订。2.1–2.3为早期条件核查；完整验收在5.x、6.x。';
 if(i===19)add='用户2026年10月6日直接确认：实际会议和当前成员评价尚未确认。以项目计划报告附录D待填写表为准。';
 if(i===20)add=links+'\n课件不列作者姓名。工具资料为方法依据，本组图表为计划资料。教材仅核对公开目录，不声称完整阅读。';
 const text=[orig,add].filter(Boolean).join('\n\n');
 p.slides.items[i].speakerNotes.textFrame.setText(text);
}
// Replace stale narration on the edited slides instead of retaining contradictory facts.
p.slides.items[14].speakerNotes.textFrame.setText('第7周王晨做移动创作与文章管理，刘景浩完成3日社区管理及本周2日运维执行工作。运维总工期3日，11月23日收尾。第9周功能与性能两路验证，双方共同验收。完整任务见附录及项目计划报告附录A。');
p.slides.items[18].speakerNotes.textFrame.setText('项目有条件可行。早期核查双端、邮箱与独立运维路线，后续完整功能、性能和恢复另行验证。阶段安排包含11月4日SRS修订和11月25日设计修订及翻转PPT1；课堂前周日22时提交。证据与限制见26页可行性报告。');
p.slides.items[19].speakerNotes.textFrame.setText('Git文档历史与恢复资料已经存在。实际会议和当前成员评价尚未由双方确认，不用未来分工替代已完成成果。第15、16项须在双方真实记录完成后同步；待填写表位于项目计划报告附录D。');
p.slides.items[11].speakerNotes.textFrame.setText('分析设计网络包括事件20至34。3.5与4.5为零时差，其他设计活动有1日时差。箭线表示活动、圆表示事件，虚线持续期0。图注采用图名避免错误范围。');
p.slides.items[13].speakerNotes.textFrame.setText('联调与验证网络含4.7、5.12及6.1至6.8。包含事件35、36及后续节点，图注不使用错误的52至76范围。功能安全与性能压力两路汇入回归验收，恢复验证6.5总时差0.5日。内部验收6.7之后还安排6.8报告复核。');

const candidate=path.join(temp,'candidate.pptx');
await (await PresentationFile.exportPptx(p)).save(candidate);
await fs.writeFile(path.join(temp,'changes.json'),JSON.stringify(modifications,null,2));
console.log('Candidate exported');
const {finalizePresentation}=await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
await fs.mkdir(path.dirname(output),{recursive:true});
const receiptPath=path.join(temp,`validation-${Date.now()}.json`);
const result=await finalizePresentation({
 workspaceDir:root,candidatePath:candidate,finalPath:output,
 pythonExecutable:'C:/Users/wangc/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',
 integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),
 layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),
 layoutArgs:['--expected-slide-size-emu','12192000,6858000'],
 explicitTotalSlideCount:48,requiredNativeTableOwnerSlides:[],requiredNativeChartOwnerSlides:[],
 fontPolicy:{basis:'reference',families:['Noto Sans SC'],referencePath:source,referenceSha256:crypto.createHash('sha256').update(await fs.readFile(source)).digest('hex')},
 verifyArtifactToolImport:true,receiptPath
});
await fs.copyFile(output,officialPath);
await fs.copyFile(receiptPath,path.join(temp,'finalizer-latest.json'));
console.log(JSON.stringify({finalPath:officialPath,sha256:result.finalSha256,slides:48,layoutWarnings:result.presentationLayout.warning_count,integrity:result.packageIntegrity.status}));
