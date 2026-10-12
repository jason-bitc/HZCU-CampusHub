from pathlib import Path
from datetime import datetime,date,timedelta
import json,csv,xml.etree.ElementTree as E

ROOT=Path(r'C:\Users\wangc\Desktop\chengyuanbang-mp')
OUT=ROOT/'deliverables/G04-项目计划';CH=OUT/'图表与排期'
CH.mkdir(parents=True,exist_ok=True)
tasks=json.loads((ROOT/'archive/项目计划历史/20261001/deliverables/G04-project-plan-20261001/图表与排期/任务数据.json').read_text(encoding='utf8'))
STAGES=['项目计划与评审','可行性研究','需求分析','总体与详细设计','编码与单元测试','综合测试','部署与维护','总结与收尾']
HOL={date(2026,10,d) for d in range(1,8)}|{date(2027,1,1)}
BASE=datetime(2026,10,8,9)
def wd(d):return d.weekday()<5 and d.date() not in HOL
def advance(s,n):
 h=n*8;c=s
 while h>1e-8:
  if not wd(c) or c.hour>=18:c=datetime.combine(c.date()+timedelta(days=1),datetime.min.time()).replace(hour=9);continue
  if c.hour<9:c=c.replace(hour=9)
  if 13<=c.hour<14:c=c.replace(hour=14)
  bound=c.replace(hour=13 if c.hour<13 else 18,minute=0)
  x=min(h,(bound-c).total_seconds()/3600);c+=timedelta(hours=x);h-=x
 return c
def next_start(s):
 if s.hour==13:return s.replace(hour=14)
 if s.hour>=18:
  s=s+timedelta(days=1);s=s.replace(hour=9,minute=0)
 while not wd(s):s+=timedelta(days=1)
 return s
def offset(s):
 d=BASE;v=0
 while d.date()<s.date():
  if wd(d):v+=1
  d+=timedelta(days=1)
 if wd(s):v+=(min(4,max(0,(s.hour+s.minute/60)-9))+min(4,max(0,(s.hour+s.minute/60)-14)))/8
 return v

