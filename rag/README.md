# 코티(Cotea) RAG 데이터

## 폴더 구조

```
/rag
├── data_source/           # 원본 (사람이 직접 편집하는 곳)
│   ├── knowledge_base/    # 30개 — 알고리즘 카테고리별 개념 문서
│   └── common_pitfalls/   # 12개 — 카테고리 무관 자바 실수 패턴
├── build/                 # 생성된 결과물 (data_source로부터 재생성 가능, 직접 수정 금지)
│   ├── knowledge_base_docs.json    # 문서 30건 (청크 분할 없음)
│   └── common_pitfalls_keywords.json
├── config/
│   ├── field_level_mapping.json   # 필드(용도) -> 힌트 레벨 매핑 (레벨 체계 변경 시 여기만 수정)
│   └── prompt-policy.json         # 공통 튜터 정책 (backend/resources/config 와 동기화)
├── scripts/
│   └── regenerate_chunks.py       # data_source -> build 변환
└── problems/ (gitignore)          # 문제별 메타데이터, 저작권 문제로 비공개 관리
```

**data_source를 고쳤다면 반드시 `scripts/regenerate_chunks.py`를 다시 실행해서 `build/`를 갱신해야 합니다.**

**2026-07-14 결정: 벡터DB(Chroma)는 쓰지 않습니다.** `knowledge_base`가 30개 문서·20개 카테고리로 규모가 작아 시맨틱 유사도 검색의 이득보다 임베딩 API·Chroma 운영 부담이 더 크다고 판단했습니다. 이전에 있던 `build_index.py`(Chroma 인덱싱), `query_test.py`(Chroma 검색 테스트), `common_pitfalls_chunks.json`(임베딩용 청크)는 전부 삭제했습니다. 대신 category(+subcategory) 정확 매칭 + 힌트 레벨 하드 필터 방식을 씁니다 (아래 "검색 로직 요약" 참고).

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
  "source": {...},
  "distinguishing_from": [   // 선택 — 혼동하기 쉬운 카테고리와의 구분 신호
    { "ref": "다른 doc_id", "signal": "이 조건이면 그쪽이 아니라 이쪽이라는 판별 문장" }
  ],
  "code_signals": {          // 선택 — common_pitfalls와 동일한 구조, 사용자 코드 키워드 매칭용
    "keywords": ["..."],
    "exclusion_keywords": ["..."],
    "keyword_confidence": "high | medium | low",
    "confidence_note": "..."
  },
  "often_combined_with": [   // 선택 — 배타적 구분이 아니라 실제로 함께 쓰이는 카테고리
    { "ref": "다른 doc_id", "note": "어떤 맥락에서 결합되는지" }
  ]
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

## build 산출물 관련 참고 — 태그 정확 매칭 방식

`knowledge_base_docs.json`은 문서 하나 = 결과 하나로, 청크 분할이나 `[category/subcategory]` 접두어가 없습니다. 청크를 쪼개거나 접두어를 붙이던 이유(Chroma 메타데이터가 배열을 지원하지 않는 제약)가 벡터DB를 쓰지 않기로 하면서 사라졌기 때문입니다. `distinguishing_from`/`code_signals`/`often_combined_with` 같은 배열 필드도 원본 그대로 저장됩니다.

`common_pitfalls`의 `level_overrides`(배열)도 마찬가지로 원본 형태 그대로 다루는 쪽으로 갈 수 있지만, 현재 `common_pitfalls_keywords.json` 생성 로직 자체는 이번 결정과 무관하게 유지했습니다 (키워드 매칭은 원래부터 Chroma와 별개였습니다).

## 검색 로직 요약

1. **category(+subcategory) 정확 매칭** (하드 필터): 문제의 `classification.primary[].tag`로 `knowledge_base_docs.json`에서 문서를 조회합니다. `subcategory`가 없는 카테고리는 태그만으로 문서가 하나로 정해지지만, `subcategory`가 여러 개로 나뉜 카테고리(예: `dp`)는 문제가 `classification.primary[].subcategory`를 지정하지 않으면 그 카테고리의 문서 전부가 매칭되고, 지정하면 일치하는 subcategory 문서만 좁혀서 매칭됩니다.
2. **힌트 레벨 하드 필터**: `config/field_level_mapping.json`의 매핑을 기준으로, 현재 힌트 레벨과 관련 없는 필드는 애초에 프롬프트에 포함하지 않습니다. 문제 메타데이터(A) 조회에 쓰는 `ProblemContextSelector`와 동일한 "정책 파일이 필드 노출을 정하고 코드는 조립만" 패턴입니다.

시맨틱 유사도 랭킹 단계는 없습니다 — 벡터DB를 쓰지 않기로 하면서 "검색 랭킹"이라는 개념 자체가 없어졌습니다. 실제 조회 코드(`RagRetrievalService` 구현체)는 아직 작성 전입니다.

## `keywords`는 임베딩 검색과 다른 갈래입니다

`build/common_pitfalls_keywords.json`은 벡터 검색용이 아니라, 백엔드가
**사용자가 제출한 코드**에 직접 문자열/정규식 매칭을 돌릴 때 쓰는 별도 데이터입니다.
질문과 의미로 매칭하는 나머지 파이프라인과 섞이지 않도록 완전히 분리되어 있습니다.

## 힌트 API 테스트

힌트 생성(`/api/hint`)은 **Spring Boot 백엔드**에서 처리합니다.  
실행·테스트 방법은 [`../backend/backend-readme.md`](../backend/backend-readme.md)를 참고하세요.

요청 예시 JSON: `backend/examples/hint_request_1829_*.json`
