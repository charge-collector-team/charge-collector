"""피그마에서 내보낸 메달 판(PNG)을 지역·도안·등급별 메달 파일로 자른다.

사용: python3 -I cut_medals.py <sheet.png> <assets/medals 폴더> [배율, 기본 3]

판은 'Medal v26 / …' 컴포넌트 30개를 한 줄에 8개씩, 칸 200x224로 늘어놓은 것(피그마 공통 부품 페이지).
출력: {지역코드}_{도안}_{b|s|g}.webp (투명 배경, 손실 압축 품질 90).
원본 그림에는 연회색 배경이 박혀 있다. 가장자리에서 이어진 배경과 고리 구멍 안 배경만 지우고(그림 속 흰색은 남김),
경계 몇 픽셀은 배경색과의 거리로 반투명을 주고 배경색을 빼서 어두운 화면에 회색 띠가 남지 않게 한다.
마지막에 메달 원의 위치(칸 대비 중심과 지름)를 재서 출력한다. 앱의 WHOLE_DISC_* 값이 이 숫자다.
"""
import pathlib
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

# 판 위 순서 그대로: 피그마 이름, 지역코드(regions.csv), 도안 번호
ORDER = [
    ("goyang", "41280", 0), ("gyeongju", "47130", 0), ("yeongdeok", "47770", 0), ("icheon", "41500", 0),
    ("songpa", "11710", 0), ("hoengseong", "51730", 0), ("jeongeup", "52180", 0),
    ("gongju_bear", "44150", 2), ("gongju_chestnut", "44150", 1), ("gongju_fortress", "44150", 0),
]
TIERS = ["b", "s", "g"]
COLS, CELL_W, CELL_H = 8, 200, 224

sheet_path, out_dir = sys.argv[1], pathlib.Path(sys.argv[2])
scale = int(sys.argv[3]) if len(sys.argv) > 3 else 3
sheet = Image.open(sheet_path).convert("RGBA")
out_dir.mkdir(parents=True, exist_ok=True)

BG_TOLERANCE = 18.0  # 배경으로 볼 색 거리
EDGE = 40.0  # 경계에서 이 거리부터 완전히 불투명


def alpha_of(medal):
    """Background keyed out: colors, background color, alpha (1 inside, 0 background, soft on a 3px edge band)."""
    rgb = np.asarray(medal.convert("RGB")).astype(np.float32)
    bg = np.median(np.concatenate([rgb[0], rgb[-1], rgb[:, 0], rgb[:, -1]]), axis=0)
    dist = np.linalg.norm(rgb - bg, axis=2)
    near = dist < BG_TOLERANCE
    labels, _ = ndimage.label(near)
    # Background touching the border, plus the hole in the ring tab (the first background run under the tab metal).
    keep = set(labels[0]) | set(labels[-1]) | set(labels[:, 0]) | set(labels[:, -1])
    col = near[:, near.shape[1] // 2]
    y = int(np.argmax(~col))  # top of the tab metal
    y += int(np.argmax(col[y:]))  # first background below it: the hole
    keep.add(labels[y, near.shape[1] // 2])
    keep.discard(0)
    hole = np.isin(labels, list(keep))
    band = ndimage.binary_dilation(hole, iterations=3) & ~hole
    alpha = np.ones(dist.shape, np.float32)
    alpha[hole] = 0
    alpha[band] = np.clip((dist[band] - BG_TOLERANCE) / (EDGE - BG_TOLERANCE), 0, 1)
    return rgb, bg, alpha


def compose(rgb, bg, alpha):
    # Unmix the background from the soft edge so it reads clean on dark screens.
    soft = (alpha > 0) & (alpha < 1)
    a = np.maximum(alpha, 1e-3)[..., None]
    fg = np.where(soft[..., None], np.clip(bg + (rgb - bg) / a, 0, 255), rgb)
    return Image.fromarray(np.dstack([fg, alpha * 255]).astype(np.uint8), "RGBA")


# The silver ring tab has highlights as light as the background, so keying eats into it. The tab has the same
# shape in every tier: the silver medal takes the bronze one's alpha above the disc.
TAB_ROWS = 110 / 672

cells = [(key, design, tier) for _, key, design in ORDER for tier in TIERS]
total = 0
discs = []
bronze_alpha = None
for i, (key, design, tier) in enumerate(cells):
    x, y = (i % COLS) * CELL_W * scale, (i // COLS) * CELL_H * scale
    rgb, bg, alpha = alpha_of(sheet.crop((x, y, x + CELL_W * scale, y + CELL_H * scale)))
    rows = int(alpha.shape[0] * TAB_ROWS)
    if tier == "b":
        bronze_alpha = alpha
    elif tier == "s":
        alpha[:rows] = bronze_alpha[:rows]
    medal = compose(rgb, bg, alpha)
    path = out_dir / f"{key}_{design}_{tier}.webp"
    medal.save(path, "WEBP", quality=90, method=6)
    total += path.stat().st_size
    # Disc: the widest opaque span is its diameter; its bottom is the lowest opaque row.
    alpha = medal.getchannel("A").point(lambda a: 255 if a > 128 else 0)
    l, t, r, b = alpha.getbbox()
    d = r - l
    discs.append(((l + r) / 2 / medal.width, (b - d / 2) / medal.height, d / medal.width))

print(f"{len(cells)} medals, {total / 1024:.0f} KB")
n = len(discs)
print("disc center x %.4f, center y %.4f, diameter/width %.4f (mean of %d)" % (sum(a for a, _, _ in discs) / n, sum(c for _, c, _ in discs) / n, sum(w for _, _, w in discs) / n, n))
print("spread y %.4f..%.4f, w %.4f..%.4f" % (min(c for _, c, _ in discs), max(c for _, c, _ in discs), min(w for _, _, w in discs), max(w for _, _, w in discs)))
