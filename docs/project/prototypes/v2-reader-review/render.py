"""Render G04 review artboards and a native-prototype hotspot handoff."""
import csv
import json
import re
from pathlib import Path

from PIL import Image
from canvas import Canvas
from unicodedata import east_asian_width

ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'boards'
OUT.mkdir(exist_ok=True)
DATA = json.loads((ROOT/'content/articles.json').read_text(encoding='utf-8-sig'))
ARTICLES = {a['id']: a for a in DATA}
BODY = (ROOT/'content/A01.md').read_text(encoding='utf-8-sig')
SECTIONS = re.split(r'^## ', BODY, flags=re.M)
CHAPTERS = [part.splitlines()[0] for part in SECTIONS[1:]]
PAPER, BG, INK, GRAY, LINE, BLUE, PALE = '#FFFFFF','#F4F6F9','#162238','#526179','#DEE5EF','#0052D9','#E8F1FF'
RED, RED_BG, GREEN = '#9E302F', '#FFF1F0', '#176344'
BOOK = ['A01','A10','A02','A03','A04','A05']
HOTSPOTS = []

class Board:
    def __init__(self, name, width=375, height=812, low=False):
        self.name, self.w, self.h, self.low = name, width, height, low
        self.d=Canvas(width,height,self.color(BG))

    def color(self, color):
        if self.low:
            return {BG:'#F0F0F0',INK:'#242424',GRAY:'#5A5A5A',LINE:'#C2C2C2',
                    BLUE:'#3A3A3A',PALE:'#E4E4E4',RED:'#454545',RED_BG:'#E4E4E4',GREEN:'#484848'}.get(color,color)
        return color

    @staticmethod
    def measure(value,size=14,bold=False):
        def em(c):
            if c.isspace(): return .29
            if c in "il1.,!:'|": return .29
            if c in 'MW@': return .81
            if c.isascii(): return .59 if c.isupper() else .54
            return 1 if east_asian_width(c) in 'WFA' else .65
        return sum(em(c) for c in value)*size*(1.015 if bold else 1)

    def box(self,x,y,w,h,fill=PAPER,stroke=None,r=0):
        assert w>0 and h>0 and x>=0 and y>=0 and x+w<=self.w+.1 and y+h<=self.h+.1, (self.name,x,y,w,h)
        self.d.rect(x,y,w,h,self.color(fill),self.color(stroke) if stroke else None,r)

    def line(self,x,y,xx,yy,color=LINE,width=1):
        self.d.line(x,y,xx,yy,self.color(color),width)

    def text(self,x,y,s,size=14,fill=INK,bold=False,anchor='lt'):
        self.d.text(x,y,s,size,self.color(fill),bold,anchor)

    def wrap(self,txt,width,size,bold=False,limit=None):
        lines=[]
        rest=txt.strip()
        while rest:
            last=0
            for idx in range(1,len(rest)+1):
                if self.measure(rest[:idx],size,bold)>width: break
                last=idx
            if not last: last=1
            if last<len(rest) and rest[last-1].isascii() and rest[last-1].isalnum() and rest[last].isascii() and rest[last].isalnum():
                space=rest[:last].rfind(' ')
                if space>last//2:
                    last=space
            line=rest[:last].strip()
            rest=rest[last:].strip()
            if limit and len(lines)==limit-1 and rest:
                while self.measure(line+'…',size,bold)>width: line=line[:-1]
                lines.append(line+'…')
                break
            lines.append(line)
        if len(lines)==2 and len(lines[1])<4 and len(lines[0])>8:
            cut=max(1,len(lines[0])-7)
            lines=[lines[0][:cut].rstrip(),lines[0][cut:].lstrip()+lines[1]]
        return lines

    def paragraph(self,x,y,txt,width,size=14,lead=22,fill=GRAY,bold=False,limit=None):
        for line in self.wrap(txt,width,size,bold,limit):
            self.text(x,y,line,size,fill,bold)
            y+=lead
        return y

    def hit(self,label,x,y,w,h,to):
        if to:
            assert x>=0 and y>=0 and x+w<=self.w and y+h<=self.h
            HOTSPOTS.append([self.name,label,x,y,w,h,to])

    def button(self,x,y,w,label,to='',kind='plain',h=44):
        fill,stroke,fg={'plain':(PAPER,LINE,BLUE),'primary':(BLUE,None,PAPER),
                         'quiet':(PALE,None,BLUE),'text':(PAPER,None,BLUE)}[kind]
        self.box(x,y,w,h,fill,stroke,8)
        self.text(x+w/2,y+h/2,label,14,fg,True,'mm')
        self.hit(label,x,y,w,h,to)

    def icon(self,x,y,kind,fill=BLUE):
        if kind=='search':
            self.d.circle(x+7,y+7,6,self.color(fill))
            self.line(x+12,y+12,x+17,y+17,fill,2)
        if kind=='toc':
            for offset in (2,8,14):
                self.box(x,y+offset,2,2,fill)
                self.line(x+6,y+1+offset,x+18,y+1+offset,fill)

    def brand(self,x,y):
        if self.low:
            self.box(x,y,26,26,PALE,LINE,5)
            self.text(x+13,y+13,'G',13,GRAY,True,'mm')
        else:
            self.d.png(ROOT/'content/G04.png',x,y,26,26)

    def nav(self,title,back=''):
        self.box(0,0,self.w,96,PAPER)
        self.text(21,14,'9:41',13,INK,True)
        self.text(self.w-70,14,'◉  ▰',12,INK)
        self.box(self.w-103,42,87,32,PAPER,LINE,16)
        self.text(self.w-82,58,'···',17,INK,True,'mm')
        self.line(self.w-61,49,self.w-61,67)
        self.text(self.w-39,58,'◎',17,INK,True,'mm')
        if back:
            self.button(11,38,44,'〈',back,'text')
            self.text(65,52,title,17,INK,True)
        else:
            self.brand(16,46)
            self.text(51,51,title,19,INK,True)
        self.line(0,95,self.w,95)

    def save(self):
        (OUT/(self.name+'.svg')).write_text(self.d.finish(),encoding='utf-8')

