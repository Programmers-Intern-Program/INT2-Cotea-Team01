# knowledge_base 작성 규칙 (초안 v0.1)

> 목적: `math` 카테고리를 추가하기 전에, `rag/data_source/knowledge_base/*.json`의 각 필드가 **실제로 어디까지 쓰이는지**를 소스 코드 기준으로 전수 대조했다. 문서(`rag/README.md`, `docs/vector-db-schema.md`)와 실제 구현이 어긋나는 지점이 여러 곳 있어서, 이 문서는 문서가 아니라 **코드(`regenerate_chunks.py`, `KnowledgeBaseRagRetrievalService.java`, `field_level_mapping.json`, 관련 테스트)를 기준**으로 작성했다.
>
> 대조한 파일: `rag/data_source/knowledge_base/bfs.json`, `dp_general.json`, `stack.json`(예시 3건), `rag/scripts/regenerate_chunks.py`, `rag/build/knowledge_base_docs.json`, `rag/config/field_level_mapping.json`, `backend/.../rag/KnowledgeBaseRagRetrievalService.java`, `backend/.../rag/RagChunk.java`, `backend/.../rag/KnowledgeBaseRagRetrievalServiceTest.java`, `backend/.../hint/ProblemContextSelector.java`(태그 추출부).

## 1. 결론부터 — 실제로 살아있는 필드는 4개뿐이다

`data_source`의 문서 하나에는 최대 9개 필드(`doc_id`, `category`, `subcategory`, `hint_level_scope`, `language`, `content.definition`, `content.when_to_use`, `content.java_specific_notes`, `applicable_scale`, `distinguishing_from`, `code_signals`, `often_combined_with` — 사실 12개)가 있지만, **사용자에게 실제로 전달되는(LLM 프롬프트에 들어가는) 값은 `definition`, `when_to_use`, `java_specific_notes`, `applicable_scale` 4개뿐이다.** 나머지는 매칭에 쓰이거나(`category`), 빌드 단계에서 버려지거나(`doc_id`, `hint_level_scope`), 빌드 결과물엔 남지만 조회 코드가 아예 읽지 않는다(`subcategory`, `distinguishing_from`, `code_signals`, `often_combined_with`, `language`).

## 2. 필드별 생애주기 (data_source → build → 런타임 소비)

| 필드 | data_source에 있음 | `regenerate_chunks.py` 빌드에 포함됨 | `KnowledgeBaseRagRetrievalService`가 실제로 읽음 | 상태 |
|---|---|---|---|---|
| `doc_id` | ✅ | ❌ (빌드 시 드롭) | — | **완전 미사용.** `RagChunk.chunkId`는 `doc_id`가 아니라 `category` 문자열을 그대로 씀 |
| `category` | ✅ | ✅ | ✅ (매칭 키) | 살아있음 — 문제의 `classification.primary[].tag`와 정확히 일치해야 매칭 |
| `subcategory` | ✅ | ✅ | ✅ (2026-07-23 이후) | 살아있음 — 문제가 subcategory를 지정하면 일치하는 문서만 좁혀서 매칭 (§3 참고) |
| `hint_level_scope` | ✅ | ❌ (빌드 시 드롭) | — | **완전 미사용.** 레벨 게이팅은 이 필드가 아니라 `field_level_mapping.json`이 필드 단위로 함 |
| `language` | ✅ | ✅ | ❌ (매칭·콘텐츠 조립 어디에도 안 쓰임) | 메타데이터로만 남아있음 |
| `content.definition` | ✅ | ✅ (flatten) | ✅ | 살아있음 — `field_level_mapping.json`의 `related_levels: [2]` |
| `content.when_to_use` | ✅ | ✅ (flatten) | ✅ | 살아있음 — `related_levels: [2, 3]` |
| `content.java_specific_notes` | ✅ | ✅ (flatten) | ✅ | 살아있음 — `related_levels: [3, 4]` |
| `applicable_scale` | ✅ (선택) | ✅ (있으면) | ✅ | 살아있음 — `related_levels: [2, 4]` |
| `distinguishing_from` | ✅ (선택) | ✅ (있으면) | ❌ | **빌드엔 남지만 프롬프트에 전혀 안 실림** (§4 참고) |
| `code_signals` | ✅ (선택) | ✅ (있으면) | ❌ | 위와 동일 |
| `often_combined_with` | ✅ (선택) | ✅ (있으면) | ❌ | 위와 동일 |

## 3. `subcategory` 매칭 — 2026-07-23 구현 완료

> **2026-07-23 완료**: 아래는 원래 "subcategory가 매칭에 전혀 안 쓰인다"는 버그를 기록한 절이었다. `ProblemContextSelector.extractSubcategories()` 추가, `RagRetrievalService.retrieve()`에 `subcategories` 파라미터 추가, `KnowledgeBaseRagRetrievalService`의 매칭 로직 확장으로 이제 실제로 필터링에 쓰인다. 아래 원인 설명은 "왜 이 필드가 필요했는지" 배경으로 남겨두고, 결론만 현재 동작으로 갱신한다.

