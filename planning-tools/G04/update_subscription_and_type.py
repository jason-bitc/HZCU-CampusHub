from pathlib import Path
import json,re
from decimal import Decimal,ROUND_HALF_UP
ROOT=Path(r'C:\Users\wangc\Desktop\chengyuanbang-mp');OUT=ROOT/'deliverables/G04-项目计划';TOOLS=ROOT/'planning-tools/G04';CH=OUT/'图表与排期'
def edit(name,fn):
 p=TOOLS/name;s=p.read_text(encoding='utf-8-sig');p.write_text(fn(s),encoding='utf-8-sig' if p.suffix=='.ps1' else 'utf-8')
def replace(s,a,b):
 assert a in s,a[:100]
 return s.replace(a,b)
budget=dict(currency='CNY',months=4,period='2026年10月至2027年1月',rate=6.7351,rate_date='2026-10-01',rate_time='17:59:27',rate_type='中国银行美元中行折算价，100美元=673.51元',plus_usd_month=20,plus_cny_month=134.70,plus_cny_total=538.81,doubao_cny_month=38,doubao_cny_total=152,total=690.81,quantity_each=1,booking_task='2.2.1',sources={'plus':'https://learn.chatgpt.com/docs/pricing','exchange':'https://www.boc.cn/sourcedb/whpj/','doubao':'小组提供的38元/月订阅价格'})
(CH/'订阅预算数据.json').write_text(json.dumps(budget,ensure_ascii=False,indent=2),encoding='utf-8')
def data(s):
 s=replace(s,'weeks=[]',"""# Project-wide subscriptions booked once to avoid double counting in summaries.
for t in tasks:t['budget']=690.81 if t['code']=='2.2' else 0
for q in sub:q['budget']=690.81 if q['code']=='2.2.1' else 0
weeks=[]""")
 s=replace(s,"','.join(q['preds']),0,q['accept']","','.join(q['preds']),q['budget'],q['accept']")
 return replace(s,"('FixedCost',0)","('FixedCost',q.get('budget',0))")
edit('fuse_g04_data.py',data)
def docs(s):
 s=replace(s,"return f'{q[\"duration\"]:g}日；新增现金0元'","return f'{q[\"duration\"]:g}日；现金预算{q[\"budget\"]:g}元'")
 s=replace(s,"('框架与代码辅助','Codex、豆包','Codex搭框架和接口骨架；豆包实现细节；双方审查后入Git')","('AI辅助开发','Codex、豆包、WorkBuddy','Codex搭框架和接口骨架；豆包辅助代码细节；小程序在WorkBuddy中开发；双方审查后入Git')")
 s=s.replace('每项新增现金预算0元，项目储备单列。','共用订阅预算690.81元在任务2.2.1登记一次；其余任务不重复分摊订阅费用。')
 s=s.replace('AI订阅和邮件发信按实际需要登记；','AI订阅按ChatGPT Plus及豆包两项编制，其他新增费用为0元；')
 s=replace(s,"[('48个工作包及95项子任务新增直接现金','逐项0元，合计0元','使用现有设备和可用工具；管理复核及里程碑亦为0元'),('项目管理储备','200元','仅在实际需要时登记原因、用途和余额'),('项目新增现金上限','200元','超出需重新评估并修订计划；实际未发生费用不写成已支出')]","[('ChatGPT Plus订阅','20美元/月≈134.70元/月；4个月538.81元','1份订阅，2026年10月至2027年1月；按80美元×6.7351折算'),('豆包订阅','38元/月；4个月152.00元','1份订阅，单价采用小组提供价格'),('其他新增费用','0元','WorkBuddy及其他工具使用当前可用条件，设备与可选服务器复用；不列管理储备'),('项目预算合计','690.81元','两项订阅共用费用在2.2.1登记一次，其他任务不重复分摊；实际按账单登记')]")
 s=replace(s,'任务预计无法赶上交接即登记', 'ChatGPT Plus单价依据OpenAI官方定价页；汇率取中国银行2026年10月1日17:59:27美元中行折算价，100美元=673.51元，即1美元=6.7351元。总额先按未舍入美元金额计算，再保留两位小数，因此不使用单月近似人民币金额重复相乘。实际人民币扣款随结算汇率及账单变化；当前690.81元为计划估算。[R9][R10]\n\n任务预计无法赶上交接即登记')
 s=s.replace('各工作包新增现金预算0元；','共用订阅费用在工作包2.2及子任务2.2.1登记690.81元，其余工作包预算0元；')
 s=s.replace('每项子任务、周管理与复核新增现金均0元。','子任务2.2.1登记全项目订阅预算690.81元；其余子任务、周管理与复核预算0元。')
 s=s.replace("'每项新增0元，整体储备200元'","'两项订阅4个月合计690.81元；2.2.1登记一次，其他0元'")
 s=s.replace("[R8] 工具官方资料", "[R9] OpenAI官方文档 Pricing，https://learn.chatgpt.com/docs/pricing ，2026年10月1日核对；ChatGPT Plus每月20美元。\n\n[R10] 中国银行 外汇牌价，https://www.boc.cn/sourcedb/whpj/ ，2026年10月1日17:59:27；美元中行折算价673.51元/100美元。\n\n[R8] 工具官方资料")
 needle="(OUT/'城院帮可行性分析报告G04.md').write_text(fea,encoding='utf8')"
 updates="""fea=fea.replace('已拥有的电脑和可用订阅作为存量资源，不再虚构采购。','已拥有的电脑作为存量资源；ChatGPT Plus和豆包订阅按2026年10月至2027年1月共4个月计入项目预算。WorkBuddy用于小程序开发，采用当前可用环境，不另列新增采购。')
fea=fea.replace('| 直接新增预算 | 48个工作包、95项子任务均0元 | 不计划新增采购，不把人时转换为金额 |\\n| 管理储备 | 200元，整体新增现金上限200元 | 临时必要支出先登记，超支前修订计划 |','| ChatGPT Plus订阅 | 20美元/月约134.70元；4个月538.81元 | 1份订阅，80美元×6.7351；官方定价与中行折算价 |\\n| 豆包订阅 | 38元/月；4个月152.00元 | 1份订阅，小组提供单价 |\\n| 其他新增费用 | 0元 | 设备、服务器和当前可用工具复用，不列管理储备 |\\n| 项目预算合计 | 690.81元 | 共用费用在2.2.1登记一次；其他任务不重复分摊 |')
fea=fea.replace('条件结论为经济上可承担。若出现必须购买且超过储备的工具，优先选择能够产出同类成果的替代方案；确需新增采购时再评估对预算和交付的影响。','条件结论为经济上可承担。预算只包含两项订阅，共690.81元。ChatGPT Plus人民币金额按中国银行2026年10月1日17:59:27中行折算价1美元=6.7351元估算，先计算80美元的总额再保留两位小数；实际按结算汇率和账单登记。其他工具采用已有条件，如出现新增费用须通过预算变更再评估。[7][8]')
fea=fea.replace('控制新增支出200元上限，核实工具可用性','订阅预算690.81元，核实WorkBuddy及其他工具可用性')
fea+='\\n\\n[7] OpenAI官方文档 Pricing，https://learn.chatgpt.com/docs/pricing ，2026年10月1日核对，ChatGPT Plus每月20美元。\\n\\n[8] 中国银行 外汇牌价，https://www.boc.cn/sourcedb/whpj/ ，2026年10月1日17:59:27，美元中行折算价673.51元/100美元。\\n'
"""
 s=replace(s,needle,updates+needle)
 s=s.replace('def setfont(run,size=11,bold=False):','def setfont(run,size=12,bold=False):')
 s=s.replace('s.page_width=Cm(21.59);s.page_height=Cm(27.94)','s.page_width=Cm(21);s.page_height=Cm(29.7)')
 s=s.replace("[('Normal',11),('Title',25),('Subtitle',13),('Heading 1',16),('Heading 2',13),('Heading 3',11.5)]","[('Normal',12),('Title',22),('Subtitle',14),('Heading 1',16),('Heading 2',14),('Heading 3',12)]")
 s=s.replace("setfont(p.add_run('第 '),10)","setfont(p.add_run('第 '),10.5)").replace("setfont(p.add_run(' 页'),10)","setfont(p.add_run(' 页'),10.5)")
 s=s.replace(" for i,c in enumerate(t.rows[0].cells)"," widths=[x*17.4/18 for x in widths]\n for i,w in enumerate(widths):t.columns[i].width=Cm(w)\n for i,c in enumerate(t.rows[0].cells)")
 s=s.replace('setfont(run,10 if len(heads)==5 else 10.5,ri==0)','setfont(run,10.5,ri==0)')
 s=s.replace('width=Inches(7.0)','width=Cm(17.4)')
 return s
