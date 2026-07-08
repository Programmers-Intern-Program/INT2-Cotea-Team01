# 코티(Cotea) RAG 데이터

## 폴더 구조

```
/rag
├── data_source/           # 원본 (사람이 직접 편집하는 곳)
│   ├── knowledge_base/    # 30개 — 알고리즘 카테고리별 개념 문서
│   └── common_pitfalls/   # 12개 — 카테고리 무관 자바 실수 패턴
├── build/                 # 생성된 결과물 (data_source로부터 재생성 가능, 직접 수정 금지)
│   ├── knowledge_base_chunks.json
│   ├── common_pitfalls_chunks.json
│   └── common_pitfalls_keywords.json
├── config/
│   └── field_level_mapping.json   # 필드(용도) -> 힌트 레벨 매핑 (레벨 체계 변경 시 여기만 수정)
├── scripts/
│   ├── regenerate_chunks.py       # data_source -> build 변환
│   ├── build_index.py             # build/*.json -> Chroma 벡터 DB 저장
│   └── query_test.py              # 검색 테스트
└── problems/ (gitignore)          # 문제별 메타데이터, 저작권 문제로 비공개 관리
```

**data_source를 고쳤다면 반드시 `scripts/regenerate_chunks.py`를 다시 실행해서 `build/`를 갱신해야 합니다.**

## `knowledge_base` 문서 스키마

```json
{
  "doc_id": "...",
  "category": "...",        // 20개 통제 어휘 중 하나
  "subcategory": "...",     // 없으면 null
  "content": {
    "definition": "...",
    "when_to_use": "...",
    "java_specific_notes": "..."
  },
  "applicable_scale": "...", // 이 접근이 통하는/안 통하는 입력 규모 기준
  "source": {...}
}
```

## `common_pitfalls` 항목 스키마

```json
{
  "pitfall_id": "...",
  "mistake_description": "...",
  "detection_signal": "...",
  "keywords": ["백엔드 문자열 매칭용 키워드 배열"],
  "exclusion_keywords": ["있으면 이 실수가 아니라고 볼 키워드 — 선택적"],
  "keyword_confidence": "high | medium | low",
  "confidence_note": "low일 때, 이유와 백엔드가 어떻게 다뤄야 하는지 — 선택적",
  "level_overrides": {
    "필드명": [3, 4]
  }
}
```

### `keyword_confidence`가 필요한 이유

`time_complexity_traps`, `integer_overflow`처럼 정적 분석 없이는 정밀하게 못 거르는 항목이 있습니다.
예: `"int "`, `"+="`는 정상 코드에도 흔해서, 이 keywords만으로 필터링하면 사실상 거의 다 걸립니다.
이런 항목은 `low`로 표시해, 백엔드가 매칭 결과를 강한 신호로 쓰지 않고
Claude 검증을 한 번 더 거치도록 유도합니다.

### `level_overrides`가 필요한 이유

기본적으로 각 필드(`mistake_description`, `detection_signal`)가 어느 힌트 레벨에서
노출되는지는 `config/field_level_mapping.json`의 전역 기본값을 따릅니다.
다만 `recursion_termination`처럼 특정 항목만 예외적으로 다른 레벨에서도
필요한 경우, 그 항목에만 `level_overrides`를 얹어서 전역 기본값을 덮어씁니다.

## 청크(build 산출물) 관련 참고 — Chroma 저장 방식

Chroma 벡터 DB의 메타데이터는 배열 타입을 지원하지 않습니다. 그래서:

- `applicable_scale`, `definition` 등은 각각 독립된 청크(`field`로 구분)로 쪼개서 저장합니다.
- 청크 `content`에는 `[category/subcategory]` 또는 `[pitfall_id]` 접두어를 붙여서,
  청크 하나만 단독으로 봐도 어떤 맥락인지 알 수 있게 합니다. (필드 단위로
  쪼개져서 프롬프트에 단독으로 들어갈 때 상위 맥락을 잃는 문제를 방지하기 위함)
- `level_overrides`가 있는 청크는 `override_lv2` / `override_lv3` / `override_lv4`
  boolean 필드로 변환되어 저장됩니다 (배열을 직접 저장할 수 없어서). 이 boolean만
  보면 파싱 없이 바로 쓸 수 있습니다 — 원본(`data_source`)의 `level_overrides`가
  정식 규격이고, 청크의 boolean은 그 저장소 한계에 따른 변환일 뿐입니다.

## 검색 로직 요약 (`scripts/query_test.py`)

1. **category 정확 매칭** (하드 필터): 문제의 `classification.primary[].tag`로 후보를 좁힙니다.
2. **시맨틱 검색**: 남은 후보 중 질문과 임베딩 유사도로 순위를 매깁니다.
3. **레벨 소프트 부스트**: 하드 필터가 아니라, 요청한 힌트 레벨과 관련된 필드의
   청크에 distance 가산점을 주는 방식입니다. 레벨이 안 맞아도 후보에서 완전히
   배제되지는 않습니다 (레벨 스코프를 하드 필터로 뒀을 때 진짜 정답 문서가
   레벨 태그 때문에 통째로 걸러지는 문제가 실제로 발견되어 이렇게 바꿨습니다).

## `keywords`는 임베딩 검색과 다른 갈래입니다

`build/common_pitfalls_keywords.json`은 벡터 검색용이 아니라, 백엔드가
**사용자가 제출한 코드**에 직접 문자열/정규식 매칭을 돌릴 때 쓰는 별도 데이터입니다.
질문과 의미로 매칭하는 나머지 파이프라인과 섞이지 않도록 완전히 분리되어 있습니다.