# Each original work package receives an executable first-version style split.
SPEC={
'1.1':('确认WBS、依赖与提交边界','建立资源预算与风险安排','每项任务写明负责人、工期、前置、产物与现金预算；管理活动单列','WBS与Project任务表；预算及风险清单'),
'1.2':('逐条检查评审要求与计划','确认汇报材料与提交清单','16项要求有材料位置；图表、分工和提交节点一致','计划评审记录；PPT提示词与附件清单'),
'1.3':('登记课堂计划意见与影响','共同修订排期与计划基线','逐项记录处理人、修改内容和复核结果；未关闭问题有期限','问题单、修订稿和计划基线标签'),
'2.1':('验证双端构建与邮箱条件','核查独立运维、存储与恢复路径','真实发信、编译、权限和恢复条件有验证记录；失败说明替代措施','环境配置清单；关键技术验证记录'),
'2.2':('核查工具、设备与现金费用','分析资料来源、作者邀请与操作成本','现有资源与新增费用分别列出；演示资料可追溯；不虚构访谈和作者承诺','经济操作分析；内容来源与邀请方案'),
'2.3':('双方汇总技术与资源判断','共同评审可行性结论与条件','技术、经济、操作、进度及风险都有依据；高风险项明确验证和应对','可行性报告；条件结论及问题清单'),
'3.1':('共同核对读者作者与管理场景','确认权限矩阵与核心用例','游客、未认证、认证、社区和运维角色分开；普通账号不能访问管理数据','用例清单；角色权限矩阵'),
'3.2':('细化文章交流、激励与治理规则','定义数据、异常与非功能验收','REQ编号关联用例；认证资料、隐私、状态、冲突、币账本与恢复边界可验收','SRS需求条目；数据字典与验收条件'),
'3.3':('绘制双端阅读创作原型','核对社区和运维管理流程','原型覆盖空结果、加载失败、认证提示、草稿保存和运维任务失败','可点击原型或线框；场景核对记录'),
'3.4':('共同汇总SRS与需求追踪','内部复核并准备需求汇报','需求、原型、权限和异常一致；未决问题记录；10月25日提交材料齐全','内部需求基线；追踪表与汇报素材'),
'3.5':('记录需求复审意见','修订需求并同步设计影响','11月4日新意见在课后完成登记与修订；变更有责任人和复核结论','SRS修订；需求变更与影响记录'),
'4.1':('设计业务模块与接口契约','设计数据关系与本机部署边界','共享API、错误响应、版本号、ER和权限边界明确；客户端不直连数据库','架构、ER、部署图；API契约'),
'4.2':('细化认证文章与治理状态','细化币事务、隐私及异常处理','验证码失效、越权、版本冲突、重复请求、下架及申诉均有处理规则','状态图；事务和失败处理设计'),
'4.3':('设计网页Markdown与移动图文','设计图片、草稿和跨端编辑流程','复杂正文不能被移动端静默覆盖；上传失败保留正文；编辑冲突可识别','页面状态图；编辑与上传详细设计'),
'4.4':('定义运维接口、鉴权与白名单','设计备份一致性、恢复与失败保护','执行器独立于业务进程；固定目标；任务互斥；恢复前保护现有数据','运维执行协议；备份恢复流程'),
'4.5':('共同核对总体与详细设计','共同评审并确认内部设计基线','关键设计问题关闭；SRS、接口、数据、流程和测试条件一致','总体及详细设计；评审记录与基线'),
'4.6':('编制核心与异常验收用例','准备合成数据和性能脚本方案','覆盖五类角色、双端、账本、恢复、性能压力与稳定性；数据不含真实隐私','TC用例、REQ追踪；合成数据及脚本方案'),
'4.7':('共同登记课堂设计意见','分别调整模块并共同复核','11月25日意见同步至需求、设计、接口和实现；大改经影响评估排期','设计变更；模块修订与复核记录'),
'5.1':('搭建模块骨架与环境配置','核对共享接口和错误格式','双方按契约独立工作；配置与凭据分离；构建入口可复现','工程骨架、环境模板和接口示例'),
'5.2':('实现登录与校园邮箱验证','实现材料认证接口和角色限制','验证码错误、过期和发送失败有反馈；认证材料受控；审核有理由与通知','认证代码、单元结果与接口集合'),
'5.3':('实现文章图片、草稿存储接口','实现岗位专业公司筛选与分页','草稿不公开；组合检索准确；图片关联正确；错误响应与版本字段符合契约','内容API、SQL、单元与接口结果'),
'5.4':('实现双端筛选与阅读界面','核对游客状态、失败和空结果','此时按契约使用模拟数据并明确标注；真实API接入在5.12完成','页面与交互记录；模拟数据说明'),
'5.5':('实现Markdown、图片和资料链接','实现预览、保存与草稿恢复','正文图片保存一致；失败保留内容；危险脚本过滤；复杂正文格式保留','编辑器模块；保存失败及预览检查'),
'5.6':('实现移动图文编辑和上传','实现移动草稿保存及恢复','手机能完成图文创作；失败图片可逐项重试；不静默覆盖复杂正文','移动创作代码；图片和草稿用例'),
'5.7':('实现发布、修改、下架和恢复接口','接通双端作者文章管理','更新有版本冲突提示；管理员下架不可由作者自行恢复；删除不误删他人数据','生命周期API；双端文章管理与单元结果'),
'5.8':('实现主页、评论图片及收藏交流','实现点赞、领取投币、荣誉排序与通知','未认证不能取得评论主页联系方式；作者关闭联系立即生效；余额不负、不自投、不重复奖励；贡献与消费分开','交流激励模块；事务并发、权限与通知用例'),
'5.9':('实现认证审核与举报反馈处理接口','实现社区管理网页并联调','刘景浩同时负责社区处理API与网页；复用认证契约；一键定位原文评论；理由、通知、限制申诉及审计可追溯','社区管理API与网页；处理状态和权限记录'),
'5.10':('实现独立运维任务执行服务','实现部署重启、备份恢复及失败保护','仅受控服务与目录；任务互斥且持久；恢复前备份；数据库与图片一致；业务停机仍可查任务','执行器、备份清单与失败保护用例'),
'5.11':('实现运维状态、日志和备份列表','联调网页部署重启与备份恢复','各动作显示目标与影响并确认；显示运行、成功、失败状态和审计；不提供任意命令输入','运维网页；四类操作与失败反馈记录'),
'5.12':('双方接通真实接口与跨端链路','构建候选版本并归档实现证据','关闭模拟数据替代API的缺口；五类角色、业务与运维真实联通；接口、代码和用例一致','安装包、代码清单、联调记录与实现基线'),
'6.1':('执行角色及核心业务功能用例','执行接口、集成和失败场景','阅读认证创作交流管理全链路通过；失败输入、数据断言与缺陷可复现','TC实际结果、Postman集合及缺陷单'),
'6.2':('固定环境执行30并发负载','分析P95、错误率与资源占用','200用户2000文章5000评论；30并发运行10分钟；读P95≤1秒、写≤2秒、非预期错误率<1%为计划目标','JMeter脚本、JTL、统计及环境记录'),
'6.3':('执行阶梯压力寻找容量边界','执行稳定性与降载恢复','10/30/50/100并发各2分钟用于探测；100不代表承诺容量；降载10并发2分钟内恢复；10并发运行30分钟','压力稳定性结果、资源趋势和账本一致性记录'),
'6.4':('执行越权隐私与危险输入检查','执行双端浏览编辑与图片兼容','跨角色直接API被拒；敏感材料不泄漏；网页与开发者工具核心流程一致；设备条件及限制写清','安全、权限、兼容及页面体验记录'),
'6.5':('验证冷启动与网页运维操作','验证损坏备份、回退与恢复一致性','状态操作真实执行；数据库与图片一致；损坏备份被拒；失败不静默损坏当前数据','启动、部署重启、恢复与回退记录'),
'6.6':('按模块分派缺陷并修复','交叉复测原场景与相邻流程','刘景浩统筹；王晨修复后端移动激励；刘景浩修复网页社区运维；阻塞及严重缺陷关闭','双方修复提交、复测结果和残留问题清单'),
'6.7':('双方确认测试结论与交付限制','','实际数据与计划目标分开；结论可追溯到版本、环境与证据；12月6日提交包齐全','系统测试报告；候选交付包和验收清单'),
'6.8':('复核测试报告与证据链','整理课堂测试汇报和已有修订','12月6日提交基线保留；后续复核作为修订记录；不把新增意见倒写到已提交版本','测试报告修订与汇报材料'),
'7.1':('整理安装、用户及管理操作说明','整理运维、恢复与故障维护规程','指南包括版本、目录、冷启动、四类操作影响、离机备份和已知限制；对应同一安装包','发布包、安装用户管理运维说明'),
'7.2':('在干净配置核对冷启动','核对演示数据、认证与外网失败回退','按指南可复现；预认证演示与真实邮箱验证分开；测试账号不展示凭据','干净环境启动及课堂演示核查记录'),
'7.3':('登记维护申请和影响分析','执行修改回退及回归演练','申请、评估、实施、回归与版本归档闭环；不为演练制造真实用户事故','维护变更单、回退与回归记录'),
'7.4':('核对备份、配置与维护资料','双方练习操作并确认交接','双方能操作固定运维任务；离机副本有位置和完整性记录；私密配置独立交接','交接清单、备份与权限配置记录'),
'7.5':('登记12月16日测试评审意见','修订报告及相关实现用例','逐项处理、复核和记录；未解决问题明确责任及期限；同版本更新交付包','测试评审修订和残留风险记录'),
'8.1':('共同核对计划实际与阶段成果','共同总结偏差风险和协作贡献','以工作包、提交和问题单说明事实；管理、开发、文档均计入成果；不虚构数字','总结报告、偏差分析和实际贡献证据'),
'8.2':('整理总评PPT资料和详细周计划','核对引用、图表与交付目录','PPT由豆包制作；正文与附录范围分工日期一致；无旧范围截图与凭据','总评素材、完整周计划和引用'),
'8.3':('共同执行交付清单核查','共同预演并确认提交版本','源码、脚本、包、文档、测试与图表同版本；12月27日提交、12月30日汇报','交付基线、验收清单及演练记录'),
'8.4':('处理总评意见并准备提交基线','核对提交后尾项与最终归档','12月31日准备1月3日提交基线；1月4日核对尾项并形成补充记录；不宣称1月3日已完成1月4日任务','总评修订、提交基线及归档尾项记录'),
'8.5':('共同核对课程收尾材料','共同确认交付与后续维护边界','1月6日课程收尾；实际互评由双方确认；后续运维范围另行约定','收尾确认、最终清单与实际成员评价'),
}
sub=[]
for t in tasks:
 a,b,accept,proof=SPEC[t['code']];t['accept']=accept;t['evidence']=proof
 t['units']=.4 if '、' in t['owner'] or t['code']=='2.1' else .8
 if t['code']=='3.5':t['start']='2026-11-04T14:00:00';t['duration']=.5;t['finish']='2026-11-04T18:00:00'
 if t['code']=='5.12':t['owner']='王晨、刘景浩';t['reviewer']='共同确认';t['units']=.4
 if t['code']=='8.4':t['accept']=accept
 st=datetime.fromisoformat(t['start']);dur=t['duration']
 parts=[dur] if not b else ([.25,dur-.25] if dur<1 else [.5,dur-.5] if dur<2 else [1,dur-1])
 for i,d in enumerate(parts,1):
  q=dict(code=t['code']+f'.{i}',parent=t['code'],name=a if i==1 else b,duration=d,start=st.isoformat(),finish=advance(st,d).isoformat(),owner=t['owner'],coordinator=t['coordinator'],reviewer=t['reviewer'],preds=[],budget=0,units=t['units'],accept=accept,evidence=proof)
  sub.append(q);st=next_start(datetime.fromisoformat(q['finish']))
