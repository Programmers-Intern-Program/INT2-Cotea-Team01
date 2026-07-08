import json, glob, os

# 이 스크립트는 scripts/ 안에서 실행한다고 가정 (레포 루트 기준 상대경로)
KB_DIR = "../data_source/knowledge_base"
CP_DIR = "../data_source/common_pitfalls"
OUT_DIR = "../build"

def prefix(category, subcategory, text):
    tag = f"{category}/{subcategory}" if subcategory else category
    return f"[{tag}] {text}"

# ---- knowledge_base ----
kb_chunks = []
for path in sorted(glob.glob(f"{KB_DIR}/*.json")):
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    base = {
        "category": doc["category"],
        "subcategory": doc.get("subcategory"),
        "language": doc.get("language", "ko"),
    }
    for field_name, level_hint in [
        ("definition", None), ("when_to_use", None), ("java_specific_notes", None)
    ]:
        kb_chunks.append({
            **base,
            "chunk_id": f'{doc["doc_id"]}_{field_name}',
            "field": field_name,
            "content": prefix(doc["category"], doc.get("subcategory"), doc["content"][field_name]),
        })
    # applicable_scale (신규 필드) 청크
    if "applicable_scale" in doc:
        kb_chunks.append({
            **base,
            "chunk_id": f'{doc["doc_id"]}_applicable_scale',
            "field": "applicable_scale",
            "content": prefix(doc["category"], doc.get("subcategory"), doc["applicable_scale"]),
        })

with open(f"{OUT_DIR}/knowledge_base_chunks.json", "w", encoding="utf-8") as f:
    json.dump(kb_chunks, f, ensure_ascii=False, indent=2)
print(f"knowledge_base_chunks: {len(kb_chunks)}개")

# ---- common_pitfalls: 임베딩용 청크(의미 검색) ----
cp_chunks = []
cp_keywords = []  # 백엔드 문자열 매칭용, 임베딩 파이프라인과 분리
for path in sorted(glob.glob(f"{CP_DIR}/*.json")):
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    pid = doc["pitfall_id"]
    overrides = doc.get("level_overrides", {})
    for field_name in ["mistake_description", "detection_signal"]:
        chunk = {
            "chunk_id": f'{pid}_{field_name}',
            "field": field_name,
            "content": f"[{pid}] {doc[field_name]}",
        }
        # 개별 오버라이드가 있는 청크만 override_lv2/3/4 boolean을 채운다.
        # (hint_level_scope를 boolean으로 풀었던 것과 동일한 방식으로 통일 —
        # Chroma 메타데이터는 배열을 지원하지 않으므로 콤마 문자열 같은
        # 임시방편 대신 처음부터 boolean으로 저장한다.)
        if field_name in overrides:
            for lv in (2, 3, 4):
                chunk[f"override_lv{lv}"] = lv in overrides[field_name]
        cp_chunks.append(chunk)
    cp_keywords.append({
        "pitfall_id": pid,
        "keywords": doc.get("keywords", []),
        "exclusion_keywords": doc.get("exclusion_keywords", []),
        "keyword_confidence": doc.get("keyword_confidence", "medium"),
        "confidence_note": doc.get("confidence_note", ""),
    })

with open(f"{OUT_DIR}/common_pitfalls_chunks.json", "w", encoding="utf-8") as f:
    json.dump(cp_chunks, f, ensure_ascii=False, indent=2)
with open(f"{OUT_DIR}/common_pitfalls_keywords.json", "w", encoding="utf-8") as f:
    json.dump(cp_keywords, f, ensure_ascii=False, indent=2)
print(f"common_pitfalls_chunks: {len(cp_chunks)}개 (임베딩용)")
print(f"common_pitfalls_keywords: {len(cp_keywords)}개 항목 (백엔드 문자열 매칭용, 별도 파일)")
