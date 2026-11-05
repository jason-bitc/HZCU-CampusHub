"""Check shared document integrity using Python's standard library only."""
from pathlib import Path
import argparse,csv,hashlib,json,re,sys,zipfile,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
DOCS=ROOT/'docs/project'
MANIFEST=DOCS/'MANIFEST.sha256.csv'
SUFFIXES={'.md','.pdf','.docx','.pptx','.mpp','.json','.csv','.xml','.txt','.drawio','.dot','.svg','.png','.jpg','.jpeg'}
def files():return sorted(p for p in DOCS.rglob('*') if p.is_file() and p!=MANIFEST)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def check_er_model(model):
    """Keep relationship ratios separate from minimum-participation constraints."""
    errors=[]
    relations=model.get('relations',[]);constraints=model.get('relationConstraints',[])
    if len(constraints)!=len(relations) or not constraints:
        return ['ER relationships require one explicit participation constraint each']
    for i,(relation,rule) in enumerate(zip(relations,constraints)):
        if len(relation)!=5:errors.append('Invalid ER relationship '+str(i));continue
        a,b,verb,ca,cb=relation
        if ca+':'+cb not in {'1:1','1:N','M:N'}:
            errors.append('Unsupported ER relationship ratio at '+str(i))
        if (rule.get('a'),rule.get('b'),rule.get('verb'),rule.get('ratio'))!=(a,b,verb,ca+':'+cb):
            errors.append('ER relationship/constraint mismatch at '+str(i))
        if any(rule.get(k) not in {0,1} for k in ['minA','minB']) or not rule.get('constraint'):
            errors.append('Missing ER minimum-participation explanation at '+str(i))
        if (rule.get('minA')==0 or rule.get('minB')==0) and '允许没有' not in rule.get('constraint',''):
            errors.append('Optional ER participation needs a text explanation at '+str(i))
    for graph in model.get('graphs',[]):
        if not graph.get('id','').startswith('ER'):continue
        for edge in graph['edges']:
            relation_id=edge['b'] if edge['b'].startswith('rel') else edge['a'] if edge['a'].startswith('rel') else None
            if not relation_id:continue
            try:
                relation=relations[int(relation_id[3:])]
                expected=relation[3] if edge['b']==relation_id else relation[4]
            except (ValueError,IndexError):errors.append('Unknown ER relation in '+graph['id']);continue
            if edge.get('label')!=expected:errors.append('ER endpoint notation differs from ratio in '+graph['id'])
    return errors

def check_srs(document,styles,name):
    """Protect the consolidated review structure without testing application code."""
    problems=[]
    ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
    key='{'+ns['w']+'}'
    levels={}
    for style in styles.findall('w:style',ns):
        label=style.find('w:name',ns)
        if label is None:continue
        match=re.fullmatch(r'(?:heading|标题)([123])',label.get(key+'val','').lower().replace(' ',''))
        if match:levels[style.get(key+'styleId')]=int(match.group(1))
    headings=[]
    for paragraph in document.findall('w:body/w:p',ns):
        style=paragraph.find('w:pPr/w:pStyle',ns)
        level=levels.get(style.get(key+'val')) if style is not None else None
        if level in {1,2}:
            headings.append(''.join(t.text or '' for t in paragraph.findall('.//w:t',ns)))
    required={'3 功能要求','5 用户用例与界面原型','6 数据模型与ER图','7 功能模型与数据流图',
              '8 数据字典','11 行为模型与状态转换图','13 非功能需求与质量验收',
              '14 需求验证与用户确认','附录B 需求阶段11项评审对应与证据状态'}
    for title in sorted(required-set(headings)):problems.append('Missing SRS review chapter '+title+': '+name)
    if len(headings)!=len(set(headings)):problems.append('Duplicate SRS navigation heading: '+name)
    if any(len(title)>85 for title in headings):problems.append('Body text entered SRS heading navigation: '+name)
    text='\n'.join(t.text or '' for t in document.findall('.//w:t',ns))
    if len(set(re.findall(r'FR-[A-Z]\d{2}',text)))!=52:problems.append('SRS must retain 52 unique FR identifiers: '+name)
    if len(set(re.findall(r'NFR\d{2}',text)))!=12:problems.append('SRS must retain 12 unique NFR identifiers: '+name)
    fields=' '.join(t.text or '' for t in document.findall('.//w:instrText',ns))
    fields+=' '+' '.join(t.get(key+'instr','') for t in document.findall('.//w:fldSimple',ns))
    if not re.search(r'\bTOC\b',fields):problems.append('SRS requires an updateable Word contents field: '+name)
    links=document.findall('.//w:hyperlink',ns)
    if sum(bool(t.get(key+'anchor')) for t in links)<10:problems.append('SRS requires cached internal contents links: '+name)
    return problems
