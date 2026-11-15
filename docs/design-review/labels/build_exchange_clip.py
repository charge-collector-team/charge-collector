"""선택된 3번 곡선 클립의 디자인 전달용 보드. 앱과 데이터는 변경하지 않음."""
from pathlib import Path
from io import BytesIO
from PIL import Image
import base64, math

root = Path(__file__).parent
b = BytesIO()
Image.open(root / 'goyang.webp').save(b, format='PNG')
asset = 'data:image/png;base64,' + base64.b64encode(b.getvalue()).decode()

def sector(outer, inner, a, z):
    def p(r, angle):
        return 208 + r * math.cos(math.radians(angle)), 421.6 + r * math.sin(math.radians(angle))
    x1,y1=p(outer,a); x2,y2=p(outer,z); x3,y3=p(inner,z); x4,y4=p(inner,a)
    return f'M{x1} {y1}A{outer} {outer} 0 0 1 {x2} {y2}L{x3} {y3}A{inner} {inner} 0 0 0 {x4} {y4}Z'

clip = f'''<g id="exchange-clip">
<path d="{sector(150,127,18,69)}" fill="url(#silver)" stroke="#7A8791" stroke-width="1.1" stroke-linejoin="round"/>
<path d="{sector(146.8,130,20.5,66.5)}" fill="url(#face)" stroke="#F5F8F9" stroke-width=".7"/>
<g transform="translate(308 516) rotate(-47) scale(.72)" stroke-linecap="round" stroke-linejoin="round" fill="none">
<path d="M-12-6H12L6-12M12 6H-12l6 6" transform="translate(.4 .6)" stroke="#F7FBFD" stroke-width="3.5"/>
<path d="M-12-6H12L6-12M12 6H-12l6 6" stroke="#263D50" stroke-width="2.8"/></g>'''
for a in (21,66):
    x=208+139*math.cos(math.radians(a)); y=421.6+139*math.sin(math.radians(a))
    clip += f'<circle cx="{x}" cy="{y}" r="1.5" fill="#7B8A94"/>'
clip += '</g>'
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="1200" height="900" viewBox="0 0 1200 900"><defs>
<linearGradient id="silver" x1="0" y1="0" x2=".85" y2="1"><stop stop-color="#FAFCFD"/><stop offset=".26" stop-color="#A2AFB9"/><stop offset=".43" stop-color="#EAF0F3"/><stop offset=".59" stop-color="#768590"/><stop offset=".82" stop-color="#D0DAE0"/><stop offset="1" stop-color="#F0F5F7"/></linearGradient>
<linearGradient id="face" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#EFF3F5"/><stop offset=".48" stop-color="#C3CFD7"/><stop offset="1" stop-color="#DDE5EA"/></linearGradient>
{clip}<g id="medal"><image x="58" y="240" width="300" height="336" xlink:href="{asset}"/><use xlink:href="#exchange-clip"/></g>
<clipPath id="detail-clip"><rect x="664" y="174" width="464" height="498" rx="16"/></clipPath></defs>
<rect width="1200" height="900" fill="#F7F8FA"/>
<g font-family="Apple SD Gothic Neo, sans-serif" fill="#1D1E21">
<text x="48" y="62" font-size="30" font-weight="600">교환 표식 · 3번 곡선 클립 선택안</text>
<text x="48" y="101" font-size="18" fill="#686A72">메달 오른쪽 아래 테두리에 밀착하는 은색 곡선 클립</text>
<text x="1152" y="62" text-anchor="end" font-size="14" fill="#686A72">DESIGN PREVIEW · 앱 미적용</text>
<rect x="48" y="144" width="568" height="642" rx="20" fill="white" stroke="#E0E1E6"/>
<rect x="72" y="174" width="520" height="498" rx="16" fill="#151618"/>
<use xlink:href="#medal" transform="translate(18 -130) scale(1.38)"/>
<text x="78" y="714" font-size="24" font-weight="600">고양시 · 고양이</text>
<text x="78" y="750" font-size="17" fill="#686A72">메달 그림은 유지 · 돌출되는 참과 고리 없음</text>
<rect x="640" y="144" width="512" height="642" rx="20" fill="white" stroke="#E0E1E6"/>
<rect x="664" y="174" width="464" height="498" rx="16" fill="#151618"/>
<g clip-path="url(#detail-clip)"><use xlink:href="#medal" transform="translate(27 -968) scale(2.8)"/></g>
<text x="670" y="714" font-size="24" font-weight="600">클립 확대</text>
<text x="670" y="750" font-size="17" fill="#686A72">테두리 곡률을 따라 배치 · 교환 화살표 음각</text>
<text x="48" y="835" font-size="18" font-weight="600">형태 선택 완료. 실제 표시 대상과 작은 화면 가독성은 개발 검토.</text>
<text x="48" y="868" font-size="16" fill="#686A72">교환 이력과 현재 보유분의 출처는 구분하며, 기능·데이터 변경은 별도 검토합니다.</text>
</g></svg>'''
(root / 'exchange-clip-selected.svg').write_text(svg)
