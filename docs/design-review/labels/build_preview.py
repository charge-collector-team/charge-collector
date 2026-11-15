"""v11 은색 교환 태그를 재사용하고, 한정 전용 프레임을 제안하는 벡터 시안. 앱 미적용."""
from pathlib import Path
from PIL import Image
from io import BytesIO
import base64, math

root=Path(__file__).parent

def asset(n):
    b=BytesIO();Image.open(root/n).save(b,format='PNG')
    return 'data:image/png;base64,'+base64.b64encode(b.getvalue()).decode()

def arc(cx,cy,r,a,b):
    def p(t):return cx+r*math.cos(math.radians(t)),cy+r*math.sin(math.radians(t))
    x,y=p(a);xx,yy=p(b)
    return f'M{x:.2f} {y:.2f} A{r} {r} 0 {int(abs(b-a)>180)} 1 {xx:.2f} {yy:.2f}'

trade=(root/'trade-tag-v11.svg').read_text()
trade=trade[trade.index('>')+1:trade.rindex('</svg>')]
# 원본 도형·금속 그라데이션·화살표는 재사용하고 종이색 외곽만 진열면색으로 바꾼다.
trade=trade.replace('#F6F3F2','#151618')
cat=asset('goyang.webp');wolf=asset('wolf.webp')
s=['''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1440" height="1040" viewBox="0 0 1440 1040">
<defs>
<linearGradient id="jump" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#F8FBFC"/><stop offset=".25" stop-color="#83909A"/><stop offset=".52" stop-color="#E4EAED"/><stop offset=".7" stop-color="#4A555F"/><stop offset="1" stop-color="#C5D0D6"/></linearGradient>
<linearGradient id="blackMetal" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#A9B0B9"/><stop offset=".10" stop-color="#20242B"/><stop offset=".32" stop-color="#3C424A"/><stop offset=".46" stop-color="#101215"/><stop offset=".56" stop-color="#858F99"/><stop offset=".63" stop-color="#2A3037"/><stop offset=".86" stop-color="#14171B"/><stop offset="1" stop-color="#727D87"/></linearGradient>
<linearGradient id="platinum" x1="0" y1="0" x2=".85" y2="1"><stop stop-color="#FAFCFC"/><stop offset=".23" stop-color="#B1BAC1"/><stop offset=".39" stop-color="#F2F4F3"/><stop offset=".53" stop-color="#8B969F"/><stop offset=".78" stop-color="#DDE2E5"/><stop offset="1" stop-color="#F5F7F6"/></linearGradient>
<radialGradient id="enamel" cx=".3" cy=".15" r=".95"><stop stop-color="#52636C"/><stop offset=".25" stop-color="#26383F"/><stop offset="1" stop-color="#111E23"/></radialGradient>
<clipPath id="portrait"><circle cx="1168" cy="421" r="112"/></clipPath>
<path id="topType" d="M1046 421 A122 122 0 0 1 1290 421"/>
<path id="bottomType" d="M1044 421 A124 124 0 0 0 1292 421"/>
</defs>
<rect width="1440" height="1040" fill="#F7F8FA"/>
<g font-family="Apple SD Gothic Neo, sans-serif" fill="#1D1E21">
<text x="64" y="65" font-size="30" font-weight="600">교환은 키링, 한정은 전용 에디션</text>
<text x="64" y="105" font-size="18" fill="#686A72">이전 은색 교환 태그 재사용 · 금속과 연결 방식으로 차이를 주는 시안</text>
<text x="1376" y="65" text-anchor="end" font-size="15" fill="#686A72">DESIGN PREVIEW · 앱 미적용</text>
''']
for x,title in [(64,'기본 메달'),(512,'교환 키링'),(960,'한정 에디션')]:
 s.append(f'<rect x="{x}" y="151" width="416" height="668" rx="20" fill="#FFF" stroke="#E0E1E6"/><text x="{x+28}" y="195" font-size="21" font-weight="600">{title}</text><rect x="{x+20}" y="221" width="376" height="400" rx="16" fill="#151618"/>')
for x in [64,512]:
 s.append(f'<image x="{x+58}" y="240" width="300" height="336" xlink:href="{cat}"/>')
