"""공공데이터 전기차 충전소 → 시·군·구(일반구 포함) 매핑.

출처: 한국환경공단 전기자동차 충전소 정보 API (data.go.kr 15076352)
키: 저장소 루트 .env 의 EV_API_KEY (Decoding 키, 저장소에 올리지 않음). 키는 출력·저장하지 않는다.

사용:
  python3 fetch_and_map.py fetch   # 전국 충전기 목록을 raw/ 에 페이지별 JSON으로 저장
  python3 fetch_and_map.py map     # raw/ → stations.json, regions.json, report.txt
"""
import json
import pathlib
import re
import sys
import time
import urllib.parse
import urllib.error
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
RAW = HERE / "raw"
ENV = HERE.parent / ".env"
URL = "https://apis.data.go.kr/B552584/EvCharger/getChargerInfo"
ROWS = 3000  # 9999는 504(응답 시간 초과)가 나서 줄임


def api_key() -> str:
    for line in ENV.read_text(encoding="utf-8").splitlines():
        if line.startswith("EV_API_KEY="):
            return line.split("=", 1)[1].strip()
    sys.exit(f"EV_API_KEY 가 {ENV} 에 없음")


def get_page(key: str, page: int) -> dict:
    q = urllib.parse.urlencode({"serviceKey": key, "pageNo": page, "numOfRows": ROWS, "dataType": "JSON"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(f"{URL}?{q}", timeout=120) as r:
                body = r.read().decode("utf-8")
            break
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as e:
            # 키가 들어 있는 URL은 출력하지 않는다
            print(f"page {page} 실패 {type(e).__name__} {getattr(e, 'code', '')}, 재시도 {attempt + 1}/5")
            time.sleep(5 * (attempt + 1))
    else:
        sys.exit(f"page {page} 5번 실패")
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        sys.exit(f"JSON 아님 (page {page}): {body[:300]}")


def fetch() -> None:
    key = api_key()
    RAW.mkdir(exist_ok=True)
    first = get_page(key, 1)
    total = int(first.get("totalCount", 0))
    pages = -(-total // ROWS)
    print(f"totalCount={total} pages={pages}")
    (RAW / "page_0001.json").write_text(json.dumps(first, ensure_ascii=False), encoding="utf-8")
    for p in range(2, pages + 1):
        out = RAW / f"page_{p:04d}.json"
        if out.exists():
            continue
        out.write_text(json.dumps(get_page(key, p), ensure_ascii=False), encoding="utf-8")
        print(f"page {p}/{pages}")
        time.sleep(0.5)


def region_from_addr(addr: str) -> str | None:
    """'경기도 성남시 분당구 …' → '경기도 성남시 분당구' (일반구 포함).

    시·도는 이름 목록 대신 첫 단어로 읽는다(데이터에 '전남광주통합특별시' 같은 새 이름이 있음).
    """
    t = (addr or "").split()
    if not t:
        return None
    sido = t[0]
    if sido.startswith("세종"):
        return sido
    if len(t) < 2 or not re.search(r"(시|군|구)$", t[1]):
        return None
    if t[1].endswith("시") and len(t) > 2 and re.fullmatch(r"\S+구", t[2]):
        return f"{sido} {t[1]} {t[2]}"
    return f"{sido} {t[1]}"


def _dist1(a: str, b: str) -> bool:
    """같은 길이에서 한 글자만 다른지 (구 이름 오타 보정용)."""
    return sum(x != y for x, y in zip(a, b)) == 1


def items(doc: dict) -> list:
    it = doc.get("items", {})
    it = it.get("item", []) if isinstance(it, dict) else it
    return it if isinstance(it, list) else [it]


def map_() -> None:
    stations: dict[str, dict] = {}
    chargers = 0
    for f in sorted(RAW.glob("page_*.json")):
        for c in items(json.loads(f.read_text(encoding="utf-8"))):
            chargers += 1
            if c.get("delYn") == "Y":
                continue
            s = stations.setdefault(c["statId"], {
                "statId": c["statId"], "name": c.get("statNm"), "addr": c.get("addr"),
                "lat": c.get("lat"), "lng": c.get("lng"),
                "zcode": c.get("zcode"), "zscode": c.get("zscode"),
                "limitYn": c.get("limitYn"), "chargers": 0,
            })
            s["chargers"] += 1
    # API의 zscode는 시 단위(41130 = 성남시 전체)라 일반구는 주소의 구 이름으로 나눈다
    from collections import Counter, defaultdict
    by_code: dict[str, list] = defaultdict(list)
    no_code = [s for s in stations.values() if not s["zscode"]]
    for s in stations.values():
        if s["zscode"]:
            s["_addr_region"] = region_from_addr(s["addr"])
            by_code[s["zscode"]].append(s)

    regions: dict[str, dict] = {}
    typo_fixed, nn_filled, addr_fail = [], 0, 0
    for code, group in by_code.items():
        names = Counter(s["_addr_region"] for s in group if s["_addr_region"])
        addr_fail += sum(1 for s in group if not s["_addr_region"])
        gu_count = Counter(n.split()[2] for n in names.elements() if len(n.split()) == 3)
        # ponytail: 3곳 이상 나온 구만 실제 일반구로 본다. 새로 생긴 구가 전국에 3곳 미만이면 놓친다
        valid = {g for g, k in gu_count.items() if k >= 3}
        # 한 글자 차이인 두 이름 중 적은 쪽이 많은 쪽의 10% 미만이면 오타로 본다 (예: 만얀구 → 만안구)
        valid -= {a for a in valid for b in valid
                  if a != b and len(a) == len(b) and _dist1(a, b) and gu_count[a] < gu_count[b] * 0.1}
        city = Counter(" ".join(n.split()[:2]) for n in names.elements()).most_common(1)
        city = city[0][0] if city else code
        if not valid:
            for s in group:
                s["regionKey"], s["region"] = code, city
            continue
        known = []
        for s in group:
            n = s["_addr_region"]
            gu = n.split()[2] if n and len(n.split()) == 3 else None
            if gu and gu not in valid:
                near = [v for v in valid if len(v) == len(gu) and _dist1(v, gu)]
                if near:
                    typo_fixed.append(f"{city} {gu}→{near[0]}")
                    gu = near[0]
                else:
                    gu = None
            s["_gu"] = gu
            if gu and s["lat"] and s["lng"]:
                known.append((float(s["lat"]), float(s["lng"]), gu))
        for s in group:
            if not s["_gu"] and known and s["lat"] and s["lng"]:
                la, ln = float(s["lat"]), float(s["lng"])
                s["_gu"] = min(known, key=lambda k: (k[0] - la) ** 2 + (k[1] - ln) ** 2)[2]
                nn_filled += 1
            gu = s["_gu"] or "구 미상"
            s["regionKey"], s["region"] = f"{code}-{gu}", f"{city} {gu}"

    for s in stations.values():
        s.pop("_addr_region", None)
        s.pop("_gu", None)
        if not s.get("regionKey"):
            s["regionKey"] = s["region"] = None
        r = regions.setdefault(s["regionKey"], {"regionKey": s["regionKey"], "region": s["region"], "zscode": s["zscode"], "stations": 0})
        r["stations"] += 1
    regions.pop(None, None)
    (HERE / "stations.json").write_text(json.dumps(list(stations.values()), ensure_ascii=False, indent=0), encoding="utf-8")
    (HERE / "regions.json").write_text(json.dumps(sorted(regions.values(), key=lambda r: r["regionKey"]), ensure_ascii=False, indent=1), encoding="utf-8")
    gu_regions = sorted({r["region"] for r in regions.values() if r["regionKey"].count("-")})
    report = [
        f"충전기 행 {chargers}, 삭제 제외 충전소 {len(stations)}",
        f"지역(시·군·구, 일반구 포함) {len(regions)}개, 그중 일반구 {len(gu_regions)}개",
        f"zscode 없는 충전소 {len(no_code)}",
        f"주소에서 지역을 못 읽은 충전소 {addr_fail}",
        f"구 이름 오타 보정 {len(typo_fixed)}건: " + ", ".join(sorted(set(typo_fixed))[:20]),
        f"주소에 구가 없어 가장 가까운 충전소의 구로 채움 {nn_filled}건",
        f"'구 미상'으로 남은 지역: " + ", ".join(sorted(r["region"] for r in regions.values() if r["region"] and r["region"].endswith("구 미상"))),
        "일반구 목록: " + ", ".join(gu_regions),
    ]
    (HERE / "report.txt").write_text("\n".join(report), encoding="utf-8")
    print("\n".join(report))


if __name__ == "__main__":
    {"fetch": fetch, "map": map_}.get(sys.argv[1] if len(sys.argv) > 1 else "", lambda: sys.exit(__doc__))()
