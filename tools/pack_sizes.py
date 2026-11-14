"""앱에 넣을 충전소 데이터 후보 형식의 크기를 잰다. stations.json → out/ 에 후보 파일을 만든다.

사용: python3 -I pack_sizes.py
"""
import csv
import gzip
import json
import pathlib
import sqlite3

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "out"
OUT.mkdir(exist_ok=True)

stations = [s for s in json.loads((HERE / "stations.json").read_text(encoding="utf-8")) if s["regionKey"] and s["lat"] and s["lng"]]
regions = sorted({s["regionKey"] for s in stations})
ridx = {r: i for i, r in enumerate(regions)}


def row(s, with_addr):
    r = [s["statId"], s["name"], round(float(s["lat"]), 5), round(float(s["lng"]), 5), ridx[s["regionKey"]]]
    return r + [s["addr"]] if with_addr else r


def write_json(name, with_addr):
    p = OUT / name
    p.write_text(json.dumps({"regions": regions, "stations": [row(s, with_addr) for s in stations]}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return p


def write_csv(name, with_addr):
    p = OUT / name
    with p.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        for s in stations:
            w.writerow(row(s, with_addr))
    return p


def write_sqlite(name, with_addr):
    p = OUT / name
    p.unlink(missing_ok=True)
    db = sqlite3.connect(p)
    cols = "statId TEXT, name TEXT, lat REAL, lng REAL, region INTEGER" + (", addr TEXT" if with_addr else "")
    db.execute(f"CREATE TABLE station({cols})")
    db.executemany(f"INSERT INTO station VALUES({','.join('?' * (6 if with_addr else 5))})", [row(s, with_addr) for s in stations])
    db.execute("CREATE TABLE region(id INTEGER PRIMARY KEY, regionKey TEXT)")
    db.executemany("INSERT INTO region VALUES(?,?)", list(enumerate(regions)))
    db.execute("CREATE INDEX station_lat_lng ON station(lat, lng)")
    db.commit()
    db.execute("VACUUM")
    db.close()
    return p


def size(p):
    raw = p.read_bytes()
    return len(raw), len(gzip.compress(raw, 9))


if __name__ == "__main__":
    print(f"충전소 {len(stations)}곳, 지역 {len(regions)}개")
    src = HERE / "stations.json"
    print(f"{'stations.json(현재)':<28} 원본 {size(src)[0] / 1e6:6.1f}MB  gzip {size(src)[1] / 1e6:5.1f}MB")
    for addr in (False, True):
        tag = "주소 포함" if addr else "주소 없음"
        for fn, ext in ((write_json, "json"), (write_csv, "csv"), (write_sqlite, "db")):
            p = fn(f"stations_{'addr' if addr else 'min'}.{ext}", addr)
            a, g = size(p)
            print(f"{ext:<6} {tag:<20} 원본 {a / 1e6:6.1f}MB  gzip {g / 1e6:5.1f}MB")
