"""Check shared document integrity using Python's standard library only."""
from pathlib import Path
import argparse,csv,hashlib,sys,zipfile,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
DOCS=ROOT/'docs/project'
MANIFEST=DOCS/'MANIFEST.sha256.csv'
SUFFIXES={'.md','.pdf','.docx','.pptx','.mpp','.json','.csv','.xml','.txt','.drawio','.dot','.svg','.png','.jpg','.jpeg'}
def files():return sorted(p for p in DOCS.rglob('*') if p.is_file() and p!=MANIFEST)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
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
    return errors
if __name__=='__main__':
    a=argparse.ArgumentParser();a.add_argument('--refresh',action='store_true');args=a.parse_args()
    if args.refresh:refresh()
    problems=check()
    if problems:
        print('\n'.join('ERROR '+e for e in problems));sys.exit(1)
    print('PASS: shared documents, hashes, size limits and Office/PDF companions ('+str(len(files()))+' files)')
