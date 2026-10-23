#!/usr/bin/env python3
"""한국관광공사 '관광 스토리텔링 DB' CSV(공공데이터포털, 이용허락범위 제한 없음) → 앱 스토리 뱅크 JSON.

사용: python3 tools/storydb_to_bank.py <csv(cp949 또는 utf-8)> app/src/main/assets/storybank/kto_storytelling.json
필드: 스팟아이디, 스팟명, 오디오명, 대본내용, 재생시간(초)
앱은 이 뱅크를 (1) "더 자세히"의 심화 레이어, (2) 창밖 모드·재탑승 추천의 보조 해설로 씁니다.
좌표가 없으므로 지명 매칭(스팟명 ⊂ 장소명/에피소드 제목)으로만 연결합니다.
"""
import csv, json, sys, re

def load(path):
    raw = open(path, 'rb').read()
    for enc in ('utf-8-sig', 'cp949', 'euc-kr'):
        try: text = raw.decode(enc); break
        except UnicodeDecodeError: continue
    rows = list(csv.reader(text.splitlines()))
    head = rows[0]; idx = {h.strip(): i for i, h in enumerate(head)}
    out = []
    for r in rows[1:]:
        if len(r) < 5: continue
        spot = r[idx['스팟명']].strip(); title = r[idx['오디오명']].strip()
        body = re.sub(r'[　\s]+', ' ', r[idx['대본내용']]).strip()
        try: sec = int(float(r[idx['재생시간(초)']]))
        except ValueError: sec = max(20, len(body) // 5)
        if not spot or not body: continue
        out.append({"id": r[idx['스팟아이디']].strip(), "spot": spot, "title": title, "text": body, "sec": sec})
    return out

if __name__ == '__main__':
    src, dst = sys.argv[1], sys.argv[2]
    bank = load(src)
    json.dump({"source": "한국관광공사 관광 스토리텔링 DB (공공데이터포털 3041768, 이용허락범위 제한 없음)", "count": len(bank), "items": bank},
              open(dst, 'w', encoding='utf-8'), ensure_ascii=False)
    print(f"{len(bank)} items -> {dst}")
