"""통계청 2018 시군구 경계(TopoJSON)를 앱용 지도 자산으로 줄인다.

사용: python3 -I build_korea_map.py <topo.json> <regions.csv> <out.txt> [허용오차(도), 기본 0.0008]

출력 줄 형식 (좌표는 1e-4도 정수, 첫 점은 절대값, 다음 점부터는 앞 점과의 차이):
  c|p|m x,y dx,dy ...   선: c=해안·바깥 경계, p=시·도 경계(2026 기준), m=시·군·구 경계
  r 1,2|- x,y dx,dy ...  칠할 고리: 우리 지역 번호들(없으면 -). 한 지역은 고리 여러 개를 EvenOdd로 칠한다
선은 TopoJSON의 공유 호를 그대로 줄이므로 이웃 지역 경계가 어긋나지 않는다.
"""
import csv
import json
import math
import sys

topo_path, regions_path, out_path = sys.argv[1:4]
eps = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0008
KX = math.cos(math.radians(36.5))

t = json.load(open(topo_path, encoding="utf-8"))
sx, sy = t["transform"]["scale"]
tx, ty = t["transform"]["translate"]
arcs = []
for a in t["arcs"]:
    x = y = 0
    pts = []
    for dx, dy in a:
        x += dx
        y += dy
        pts.append((x * sx + tx, y * sy + ty))
    arcs.append(pts)
before = sum(len(a) for a in arcs)


def simplify(pts):
    if len(pts) < 3:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        i, j = stack.pop()
        (x1, y1), (x2, y2) = pts[i], pts[j]
        ax, ay = (x2 - x1) * KX, y2 - y1
        L = math.hypot(ax, ay)
        best, at = -1.0, -1
        for k in range(i + 1, j):
            px, py = (pts[k][0] - x1) * KX, pts[k][1] - y1
            d = abs(ax * py - ay * px) / L if L > 0 else math.hypot(px, py)
            if d > best:
                best, at = d, k
        if best > eps:
            keep[at] = True
            stack += [(i, at), (at, j)]
    out = [p for p, k in zip(pts, keep) if k]
    # A closed arc (an island) needs at least a triangle.
    if len(out) < 4 and pts[0] == pts[-1]:
        out = [pts[0], pts[len(pts) // 3], pts[2 * len(pts) // 3], pts[-1]]
    return out


arcs = [simplify(a) for a in arcs]
after = sum(len(a) for a in arcs)

geoms = list(t["objects"].values())[0]["geometries"]


def eff_sido(code):
    # 2026 기준: 광주(24)는 전남(36)과 통합, 군위군(37310)은 대구(22).
    if code == "37310":
        return "22"
    return "36" if code[:2] == "24" else code[:2]


users = {}
for gi, g in enumerate(geoms):
    polys = g["arcs"] if g["type"] == "MultiPolygon" else [g["arcs"]]
    for poly in polys:
        for ring in poly:
            for ai in ring:
                users.setdefault(ai if ai >= 0 else ~ai, set()).add(gi)

# Our regions -> features by name, within the province's 2018 sido codes.
SIDO = {
    "서울특별시": {"11"}, "부산광역시": {"21"}, "대구광역시": {"22", "37"}, "인천광역시": {"23"},
    "대전광역시": {"25"}, "울산광역시": {"26"}, "세종특별자치시": {"29"}, "경기도": {"31"},
    "강원특별자치도": {"32"}, "충청북도": {"33"}, "충청남도": {"34"}, "전북특별자치도": {"35"},
    "전남광주통합특별시": {"24", "36"}, "경상북도": {"37"}, "경상남도": {"38"}, "제주특별자치도": {"39"},
}
# 2018 이후 이름만 바뀐 곳.
RENAMED = {"미추홀구": "남구"}
# 2018 이후 새로 생긴 인천 구: 옛 구의 경계로 대신 칠한다(근사).
ALIAS = {"제물포구": ["중구", "동구"], "영종구": ["중구"], "서해구": ["서구"], "검단구": ["서구"]}
by_name = {}
for gi, g in enumerate(geoms):
    by_name.setdefault(g["properties"]["name"].replace(" ", ""), []).append(gi)

feature_regions = {}
unmatched = []
approx = []
for row in csv.reader(open(regions_path, encoding="utf-8")):
    idx, _, name, _ = row
    province, _, rest = name.partition(" ")
    codes = SIDO[province]

    def find(n):
        return [gi for gi in by_name.get(n.replace(" ", ""), []) if geoms[gi]["properties"]["code"][:2] in codes]

    hits = find(RENAMED.get(rest, rest)) if rest else [gi for gi, g in enumerate(geoms) if g["properties"]["code"][:2] in codes]
    if not hits and " " in rest:
        hits = find(rest.split(" ")[0])  # 2018에 없던 일반구: 시 전체 경계로
        if hits:
            approx.append(name)
    if not hits and rest in ALIAS:
        hits = [h for n in ALIAS[rest] for h in find(n)]
        approx.append(name)
    if not hits:
        unmatched.append(name)
    for gi in hits:
        feature_regions.setdefault(gi, []).append(int(idx))


def q(p):
    return round(p[0] * 1e4), round(p[1] * 1e4)


def enc(pts):
    out, px, py = [], 0, 0
    for i, p in enumerate(pts):
        x, y = q(p)
        out.append(f"{x},{y}" if i == 0 else f"{x - px},{y - py}")
        px, py = x, y
    return " ".join(out)


lines = []
counts = {"c": 0, "p": 0, "m": 0}
for ai, pts in enumerate(arcs):
    us = users.get(ai, set())
    if len(us) <= 1:
        cls = "c"
    else:
        cls = "p" if len({eff_sido(geoms[g]["properties"]["code"]) for g in us}) > 1 else "m"
    counts[cls] += 1
    lines.append(f"{cls} {enc(pts)}")

ring_count = 0
for gi, g in enumerate(geoms):
    tag = ",".join(map(str, feature_regions.get(gi, []))) or "-"
    polys = g["arcs"] if g["type"] == "MultiPolygon" else [g["arcs"]]
    for poly in polys:
        for ring in poly:
            pts = []
            for ai in ring:
                a = arcs[ai] if ai >= 0 else arcs[~ai][::-1]
                pts += a if not pts else a[1:]
            if len(pts) >= 4:
                lines.append(f"r {tag} {enc(pts)}")
                ring_count += 1

header = [
    "# 출처: 통계청 통계지리정보서비스(SGIS) 2018 시군구 경계, 공공누리 제1유형 (southkorea/southkorea-maps 경유).",
    f"# build_korea_map.py로 줄임: 허용오차 {eps}도, 점 {before} -> {after}.",
]
open(out_path, "w", encoding="utf-8").write("\n".join(header + lines) + "\n")
import os
print(f"points {before} -> {after} (eps {eps}), arcs {counts}, rings {ring_count}, size {os.path.getsize(out_path)} bytes")
print(f"features without our region: {[geoms[g]['properties']['name'] for g in range(len(geoms)) if g not in feature_regions]}")
print(f"our regions unmatched ({len(unmatched)}): {unmatched}")
print(f"our regions drawn with an older boundary ({len(approx)}): {approx}")