for t in tasks:
 children=[q for q in sub if q['parent']==t['code']]
 for i,q in enumerate(children):q['preds']=[children[i-1]['code']] if i else [next(x['code'] for x in reversed(sub) if x['parent']==p) for p in t['preds']]

# Project-wide subscriptions booked once to avoid double counting in summaries.
for t in tasks:t['budget']=690.81 if t['code']=='2.2' else 0
for q in sub:q['budget']=690.81 if q['code']=='2.2.1' else 0
weeks=[]
start=date(2026,10,5)
goals=['计划评审准备与关键条件核查','计划修订与可行性结论','需求、原型与验收条件','总体架构和关键业务设计','需求反馈与内部设计基线','认证、文章接口及双端创作界面','移动创作、文章管理和社区治理','交流激励、运维及真实接口联调','完整系统测试与首次内部验收','测试证据复核和发布维护说明','维护交接与课堂测试意见处理','总结材料、交付核查与总评演练','总评反馈与1月3日归档基线','归档尾项核对与课程收尾']
for n in range(14):
 a=start+timedelta(days=n*7);b=min(a+timedelta(days=6),date(2027,1,6))
 active=[q for q in sub if datetime.fromisoformat(q['start']).date()<=b and datetime.fromisoformat(q['finish']).date()>=a]
 work=[a+timedelta(days=i) for i in range((b-a).days+1) if (a+timedelta(days=i)).weekday()<5 and a+timedelta(days=i) not in HOL]
 weeks.append(dict(number=n+1,start=a.isoformat(),finish=b.isoformat(),goal=goals[n],subtasks=[q['code'] for q in active],management=dict(code=f'M{n+1:02}',name='周进度风险与评审提交管理',owner='王晨',support='刘景浩提供模块状态、风险和完成证据',start=datetime.combine(work[0],datetime.min.time()).replace(hour=9).isoformat(),finish=datetime.combine(work[-1],datetime.min.time()).replace(hour=18).isoformat(),duration=len(work),units=.1,budget=0,accept='检查完成证据、阻塞、下周交接和提交节点；更新风险变更及实际差异；双方确认记录')))