# 교환 태그: 메달 오른쪽 아래의 작은 연결 고리에 은색 태그를 끼운다.
s.append('''<g transform="translate(512 0)"><ellipse cx="309" cy="525" rx="9" ry="15" transform="rotate(-25 309 525)" fill="none" stroke="#090B0E" stroke-width="7"/><ellipse cx="309" cy="525" rx="9" ry="15" transform="rotate(-25 309 525)" fill="none" stroke="url(#jump)" stroke-width="4.5"/>
<svg x="292" y="497" width="100" height="77" viewBox="0 0 130 100">'''+trade+'''</svg><path d="M302 513q-3 9 5 15" fill="none" stroke="url(#jump)" stroke-width="4.5" stroke-linecap="round"/></g>''')
# 한정: 원래 그림은 작은 원형 창에 그대로 보이고, 바깥 프레임만 전용 금속으로 교체한다.
cx,cy=1168,421
s.append('''<rect x="1150" y="252" width="36" height="30" rx="10" fill="url(#blackMetal)" stroke="#B9C2C9" stroke-width="1.5"/><rect x="1158" y="259" width="20" height="13" rx="5" fill="#151618" stroke="#727D87" stroke-width="1"/>
<circle cx="1168" cy="421" r="145" fill="#0A0C0F"/>
<circle cx="1168" cy="421" r="143.5" fill="url(#blackMetal)" stroke="#AEB7BE" stroke-width="1.2"/>
<circle cx="1168" cy="421" r="134" fill="#171B20" stroke="#050607" stroke-width="1"/>
<circle cx="1168" cy="421" r="130.5" fill="url(#platinum)"/>
<circle cx="1168" cy="421" r="114.7" fill="#2A3036" stroke="#F1F4F4" stroke-width="1"/>''')
for i in range(84):
 a=math.radians(i*360/84);x=cx+137.5*math.cos(a);y=cy+137.5*math.sin(a);xx=cx+140.5*math.cos(a);yy=cy+140.5*math.sin(a)
 s.append(f'<path d="M{x:.2f} {y:.2f}L{xx:.2f} {yy:.2f}" stroke="#D0D7DC" opacity=".22" stroke-width=".6"/>')
scale=112/195
s.append(f'<g clip-path="url(#portrait)"><image x="{cx-300*scale:.3f}" y="{cy-330*scale:.3f}" width="{600*scale:.3f}" height="{672*scale:.3f}" xlink:href="{wolf}"/></g>')
s.append(f'<path d="{arc(cx,cy,142,208,306)}" fill="none" stroke="#E5EDF1" stroke-width="1.2" opacity=".8"/>')
for text, radius, start, end, font, bottom in [('SPECIAL EDITION',122,-130,-50,9.5,False),('늑구 · 무사 귀환',123,133,47,10.5,True)]:
    for i,ch in enumerate(text):
        if ch == ' ':continue
        angle=start+(end-start)*i/(len(text)-1)
        xx=cx+radius*math.cos(math.radians(angle));yy=cy+radius*math.sin(math.radians(angle))
        rotation=angle-90 if bottom else angle+90
        s.append(f'<g transform="translate({xx:.2f} {yy:.2f}) rotate({rotation:.2f})"><text text-anchor="middle" y="3" font-size="{font}" font-weight="600" fill="#F5F6F7" transform="translate(.3 .5)">{ch}</text><text text-anchor="middle" y="3" font-size="{font}" font-weight="600" fill="#454E56">{ch}</text></g>')
s.append('''<circle cx="1272" cy="526" r="24" fill="url(#platinum)" stroke="#161C22" stroke-width="1.3"/>
<circle cx="1272" cy="526" r="19.7" fill="url(#enamel)" stroke="#E3E8EB" stroke-width=".9"/>
<path d="M1259 518A15 15 0 0 1 1278 512" stroke="#B8C8D0" stroke-width="1" fill="none" opacity=".55"/>
<text x="1272" y="530" text-anchor="middle" font-size="11" font-weight="600" fill="#EEF2F2">한정</text>
''')
for x,qty in [(64,'×2'),(512,'×2'),(960,'×1')]:s.append(f'<text x="{x+64}" y="587" fill="#F7F8FA" font-size="18">{qty}</text>')
for x,title,grade,caption in [(64,'고양시 · 고양이','동 메달','기본 메달은 별도 표식 없이.'),(512,'고양시 · 고양이','교환 이력','이전 은색 태그를 연결고리에 매다는 형태.'),(960,'대전 · 늑구','한정 메달','흑니켈 테두리 · 백금 각인 · 에나멜 인장.')]:
 s.append(f'<text x="{x+28}" y="668" font-size="23" font-weight="600">{title}</text><text x="{x+28}" y="704" font-size="17" fill="#686A72">{grade}</text><text x="{x+28}" y="768" font-size="15" fill="#686A72">{caption}</text>')
s.append('''<rect x="64" y="855" width="1312" height="126" rx="16" fill="#FFF" stroke="#E0E1E6"/>
<text x="88" y="892" font-size="18" font-weight="600">수집품 자체가 달라 보이게</text>
<text x="88" y="927" font-size="17" fill="#686A72">교환은 원래 메달에 붙는 키링, 한정은 별도의 금속 프레임으로 구분하는 제안입니다.</text>
<text x="88" y="956" font-size="15" fill="#686A72">교환 이력은 현재 보유분의 출처를 뜻하지 않습니다. 한정 수량·발급번호는 이 시안에서 정하지 않았습니다.</text>
</g></svg>''')
(root/'label-preview-v2.svg').write_text(''.join(s))
print(root/'label-preview-v2.svg')