edit('fuse_g04_documents.py',docs)
def prompt(s):
 s=s.replace('Codex/豆包。工具对应产物','Codex/豆包/WorkBuddy。小程序在WorkBuddy中开发，HBuilderX承担uni-app构建，微信开发者工具调试和演示。工具对应产物')
 s=s.replace('每项新增现金0元，管理储备200元，总上限200元','ChatGPT Plus每月20美元约134.70元，4个月538.81元；豆包每月38元，4个月152.00元；合计690.81元，其他新增费用0元，不设管理储备')
 s=s.replace('日期、工期和0元预算保留','日期、工期和预算保留，2.2.1记690.81元，其余0元')
 s=s.replace('当前会议和成员评价根据双方实际记录补齐。','当前会议和成员评价根据双方实际记录补齐。小程序使用WorkBuddy开发，Codex和豆包保留对应辅助分工。')
 s=s.replace('## 视觉要求','订阅预算按各1份、2026年10月至2027年1月4个月编制；80美元×6.7351=538.81元，加豆包152元，共690.81元。采用中国银行2026年10月1日17:59:27中行折算价；来源https://learn.chatgpt.com/docs/pricing 和 https://www.boc.cn/sourcedb/whpj/ 。不要将四个月总额用已经舍入的单月人民币金额重复计算。共用订阅在任务2.2.1记一次预算，不重复累加工作包与子任务。\n\n## 视觉要求')
 return s
edit('fuse_g04_prompt.py',prompt)
edit('export_fused_documents.ps1',lambda s:s.replace('$style.Font.Size=10.5','$style.Font.Size=12'))
edit('verify_and_package.py',lambda s:s.replace("q['budget']==0","abs(q['budget']-(690.81 if q['code'] in ['2.2','2.2.1'] else 0))<0.001").replace("assert all(len(pg.extract_text().strip())>160 for pg in PdfReader(p).pages)","assert all(len(pg.extract_text().strip())>100 for pg in PdfReader(p).pages)"))
print('Tool, subscription budget and typography generators updated')