milestones=[('K01','项目计划课堂评审','2026-10-14'),('K02','可行性报告课堂节点','2026-10-21'),('K03','SRS需求课堂评审','2026-10-28'),('K04','SRS修订与需求复审','2026-11-04'),('K05','总体设计课堂节点','2026-11-11'),('K06','详细设计课堂节点','2026-11-18'),('K07','设计评审与翻转PPT1','2026-11-25'),('K08','代码用例与翻转PPT2','2026-12-02'),('K09','系统测试报告课堂节点','2026-12-09'),('K10','测试报告修订课堂评审','2026-12-16'),('K11','项目总结与总评PPT','2026-12-30'),('K12','课程收尾与交付确认','2027-01-06')]
for name,data in [('任务数据',tasks),('子任务数据',sub),('周计划数据',weeks)]:
 (CH/(name+'.json')).write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf8')
with (CH/'详细任务与验收标准.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['任务编号','工作包','具体任务','任务负责人','复核','开始','完成','工期工作日','前置任务','新增现金预算元','验收标准','应保存的证据'])
 for q in sub:w.writerow([q['code'],q['parent'],q['name'],q['owner'],q['reviewer'],q['start'],q['finish'],q['duration'],','.join(q['preds']),q['budget'],q['accept'],q['evidence']])

# Native Project uses explicit review and ongoing management capacity alongside delivery tasks.
NS='http://schemas.microsoft.com/project';E.register_namespace('',NS)
def node(p,n,v):x=E.SubElement(p,'{'+NS+'}'+n);x.text=str(v);return x
pr=E.Element('{'+NS+'}Project')
for k,v in [('SaveVersion',14),('Name','城院帮项目计划 G04'),('ScheduleFromStart',1),('StartDate',BASE.isoformat()),('CalendarUID',1),('MinutesPerDay',480),('MinutesPerWeek',2400),('DaysPerMonth',20),('DefaultStartTime','09:00:00'),('DefaultFinishTime','18:00:00'),('CurrencyCode','CNY'),('CurrencySymbol','¥'),('CurrencyDigits',2),('DefaultTaskType',1),('DurationFormat',7),('NewTasksAreManual',0),('CriticalSlackLimit',0)]:node(pr,k,v)
cals=node(pr,'Calendars','');c=node(cals,'Calendar','')
for k,v in [('UID',1),('Name','G04课程排期日历'),('IsBaseCalendar',1),('BaseCalendarUID',-1)]:node(c,k,v)
days=node(c,'WeekDays','')
for d in range(1,8):
 p=node(days,'WeekDay','');node(p,'DayType',d);node(p,'DayWorking',int(d not in [1,7]))
 if d not in [1,7]:
  ws=node(p,'WorkingTimes','')
  for a,b in [('09:00:00','13:00:00'),('14:00:00','18:00:00')]:z=node(ws,'WorkingTime','');node(z,'FromTime',a);node(z,'ToTime',b)
for rid,who in enumerate(['王晨','刘景浩'],1):
 cc=node(cals,'Calendar','')
 for k,v in [('UID',rid+2),('Name',who),('IsBaseCalendar',0),('BaseCalendarUID',1)]:node(cc,k,v)
rr=node(pr,'Resources','')
for rid,who in enumerate(['王晨','刘景浩'],1):
 r=node(rr,'Resource','')
 for k,v in [('UID',rid),('ID',rid),('Name',who),('Type',1),('MaxUnits',1),('StandardRate',0),('OvertimeRate',0),('CostPerUse',0),('CalendarUID',rid+2)]:node(r,k,v)
entries=[]
for s,stage in enumerate(STAGES,1):
 entries.append(dict(code=str(s),name=stage,summary=True,level=1))
 for t in tasks:
  if t['code'].startswith(str(s)+'.'):
   entries.append(dict(t,summary=True,level=2))
   entries.extend(dict(q,summary=False,level=3) for q in sub if q['parent']==t['code'])
entries.append(dict(code='M',name='持续项目管理与交叉复核',summary=True,level=1))
for w in weeks:entries.append(dict(w['management'],summary=False,level=2,preds=[],coordinator='王晨',reviewer='刘景浩',evidence='周会议、风险变更和提交核查记录'))
for t in tasks:
 if t['reviewer']!='共同确认':entries.append(dict(code='R'+t['code'],name='复核与交接 '+t['name'],summary=False,level=2,start=t['start'],finish=t['finish'],duration=t['duration'],owner=t['reviewer'],units=.1,preds=[],budget=0,accept=t['accept'],evidence='交叉复核问题与关闭记录'))
entries.append(dict(code='K',name='课程里程碑',summary=True,level=1))
for code,name,d in milestones:entries.append(dict(code=code,name=name,summary=False,level=2,start=d+'T09:00:00',finish=d+'T09:00:00',duration=0,owner='',preds=[],units=0,budget=0,accept='按课程进度提交并登记当堂意见',evidence='提交及课堂反馈记录'))
uids={q['code']:i for i,q in enumerate(entries,1)}
deadline={'1.3.2':'2026-10-15T18:00:00','3.4.2':'2026-10-23T18:00:00','4.5.2':'2026-11-05T18:00:00','5.12.2':'2026-11-27T18:00:00','6.7.1':'2026-12-04T18:00:00','6.8.2':'2026-12-08T18:00:00','7.1.2':'2026-12-10T18:00:00','7.2.2':'2026-12-11T18:00:00','7.4.2':'2026-12-15T18:00:00','8.3.2':'2026-12-24T18:00:00','8.4.2':'2027-01-04T18:00:00','8.5.2':'2027-01-06T18:00:00'}
tt=node(pr,'Tasks','')
for q in entries:
 x=node(tt,'Task','')
 for k,v in [('UID',uids[q['code']]),('ID',uids[q['code']]),('Name',q['code']+' '+q['name']),('WBS',q['code']),('OutlineNumber',q['code']),('OutlineLevel',q['level']),('Summary',int(q['summary'])),('Manual',0)]:node(x,k,v)
 if 'owner' in q:node(x,'Notes',f'任务负责人：{q["owner"]}；通过标准：{q["accept"]}；证据：{q.get("evidence","")}。资源占用只用于排期检查，不代表人时或贡献。')
 if not q['summary']:
  for k,v in [('Start',q['start']),('Finish',q['finish']),('Duration',f'PT{q["duration"]*8:g}H0M0S'),('DurationFormat',7),('Estimated',0),('Type',1),('FixedCost',q.get('budget',0)),('ConstraintType',4),('ConstraintDate',q['start']),('Milestone',int(q['duration']==0))]:node(x,k,v)
  if q['code'] in deadline:node(x,'Deadline',deadline[q['code']])
  for p in q.get('preds',[]):
   z=node(x,'PredecessorLink','');node(z,'PredecessorUID',uids[p]);node(z,'Type',1);node(z,'LinkLag',0);node(z,'LagFormat',7)
aa=node(pr,'Assignments','');aid=0
for q in entries:
 if q['summary'] or not q.get('owner'):continue
 for who in q['owner'].split('、'):
  aid+=1;a=node(aa,'Assignment','')
  for k,v in [('UID',aid),('TaskUID',uids[q['code']]),('ResourceUID',1 if who=='王晨' else 2),('Units',q['units']),('Start',q['start']),('Finish',q['finish'])]:node(a,k,v)
E.ElementTree(pr).write(CH/'MicrosoftProject任务源.xml',encoding='utf-8',xml_declaration=True)
(CH/'Project条目.json').write_text(json.dumps(entries,ensure_ascii=False,indent=2),encoding='utf8')
print(json.dumps({'packages':len(tasks),'subtasks':len(sub),'weekly_management':len(weeks),'project_entries':len(entries),'output':str(OUT)},ensure_ascii=False))