수정 전 `KnowledgeBaseRagRetrievalService.retrieve()`의 매칭 로직은 category만 봤다:

```java
String category = doc.path("category").asText(null);
if (category == null || !tags.contains(category)) continue;
```

`subcategory`가 있는 카테고리(`dp`, `hash_set`, `greedy`, `simulation`, `trees`, `graph_traversal`, `array`, `string` 등)는 카테고리당 문서가 2~4개씩 있어서(예: `dp` → dp_general/dp_knapsack/dp_path_counting/dp_subsequence), 문제가 `tag: "dp"`로만 분류되면 이 문서들이 전부 매칭돼 한꺼번에 프롬프트에 들어갔다. `rag/README.md`의 "카테고리당 문서가 사실상 1:1" 서술도 이 문제를 근거로 부정확하다고 지적했었다.

**현재 동작**: 문제가 `classification.primary[].subcategory`를 지정하지 않으면(빈 값) 해당 category의 모든 subcategory 문서가 그대로 매칭된다(하위호환). 지정하면 일치하는 subcategory 문서만 좁혀서 매칭된다 — "general" 문서를 자동으로 끼워 넣는 로직은 없고, 필요하면 문제 쪽에서 명시적으로 지정해야 한다. `rag/README.md`의 "1:1 매칭" 서술도 이 동작을 반영해 갱신했다.

## 4. `distinguishing_from` / `code_signals` / `often_combined_with`는 LLM이 못 본다

`buildContent()`는 오직 `field_level_mapping.json`의 `knowledge_base` 키 목록(`definition`, `when_to_use`, `java_specific_notes`, `applicable_scale` 4개)만 순회해서 값을 이어붙인다. 세 필드 다 이 4개 목록에 없으므로, 아무리 잘 써도 최종 프롬프트에 한 글자도 안 들어간다. (이전 세션에서 30개 문서에 이 필드들을 채우는 작업 자체는 완료됐지만, "소비하는 코드"는 아직 없다는 뜻이다 — 데이터 존재와 실제 반영은 별개다.)

**math 작성 시 권장**: 이 3개 필드는 당장 사용자에게 영향을 주지 않으니, 시간이 부족하면 후순위로 두거나 생략해도 기능상 문제는 없다. 다만 나중에 이 필드들을 소비하는 코드가 추가될 걸 대비해 스키마 형태(참조 대상 `ref`가 실제 존재하는 `doc_id`/`category`를 가리키는 것 등)는 맞춰두는 게 안전하다.

## 5. 그래서 `math.json` 작성 시 실제로 신경 써야 할 것 (우선순위 순)

> **2026-07-22 완료**: `rag/data_source/knowledge_base/math.json` 작성, `regenerate_chunks.py` 실행(`knowledge_base_docs.json` 31건으로 갱신), `docs/vector-db-schema.md`·`docs/problem-data-authoring-rules.md`의 통제 어휘 목록 갱신까지 §6 체크리스트 1~4번 완료. subcategory는 §3의 한계를 감안해 `null`(단일 문서)로 시작 — 실제 프로그래머스 수학 문제 분포 조사는 아직 안 함, 필요해지면 재검토.

1. **`category: "math"`** — `classification.primary[].tag`와 정확히 일치해야 매칭된다. `docs/erd.md`가 21개(그중 하나가 math)라고 적어둔 것과 맞추려면, 문제 데이터 쪽에서도 `math`를 tag로 쓰기 전에 이 문서부터 존재해야 한다(순서: knowledge_base 문서 → 문제 데이터에서 tag 사용).
2. **`content.definition` / `when_to_use` / `java_specific_notes`** — 실제로 프롬프트에 실리는 핵심 3필드. Lv2(definition), Lv2~3(when_to_use), Lv3~4(java_specific_notes) 노출 기준에 맞게, "알고리즘 이름을 몰라도 이해되는 정의(definition) → 언제 쓰는지(when_to_use) → 자바 구현 시 흔한 실수(java_specific_notes)" 순서로 난이도가 올라가게 쓴다. (`bfs.json`/`dp_general.json`/`stack.json` 3건 모두 이 패턴을 따르고 있어 그대로 참고하면 된다.)
3. **`applicable_scale`** — "이 접근이 통하는/안 통하는 입력 규모" 기준. math는 다른 카테고리와 달리 "입력 규모"보다 "정수 오버플로우, 부동소수점 오차, 나눗셈 나머지 처리" 같은 수치적 한계가 더 중요할 수 있다 — 이 필드의 의미를 그대로 가져오되, math 특성에 맞게 재해석해서 쓸 것.
4. **`subcategory`** — 이제 §3에 따라 실제로 필터링에 쓰이니, 나눌 경우 문제 데이터 쪽에서 해당 subcategory를 명시해야 그 문서만 좁혀서 매칭된다는 점을 감안해서 설계할 것. `math.json`은 프로그래머스 수학 문제 분포 조사 전이라 우선 `null`(단일 문서)로 시작했고, 필요해지면 재검토한다.
5. **`doc_id`, `hint_level_scope`** — 완전 미사용이라 채워도 기능에 영향 없다. 다만 스키마 일관성(다른 29개 문서와 형태 통일)을 위해 관례대로 채워두는 걸 권장한다 (`doc_id`는 `category`와 동일하게, `hint_level_scope`는 `[2, 3]` 같은 기존 문서들의 패턴을 따름).
6. **`distinguishing_from` / `code_signals` / `often_combined_with`** — §4에 따라 지금은 기능적으로 죽어있는 필드. 시간 여유가 있을 때만.

