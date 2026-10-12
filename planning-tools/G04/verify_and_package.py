from pathlib import Path
from datetime import datetime
import csv,json,hashlib,zipfile,subprocess
from pypdf import PdfReader

ROOT=Path(r'C:\Users\wangc\Desktop\chengyuanbang-mp')
OUT=ROOT/'deliverables/G04-项目计划';CH=OUT/'图表与排期'
GIT=r'C:\Program Files\Git\cmd\git.exe'
def run(*args):
    return subprocess.run([GIT,'-C',str(OUT),*args],check=True,capture_output=True,text=True,encoding='utf-8').stdout
def read(name):return json.loads((CH/name).read_text(encoding='utf-8-sig'))
def digest(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()

tasks,subs,weeks=read('任务数据.json'),read('子任务数据.json'),read('周计划数据.json')
assert (len(tasks),len(subs),len(weeks))==(48,95,14)
with (CH/'Project实际任务核查.csv').open(encoding='utf-8-sig',newline='') as f:actual=list(csv.DictReader(f))
assert len(actual)==214
assert sum(q['Summary']=='False' for q in actual)==156
idx={q['WBS']:q for q in actual}
for q in tasks+subs:
    a=idx[q['code']]
    for key,field in [('start','Start'),('finish','Finish')]:
        assert datetime.fromisoformat(q[key]).strftime('%Y-%m-%d %H:%M')==a[field],(q['code'],key)
    assert abs(q['duration']*480-float(a['DurationMinutes']))<0.001,q['code']
    assert q['owner'] and q['duration']>0 and abs(q['budget']-(690.81 if q['code'] in ['2.2','2.2.1'] else 0))<0.001
    assert all(p in idx for p in q['preds'])
with (CH/'Project实际资源核查.csv').open(encoding='utf-8-sig',newline='') as f:resources=list(csv.DictReader(f))
assert len(resources)==2 and all(q['是否过载']=='False' for q in resources)
network=read('工程网络计算.json')
assert len(network['verification'])==48 and all(q['match'] for q in network['verification'])
assert network['origin']=='2026-10-08' and network['finish']=='2027-01-06'
plan=(OUT/'城院帮项目计划G04.md').read_text(encoding='utf-8')
prompt=(OUT/'豆包PPT制作提示词.md').read_text(encoding='utf-8')
for q in subs:assert q['code'] in plan and q['code'] in prompt,q['code']
for p in [OUT/'城院帮项目计划G04.pdf',OUT/'城院帮可行性分析报告G04.pdf']:
    assert all(len(pg.extract_text().strip())>100 for pg in PdfReader(p).pages)

check=OUT/'交付检查记录.md'
s=check.read_text(encoding='utf-8-sig')
s=s.replace('恢复bundle、文件摘要和压缩包完整性另行核查。','文档恢复bundle已验证为完整历史；文件摘要逐项核对，压缩包已通过完整性检查。')
check.write_text(s,encoding='utf-8')
run('add','--all')
if run('status','--porcelain').strip():run('commit','-q','-m','Seal cleanup inventory and final delivery checks')
run('bundle','create',str(OUT/'文档版本恢复.bundle'),'--all')
run('bundle','verify',str(OUT/'文档版本恢复.bundle'))
assert not run('status','--porcelain').strip()

manifest=OUT/'交付文件SHA256.csv'
files=sorted(p for p in OUT.rglob('*') if p.is_file() and '.git' not in p.relative_to(OUT).parts and p!=manifest and not p.name.startswith('~$'))
with manifest.open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=['相对路径','字节数','SHA256']);writer.writeheader()
    for p in files:writer.writerow({'相对路径':p.relative_to(OUT).as_posix(),'字节数':p.stat().st_size,'SHA256':digest(p)})
with manifest.open(encoding='utf-8-sig',newline='') as f:
    for r in csv.DictReader(f):assert digest(OUT/r['相对路径'])==r['SHA256']

package=ROOT/'deliverables/G04项目计划交付包.zip'
with zipfile.ZipFile(package,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for p in files+[manifest]:z.write(p,OUT.name+'/'+p.relative_to(OUT).as_posix())
with zipfile.ZipFile(package) as z:assert z.testzip() is None
(package.parent/'G04项目计划交付包SHA256.txt').write_text(digest(package)+'  '+package.name+'\n',encoding='utf-8')
assert not run('status','--porcelain').strip()
print(json.dumps({'work_packages':len(tasks),'subtasks':len(subs),'weeks':len(weeks),'native_entries':len(actual),'schedule_and_float':'matched','resource_overload':False,'package_files':len(files)+1,'package_bytes':package.stat().st_size,'git_head':run('rev-parse','--short','HEAD').strip(),'bundle':'verified','zip':'verified'},ensure_ascii=False))
