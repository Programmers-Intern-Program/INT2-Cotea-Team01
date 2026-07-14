import json, glob, os

# 이 스크립트는 scripts/ 안에서 실행한다고 가정 (레포 루트 기준 상대경로)
KB_DIR = "../data_source/knowledge_base"
CP_DIR = "../data_source/common_pitfalls"
OUT_DIR = "../build"

# ---- knowledge_base: 문서 단위 통합 (태그 정확 매칭, 청크 분할 없음) ----
kb_docs = []
for path in sorted(glob.glob(f"{KB_DIR}/*.json")):
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    entry = {
        "category": doc["category"],
        "subcategory": doc.get("subcategory"),
        "language": doc.get("language", "ko"),
        "definition": doc["content"]["definition"],
        "when_to_use": doc["content"]["when_to_use"],
        "java_specific_notes": doc["content"]["java_specific_notes"],
    }
    if "applicable_scale" in doc:
        entry["applicable_scale"] = doc["applicable_scale"]
    for optional_field in ("distinguishing_from", "code_signals", "often_combined_with"):
        if optional_field in doc:
            entry[optional_field] = doc[optional_field]
    kb_docs.append(entry)

with open(f"{OUT_DIR}/knowledge_base_docs.json", "w", encoding="utf-8") as f:
    json.dump(kb_docs, f, ensure_ascii=False, indent=2)
print(f"knowledge_base_docs: {len(kb_docs)}개")

# ---- common_pitfalls: 백엔드 문자열 매칭용 키워드 ----
cp_keywords = []
for path in sorted(glob.glob(f"{CP_DIR}/*.json")):
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    pid = doc["pitfall_id"]
    cp_keywords.append({
        "pitfall_id": pid,
        "keywords": doc.get("keywords", []),
        "exclusion_keywords": doc.get("exclusion_keywords", []),
        "keyword_confidence": doc.get("keyword_confidence", "medium"),
        "confidence_note": doc.get("confidence_note", ""),
    })

with open(f"{OUT_DIR}/common_pitfalls_keywords.json", "w", encoding="utf-8") as f:
    json.dump(cp_keywords, f, ensure_ascii=False, indent=2)
print(f"common_pitfalls_keywords: {len(cp_keywords)}개 항목 (백엔드 문자열 매칭용)")