## 5-1. `code_signals`의 구조적 한계 — 매칭 안 됨은 "안 씀"의 증거가 아니다

`code_signals.keywords`는 사용자 코드에 특정 문자열(`Queue<`, `.poll(` 등)이 있는지로 패턴 사용 여부를 추정한다. 이 방식은 **사용자가 표준 라이브러리 대신 직접 클래스를 구현하면 무력화된다** — 예를 들어 BFS를 직접 만든 큐(배열+인덱스, 또는 커스텀 `MyQueue` 클래스)로 구현하면 `Queue<`/`.poll(`/`.offer(` 어느 것도 매칭되지 않는다.

기존 `keyword_confidence`/`confidence_note`는 **정밀도(오탐) 문제**용이다 — "매칭은 됐지만 확신이 낮다"는 신호다. 커스텀 구현 문제는 **재현율(미탐) 문제**다 — "매칭이 안 됐다고 해서 그 패턴을 안 쓴 건 아니다." 이 둘은 다른 문제라 `keyword_confidence`만으로는 해결되지 않는다. 이후 이 필드를 실제로 소비하는 로직을 설계할 때는 **"매칭 = 약한 긍정 신호, 비매칭 = 판단 불가(부정 신호 아님)"** 원칙을 지켜야 한다.

`bfs.json`이 `keyword_confidence: "high"`인 이유는 "자바에서 BFS는 Queue 계열 없이 구현하는 경우가 드물다"는, 즉 **표준 API 사용이 관례인 카테고리**이기 때문이다. **`math`는 이 전제가 성립하지 않는다** — GCD, 모듈러 연산, 소수 판별 등은 `Math.floorMod()`/`BigInteger` 같은 표준 API 없이 for/while문으로 직접 구현하는 경우가 오히려 흔하다. 그래서 math의 `code_signals`는 구조적으로 `high` confidence를 기대하기 어렵고, 기본값을 `medium` 이하로 잡는 게 더 정직하다. (§4에 따라 지금은 어차피 미소비 필드이므로 당장 급한 작업은 아니다.)

## 6. 신규 카테고리 추가 시 체크리스트 (math뿐 아니라 앞으로 카테고리 추가할 때 공통)

1. `rag/data_source/knowledge_base/{category}.json` 생성 (§5 우선순위대로)
2. `python rag/scripts/regenerate_chunks.py` 실행해서 `rag/build/knowledge_base_docs.json` 갱신 (`rag/README.md`에 명시된 필수 절차)
3. `docs/vector-db-schema.md`의 "20개 통제 어휘" 목록 갱신 (지금 이 문서가 이미 실제와 어긋나 있으므로, math 추가 시점에 한 번 정리 필요)
4. `docs/problem-data-authoring-rules.md`(이전에 만든 문서) §4.1의 통제 어휘 목록에도 `math` 추가
5. 문제 데이터(`rag/problems/*.json`)에서 `classification.primary[].tag: "math"`를 쓰는 건 위 1~4가 끝난 뒤에만 — 순서가 바뀌면 §3처럼 "태그는 있는데 RAG가 조용히 빈 결과를 반환"하는 상황이 재현된다.

## 7. 이번 조사에서 함께 드러난, 이 문서 범위를 벗어나는 별도 이슈

- `subcategory` 필터링은 2026-07-23에 구현 완료됐다(§3). `distinguishing_from`/`code_signals`/`often_combined_with` 미소비(§4)는 여전히 남아있는 별개 이슈로, `math` 작성 규칙과 무관하게 **RAG 검색 품질 자체의 개선 여지**다. 코드 변경이 필요한 사안이라 이 규칙 문서 범위 밖으로 남겨둔다.
- `docs/vector-db-schema.md`가 통제 어휘 개수(20→21) 외에 실제 구현과 어긋나는 부분이 더 있는지는 아직 별도로 점검하지 않았다.
