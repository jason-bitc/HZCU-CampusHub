"""SVG artboard drawing primitives, coordinates in CSS pixels."""
from base64 import b64encode
from html import escape

class Canvas:
    def __init__(self, width, height, fill):
        self.width, self.height = width, height
        self.commands=[f'<rect width="{width}" height="{height}" fill="{fill}"/>']

    def rect(self,x,y,w,h,fill,stroke=None,r=0):
        self.commands.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke or "none"}"/>')

    def line(self,x,y,xx,yy,color,width):
        self.commands.append(f'<path d="M{x},{y}L{xx},{yy}" stroke="{color}" stroke-width="{width}" fill="none"/>')

    def text(self,x,y,value,size,color,weight,anchor):
        if anchor.startswith('m'): align='middle'
        elif anchor.startswith('r'): align='end'
        else: align='start'
        baseline='central' if anchor.endswith('m') else 'text-before-edge'
        self.commands.append(f'<text x="{x}" y="{y}" font-family="Microsoft YaHei, sans-serif" font-size="{size}" font-weight="{700 if weight else 400}" fill="{color}" text-anchor="{align}" dominant-baseline="{baseline}" xml:space="preserve">{escape(value)}</text>')

    def circle(self,x,y,r,color):
        self.commands.append(f'<circle cx="{x}" cy="{y}" r="{r}" fill="none" stroke="{color}" stroke-width="1.6"/>')

    def png(self,p,x,y,w,h):
        data=b64encode(p.read_bytes()).decode()
        self.commands.append(f'<image x="{x}" y="{y}" width="{w}" height="{h}" href="data:image/png;base64,{data}"/>')

    def finish(self):
        return f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{self.height}" viewBox="0 0 {self.width} {self.height}">' + ''.join(self.commands) + '</svg>'