def tabbar(b):
    y=b.h-67
    b.box(0,y,b.w,67,PAPER)
    b.line(0,y,b.w,y)
    for x,label,icon in [(b.w*.17,'发现','search'),(b.w*.5,'创作','toc'),(b.w*.83,'我的','person')]:
        clr=BLUE if label=='发现' else GRAY
        if icon=='person':
            b.d.circle(x,y+13,4,b.color(clr))
            b.d.commands.append(f'<path d="M{x-8} {y+26}a8 8 0 0 1 16 0" fill="none" stroke="{b.color(clr)}" stroke-width="1.6"/>')
        else:
            b.icon(x-8,y+10,icon,clr)
        b.text(x,y+30,label,12,clr,False,'mt')
    b.box((b.w-96)//2,b.h-9,96,4,INK,None,2)

def article_row(b,x,y,width,a,card,layout,highlight=False):
    title=b.wrap(a['title'],width-30,17,True,2)
    box_height=122+(len(title)-1)*23
    if highlight:
        b.box(x-2,y-2,width+4,box_height+4,PALE,BLUE,12)
    if card:
        b.box(x,y,width,box_height,PAPER,LINE,12)
    else:
        b.box(x,y,width,box_height)
        b.line(x+15,y+box_height-1,x+width-15,y+box_height-1)
    x+=15
    right=x+width-30
    end=b.paragraph(x,y+13,a['title'],width-30,17,23,INK,True,2)
    labels=[a['jobTags'][0],a['majorTags'][0]]
    if a['companyTags']: labels.append(a['companyTags'][0])
    px=x
    for label in labels:
        ww=b.measure(label,11)+14
        b.box(px,end+1,ww,20,PALE,None,4)
        b.text(px+7,end+5,label,11,BLUE)
        px+=ww+5
    b.paragraph(x,end+28,a['summary'],width-30,13,19,GRAY,False,2)
    b.text(x,y+box_height-21,'城院帮资料整理',11,GRAY)
    destination='R01-article-'+layout if a['id']=='A01' else 'R11-compare-'+layout if a['id']=='A10' else ''
    b.text(right,y+box_height-21,'阅读 ›',12,BLUE,True,'rt')
    b.hit(a['title'],x-4,y+4,width-22,box_height-8,destination)
    return box_height

def active_tag(b,x,y,label,target):
    width=b.measure(label,12)+26
    b.box(x,y+8,width,28,PALE,None,6)
    b.text(x+8,y+16,label+' ×',12,BLUE)
    b.hit('移除 '+label,x,y,width,44,target)
    return x+width+5

def discover(name,layout,state='default',low=False):
    b=Board(name,low=low)
    base_hits=len(HOTSPOTS)
    other='B' if layout=='A' else 'A'
    start='D01-discover-'+layout
    filtered='D03-java-'+layout
    jobs='D02-job-'+layout
    pane='D04-filter-'+layout
    error='D08-error-'+layout
    selected='D05-applied-'+layout
    recover='D06-removed-'+layout
    keyword = '深海考古' if state=='empty' else 'Java 后端' if state in ('java','applied','removed','error','sheet','chosen','major','return','append-error') else ''
    condition=state in ('applied','removed','error','return')
    b.nav('城院帮')
    b.box(16,107,343,44,PAPER,LINE,9)
    b.icon(29,120,'search',GRAY)
    b.text(55,121,keyword or '搜索标题或摘要',14,INK if keyword else GRAY)
    b.hit('输入关键词',16,107,278,44,'D07-search-'+layout)
    b.button(291,107,68,'搜索',filtered,'text')
    b.button(16,162,113,'岗位 ▾',jobs)
    b.button(137,162,105,'进一步筛选',pane)
    y=208
    if keyword:
        tx=active_tag(b,16,y,keyword,start)
        if condition:
            tx=active_tag(b,tx,y,'计算机科学与技术',filtered)
        if state in ('applied','error'):
            active_tag(b,tx,y,'拼多多',recover)
        y+=45
        b.button(16,y,76,'清除条件',start,'text')
    total=1 if state in ('applied','error') else 2 if state in ('java','removed','sheet','chosen','major','return') else 6
    if not keyword:
        b.text(17,y+16,'经验 · '+str(total) if state not in ('loading','zero','empty') else '经验',13,INK,True)
    elif state not in ('empty', 'loading'):
        b.text(98,y+16,str(total)+' 篇经验',12,GRAY)
    b.box(198,y+5,161,38,PALE,None,7)
    b.button(200,y+6,77,'认可排序','','quiet',36)
    b.button(278,y+6,79,'最新排序','','primary' if state=='latest' else 'plain',36)
    if not keyword:
        b.hit('认可排序',198,y,80,44,start)
        b.hit('最新排序',278,y,81,44,'D13-latest-'+layout)
    y+=51
    if state in ('zero','empty'):
        b.box(16,y,343,180,PAPER,LINE,12)
        b.text(187,y+38,'还没有公开经验' if state=='zero' else '没有符合当前条件的经验',17,INK,True,'mt')
        b.text(187,y+73,'可重新读取经验列表' if state=='zero' else '试试其他关键词，或清除筛选条件',13,GRAY,False,'mt')
        b.button(108,y+116,159,'重新读取' if state=='zero' else '清除条件',start,'primary')
    elif state=='loading':
        for i in range(3):
            b.box(16,y+i*109,343,97,PAPER,None,10)
            b.box(34,y+17+i*109,202,12,LINE,None,4)
            b.box(34,y+43+i*109,278,8,PALE,None,4)
            b.box(34,y+64+i*109,216,8,PALE,None,4)
        b.text(188,y+337,'正在读取经验…',12,GRAY,False,'mt')
    else:
        if state=='default':
            b.hit('向下浏览列表',16,685,343,56,'D18-list-tail-'+layout)
        if state=='error':
            b.box(16,y,343,100,RED_BG,None,9)
            b.text(28,y+14,'读取失败，当前显示上次读取的结果',13,RED,True)
            b.text(28,y+43,'当前条件仍保留',12,GRAY)
            b.button(241,y+45,104,'按原条件重试',selected,'plain')
            y+=110
        entries=BOOK if state not in ('java','applied','removed','error','sheet','chosen','major','latest','return') else (
            list(reversed(BOOK)) if state=='latest' else ['A01'] if state in ('applied','error') else ['A01','A10'])
        if state in ('list-tail','more','append-error'):
            entries=BOOK[2:4]
        elif state=='more-done':
            entries=BOOK[4:]
        for aid in entries:
            y+=article_row(b,16,y,343,ARTICLES[aid],layout=='A',layout,highlight=state=='return' and aid=='A01')
            y+=10 if layout=='A' else 0
            if y+145>b.h-67: break
        if b.h-y>=120:
            b.button(115,y+3,145,'正在加载更多…' if state=='more' else '加载更多','D10-more-'+layout,'plain')
        elif state in ('more','append-error'):
            b.box(0,b.h-108,375,41,PAPER)
            if state=='more':
                b.text(188,b.h-96,'正在加载更多…',13,GRAY,False,'mt')
                b.hit('加载完成',100,b.h-109,175,42,'D19-more-done-'+layout)
            else:
                b.button(107,b.h-111,161,'重试加载更多','D10-more-'+layout)
    tabbar(b)
    if state in ('sheet','chosen','major','job','search'):
        del HOTSPOTS[base_hits:]
        y=352 if state in ('sheet','chosen','major') else 443 if state=='job' else 482
        b.d.commands.append('<rect width="375" height="812" fill="#0d1828" opacity=".34"/>')
        b.box(0,y,375,812-y,PAPER,None,16)
        b.text(16,y+21,{'job':'选择目标岗位','search':'搜索经验'}.get(state,'进一步筛选'),18,INK,True)
        b.button(313,y+9,46,'×',filtered if keyword else start,'text')
        if state=='job':
            for i,(label,dest) in enumerate([('全部岗位',start),('Java 后端',filtered),('C++ 后端',''),('Web 前端','')]):
                b.button(16,y+66+i*54,343,label,dest,'quiet' if i==1 else 'plain')
        elif state=='search':
            for i,(label,dest) in enumerate([('Java 后端',filtered),('深海考古','D09-empty-'+layout)]):
                b.box(16,y+72+i*66,343,52,BG,None,8)
                b.icon(29,y+90+i*66,'search',GRAY)
                b.text(55,y+90+i*66,label,14,INK)
                b.button(279,y+76+i*66,72,'搜索',dest,'primary')
        else:
            major=state in ('major','chosen')
            company=state=='chosen'
            b.text(16,y+75,'专业',13,INK,True)
            b.button(16,y+99,172,('✓ ' if major else '')+'计算机科学与技术','D14-major-'+layout,'quiet' if major else 'plain')
            b.button(197,y+99,162,'软件工程','','plain')
            b.text(16,y+158,'公司',13,INK,True)
            b.button(16,y+182,108,('✓ ' if company else '')+'拼多多','D15-chosen-'+layout,'quiet' if company else 'plain')
            b.button(132,y+182,108,'招商银行','','plain')
            b.text(16,y+240,'同专业 · 登录后按本人资料筛选',13,GRAY)
            b.button(284,y+227,75,'去登录','R05-login-'+layout,'text')
            b.text(16,y+286,'主题',13,INK,True)
            b.button(16,y+309,108,'第一份实习','','plain')
            b.button(132,y+309,108,'简历与投递','','plain')
            b.button(16,741,98,'重置',pane)
            b.button(123,741,236,'应用','D05-applied-'+layout if company else recover if major else filtered,'primary')
    b.save()

def render_article(name,width=375,layout='A',state='top',low=False):
    desktop=width>=1000
    h=1000 if desktop else 812
    b=Board(name,width,h,low=low)
    back='D16-return-'+layout
    alt=state=='compare'
    base_hits=len(HOTSPOTS)
    a=ARTICLES['A10' if alt else 'A03' if state in ('image','image-ok') else 'A01']
    logged=state in ('restored','saved','end')
    if desktop:
        b.box(0,0,width,68,PAPER)
        b.brand(44,20)
        b.text(82,22,'城院帮',20,INK,True)
        b.text(1198,26,'校园账号登录',14,BLUE)
        b.line(0,67,width,67)
        b.text(234,98,'发现经验  /  经验详情',14,GRAY)
        x,content,y=234,666,145
        b.box(966,152,238,445,PAPER,LINE,12)
        b.text(985,174,'文章目录',16,INK,True)
        for i,title in enumerate(CHAPTERS[:10]):
            b.text(985,218+i*33,title.split('：')[0][:17],13,BLUE if not i else GRAY,not i)
        b.box(1320,h-104,48,48,PAPER,LINE,24)
        b.text(1344,h-80,'↑',22,BLUE,True,'mm')
    else:
        b.box(0,0,width,h,PAPER)
        b.nav('校园账号' if state=='login' else '经验详情','R04-jump-'+layout if state=='login' else back)
        x,content,y=16,width-32,112
        if state not in ('unavailable','login','notoc'):
            b.box(width-60,105,44,44,PAPER,LINE,8)
            b.icon(width-47,118,'toc')
            if state not in ('notoc','compare','image','image-ok'):
                b.hit('目录',width-60,105,44,44,'R03-toc-'+layout)
        if state=='unavailable':
            b.text(width/2,248,'这篇经验已不可公开阅读',18,INK,True,'mt')
            b.button(104,300,167,'返回发现',back,'primary')
            b.save()
            return
    if state in ('top','notoc','compare'):
        cap=content if desktop else content-52
        y=b.paragraph(x,y,a['title'],cap,34 if desktop else 22,48 if desktop else 32,INK,True)
        for label,xx in [('Java 后端',x),('计算机科学与技术',x+76)]:
            ww=b.measure(label,12)+16
            b.box(xx,y+12,ww,24,PALE,None,5)
            b.text(xx+8,y+19,label,12,BLUE)
        b.text(x,y+45,'城院帮资料整理 · 公开来源',13,GRAY)
        y=b.paragraph(x,y+71,a['summary'],content,14 if not desktop else 17,23,GRAY)
        b.line(x,y+8,x+content,y+8)
        y+=28
        if alt:
            b.paragraph(x,y,a['sourceNote'],content,16,27,INK)
        else:
            y=b.paragraph(x,y,SECTIONS[0],content,18 if desktop else 16,32 if desktop else 27,INK)
            if y+50<b.h-112:
                y=b.paragraph(x,y+13,CHAPTERS[0],content,22 if desktop else 19,30,INK,True)
                first_body=next(p for p in SECTIONS[1].split('\n\n')[1:] if not p.startswith('#'))
                room=max(1,(b.h-117-y)//27)
                b.paragraph(x,y+6,first_body,content,18 if desktop else 16,32 if desktop else 27,INK,False,room)
    elif state!='login':
        chap=3 if state in ('jump','prompt','restored','saved') else 2
        y=164
        if state=='end':
            b.text(x,y,'来源与评论',20,INK,True)
            y+=45
            for link in a['sourceLinks']:
                y=b.paragraph(x,y,link['label'],content,14,30,BLUE)
            b.line(x,y+5,x+content,y+5)
            b.text(x,y+30,'评论',18,INK,True)
            b.text(x,y+68,'还没有评论',14,GRAY)
            b.button(x,y+103,110,'发表评论')
            b.button(x,y+161,85,'举报')
        else:
            chapter='如何讲清项目的四个问题' if state in ('image','image-ok') else CHAPTERS[chap]
            y=b.paragraph(x,y,chapter,content,21,31,INK,True)+14
            if logged:
                b.text(x,y,'城院帮资料整理 · 来源按原文列出',12,GREEN)
                y+=27
            if state=='image':
                b.box(x,y,content,141,BG,LINE,8)
                b.text(x+15,y+26,'项目复盘配图加载失败',16,INK,True)
                b.text(x+15,y+61,'你可以先继续阅读正文',13,GRAY)
                b.button(x+15,y+88,99,'重试图片','R10-image-ok')
                y+=155
            if state=='image-ok':
                image=Image.open(ROOT/'content/A03-image.png').convert('RGB')
                image.thumbnail((int(content*2),360))
                b.d.png(ROOT/'content/A03-image.png',x,y,image.width//2,image.height//2)
                y+=image.height//2+12
            paragraphs=[a['summary']] if state in ('image','image-ok') else SECTIONS[chap+1].split('\n\n')[1:]
            for p in paragraphs:
                if not p.strip(): continue
                if y>h-110: break
                is_h=p.startswith('### ')
                y=b.paragraph(x,y,p.removeprefix('### '),content,17 if is_h else 16,27,INK,is_h,max(1,(h-113-y)//27))+13
    if not desktop and state!='login':
        y=h-70
        b.box(0,y,width,70,PAPER)
        b.line(0,y,width,y)
        b.button(16,y+7,95,'已收藏' if state=='saved' else '收藏', 'R06-restored-'+layout if state=='saved' else 'R07-saved-'+layout if logged else 'R04-prompt-'+layout,
                 'quiet' if state=='saved' else 'plain')
        b.button(120,y+7,76,'点赞')
        b.button(204,y+7,68,'硬币')
        b.button(width-91,y+7,75,'评论','R08-end-'+layout)
        b.box((width-98)//2,h-8,98,4,INK,None,2)
        if state=='top':
            b.hit('滑动至中段',16,624,width-32,105,'R02-middle-'+layout)
        if state=='toc':
            del HOTSPOTS[base_hits:]
            b.d.commands.append(f'<rect width="{width}" height="812" fill="#0d1828" opacity=".34"/>')
            b.box(0,340,width,472,PAPER,LINE,16)
            b.text(16,364,'文章目录',19,INK,True)
            b.button(width-62,351,46,'×','R02-middle-'+layout,'text')
            for i,title in enumerate(CHAPTERS[:8]):
                b.button(16,411+i*44,width-32,('当前 · ' if i==2 else '')+title.split('：')[0], 'R04-jump-'+layout if i==3 else '', 'quiet' if i==2 else 'text')
        if state=='prompt':
            del HOTSPOTS[base_hits:]
            b.d.commands.append(f'<rect width="{width}" height="812" fill="#0d1828" opacity=".34"/>')
            b.box(24,290,width-48,201,PAPER,LINE,16)
            b.text(43,314,'登录后收藏',20,INK,True)
            b.paragraph(43,354,'登录后回到当前章节，继续收藏这篇经验。',width-86,14,22,GRAY)
            b.button(43,427,100,'继续阅读','R04-jump-'+layout)
            b.button(151,427,width-193,'登录校园账号','R05-login-'+layout,'primary')
    if state=='login':
        b.text(16,130,'校园账号登录',26,INK,True)
        b.text(16,178,'登录后回到原文章与章节',14,GRAY)
        for i,(label,value) in enumerate([('校园邮箱','输入校园邮箱'),('密码','输入密码')]):
            b.text(16,240+i*94,label,14,INK,True)
            b.box(16,265+i*94,width-32,44,BG,LINE,8)
            b.text(28,278+i*94,value,14,GRAY)
        b.button(16,472,width-32,'模拟登录成功','R06-restored-'+layout,'primary')
        b.button(16,530,width-32,'取消，返回文章','R04-jump-'+layout,'text')
    b.save()

def whole_article():
    # Size the full-length source board from actual wrapped lines.
    draft=Board('measure')
    blocks=[block for block in re.split(r'\n\s*\n',BODY) if block.strip()]
    runs=[]
    for block in blocks:
        size=20 if block.startswith('## ') else 17 if block.startswith('### ') else 16
        bold=block.startswith('#')
        content=re.sub(r'^#{1,6} ', '', block)
        lines=draft.wrap(content.replace('\n',' '),343,size,bold)
        runs.append((size,bold,lines))
    height=235+sum(len(lines)*27+15 for _,_,lines in runs)+145
    b=Board('R14-full-article',375,height)
    b.box(0,0,375,height,PAPER)
    b.nav('经验详情','D01-discover-A')
    b.paragraph(16,117,ARTICLES['A01']['title'],343,22,31,INK,True)
    b.text(16,186,'城院帮资料整理 · 公开来源',13,GRAY)
    y=219
    for size,bold,lines in runs:
        for line in lines:
            b.text(16,y,line,size,INK,bold)
            y+=27
        y+=15
    b.text(16,y+20,'原始资料',18,INK,True)
    for i,link in enumerate(ARTICLES['A01']['sourceLinks']):
        b.paragraph(16,y+54+i*32,link['label'],343,13,19,BLUE)
    b.save()

def review_entry():
    b=Board('00-review-entry')
    b.nav('城院帮')
    b.text(16,127,'发现与阅读评审',24,INK,True)
    b.paragraph(16,177,'任选一种结果列表，使用相同任务走查。',343,15,24,GRAY)
    b.button(16,244,166,'轻卡片 A','D01-discover-A','primary')
    b.button(193,244,166,'连续列表 B','D01-discover-B','primary')
    b.line(16,320,359,320)
    b.text(16,350,'继续核对状态',17,INK,True)
    b.button(16,396,165,'模拟读取失败 · A','D08-error-A')
    b.button(193,396,166,'模拟读取失败 · B','D08-error-B')
    b.button(16,458,165,'首次加载','D11-loading-A')
    b.button(193,458,166,'无公开经验','D12-zero-A')
    b.button(16,520,165,'续页读取失败','D17-append-error-A')
    b.button(193,520,166,'图片加载失败','R10-image-error')
    b.button(16,582,165,'桌面阅读','R09-desktop')
    b.button(193,582,166,'窄屏阅读','R09-responsive')
    b.save()

def flow():
    b=Board('00-task-flow',1200,600)
    b.brand(36,33)
    b.text(80,32,'城院帮 G04 · 发现与阅读任务流程',27,INK,True)
    b.text(36,91,'游客和校园认证读者共用公开发现及详情。认证状态影响收藏与受保护信息。',15,GRAY)
    stages=[('1  发现 A / B','搜索 · 岗位'),('2  进一步筛选','暂选 · 应用 · 移除'),('3  查看结果','按回执更新'),('4  阅读与跳章','正文 · 当前目录'),('5  收藏与返回','恢复章位置和列表')]
    for i,(title,sub) in enumerate(stages):
        x=36+i*226
        b.box(x,170,201,86,PAPER,LINE,12)
        b.text(x+15,189,title,17,INK,True)
        b.text(x+15,224,sub,13,GRAY)
        if i<len(stages)-1: b.text(x+212,211,'→',16,BLUE,True,'mm')
    for x,y,title,small in [(488,304,'筛选无匹配','清除条件，再看全量'),(488,409,'读取失败','保留条件和旧内容，重试'),(940,304,'游客收藏','登录成功或取消后回原章节'),(940,409,'认证后收藏','回读收藏状态，返回原列表')]:
        b.line(x+100,y-15,x+100,y-2,BLUE)
        b.box(x,y,201,72,PALE,None,10)
        b.text(x+12,y+13,title,15,BLUE,True)
        b.paragraph(x+12,y+40,small,178,12,16)
    b.save()

flow()
review_entry()
whole_article()
for layout in ('A','B'):
    discover('L01-wire-'+layout,layout,low=True)
    pairs=[('D01-discover','default'),('D02-job','job'),('D03-java','java'),('D04-filter','sheet'),
           ('D05-applied','applied'),('D06-removed','removed'),('D07-search','search'),('D08-error','error'),
           ('D09-empty','empty'),('D10-more','more'),('D11-loading','loading'),('D12-zero','zero'),
           ('D13-latest','latest'),('D14-major','major'),('D15-chosen','chosen'),('D16-return','return'),('D17-append-error','append-error'),('D18-list-tail','list-tail'),('D19-more-done','more-done')]
    for name,state in pairs:
        discover(name+'-'+layout,layout,state)
    for prefix,state in [('R01-article','top'),('R02-middle','mid'),('R03-toc','toc'),('R04-jump','jump'),
                         ('R04-prompt','prompt'),('R05-login','login'),('R06-restored','restored'),
                         ('R07-saved','saved'),('R08-end','end'),('R11-compare','compare')]:
        render_article(prefix+'-'+layout,layout=layout,state=state)
render_article('L02-wire-reader',low=True)
render_article('R09-desktop',1440)
render_article('R09-responsive',390)
render_article('R10-image-error',state='image')
render_article('R10-image-ok',state='image-ok')
render_article('R12-no-toc',state='notoc')
render_article('R13-unavailable',state='unavailable')
with (ROOT/'hotspots.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.writer(f)
    writer.writerow(['board','control','x','y','width','height','target'])
    writer.writerows(row for row in HOTSPOTS if not row[0].startswith('L'))
for _,_,_,_,_,_,target in HOTSPOTS:
    assert (OUT/(target+'.svg')).is_file(),target