def refresh():
    with MANIFEST.open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f);w.writerow(['path','bytes','sha256'])
        w.writerows([p.relative_to(DOCS).as_posix(),p.stat().st_size,sha(p)] for p in files())
def check():
    errors=[];payload=files()
    if not payload:errors.append('No shared documents found')
    if not MANIFEST.exists():return ['Missing docs/project/MANIFEST.sha256.csv; refresh it first']
    records=list(csv.DictReader(MANIFEST.open(encoding='utf-8-sig',newline='')))
    expected={}
    for r in records:
        name=r.get('path','')
        if not name or '\\' in name or Path(name).is_absolute() or '..' in Path(name).parts:
            errors.append('Invalid manifest path: '+name);continue
        if name in expected:errors.append('Duplicate manifest path: '+name)
        expected[name]=r
    actual={p.relative_to(DOCS).as_posix():p for p in payload}
    for name in sorted(expected.keys()-actual.keys()):errors.append('Missing file: '+name)
    for name in sorted(actual.keys()-expected.keys()):errors.append('Unregistered file: '+name)
    for name,p in actual.items():
        if p.suffix.lower() not in SUFFIXES or p.name.startswith(('~$','.env')) or '.git' in p.parts:
            errors.append('Disallowed document: '+name)
        if p.stat().st_size>25*1024*1024:errors.append('Document exceeds project 25 MiB limit: '+name)
        r=expected.get(name)
        if r and (str(p.stat().st_size)!=r.get('bytes') or sha(p)!=r.get('sha256')):errors.append('Hash or size mismatch: '+name)
        if p.suffix.lower()=='.pdf' and p.read_bytes()[:5]!=b'%PDF-':errors.append('Invalid PDF header: '+name)
        if p.suffix.lower() in {'.docx','.pptx'}:
            try:
                with zipfile.ZipFile(p) as z:
                    if z.testzip():errors.append('Corrupt Office ZIP: '+name)
                    ET.fromstring(z.read('[Content_Types].xml'))
                    if p.name=='城院帮软件需求规格说明书G04.docx':
                        errors.extend(check_srs(ET.fromstring(z.read('word/document.xml')),ET.fromstring(z.read('word/styles.xml')),name))
                    if p.suffix.lower()=='.docx' and '周会议纪要' in p.stem:
                        document=ET.fromstring(z.read('word/document.xml'))
                        ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
                        cover=document.findall('w:body/w:p',ns)[:8]
                        if not any(q.find('.//w:drawing',ns) is not None for q in cover):
                            errors.append('Missing meeting cover logo: '+name)
                        styles=ET.fromstring(z.read('word/styles.xml'))
                        key='{'+ns['w']+'}'
                        heading_ids=set()
                        for style in styles.findall('w:style',ns):
                            label=style.find('w:name',ns)
                            if label is not None and label.get(key+'val','').lower().replace(' ','') in {'heading1','标题1'}:
                                heading_ids.add(style.get(key+'styleId'))
                        headings=[q for q in document.findall('w:body/w:p/w:pPr/w:pStyle',ns) if q.get(key+'val') in heading_ids]
                        if len(headings)!=5:errors.append('Meeting template requires five main sections: '+name)
            except Exception:errors.append('Invalid Office package: '+name)
            if not p.with_suffix('.pdf').exists():errors.append('Missing PDF companion: '+name)
            if p.suffix.lower()=='.docx' and not p.with_suffix('.md').exists():errors.append('Missing Markdown companion: '+name)
    model_path=DOCS/'models/图表与模型/模型数据源G04.json'
    if model_path.exists():
        try:errors.extend(check_er_model(json.loads(model_path.read_text(encoding='utf-8-sig'))))
        except (ValueError,TypeError,KeyError):errors.append('Invalid ER relationship model')
    return errors
if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('--refresh',action='store_true');args=a.parse_args()
    if args.refresh:refresh()
    problems=check()
    if problems:
        print('\n'.join('ERROR '+e for e in problems));sys.exit(1)
    print('PASS: shared documents, hashes, size limits and Office/PDF companions ('+str(len(files()))+' files)')
