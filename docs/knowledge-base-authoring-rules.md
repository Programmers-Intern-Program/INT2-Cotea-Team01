# knowledge_base 작성 규칙 (초안 v0.1)

> 목적: `math` 카테고리를 추가하기 전에, `rag/data_source/knowledge_base/*.json`의 각 필드가 **실제로 어디까지 쓰이는지**를 소스 코드 기준으로 전수 대조했다. 문서(`rag/README.md`, `docs/vector-db-schema.md`)와 실제 구현이 어긋나는 지점이 여러 곳 있어서, 이 문서는 문서가 아니라 **코드(`regenerate_chunks.py`, `KnowledgeBaseRagRetrievalService.java`, `field_level_mapping.json`, 관련 테스트)를 기준**으로 작성했다.
>
> 대조한 파일: `rag/data_source/knowledge_base/bfs.json`, `dp_general.json`, `stack.json`(예시 3건), `rag/scripts/regenerate_chunks.py`, `rag/build/knowledge_base_docs.json`, `rag/config/field_level_mapping.json`, `backend/.../rag/KnowledgeBaseRagRetrievalService.java`, `backend/.../rag/RagChunk.java`, `backend/.../rag/KnowledgeBaseRagRetrievalServiceTest.java`, `backend/.../hint/ProblemContextSelector.java`(태그 추출부).

## 1. 결론부터 — 실제로 살아있는 필드는 몇 개인가

> **2026-07-23 갱신**: 아래 "4개뿐"이라는 결론은 이 문서를 처음 쓴 시점 기준이다. 그 뒤 `subcategory`(매칭, §3)와 `often_combined_with`(프롬프트 노출, §4)가 추가로 살아났고, `distinguishing_from`은 런타임이 아니라 문제 데이터 작성 시점의 참고 자료로 쓰기로 결정했다(§4). `code_signals`는 검토 끝에 지금은 쓰지 않기로 명시적으로 결정했다(§5-1).

`data_source`의 문서 하나에는 최대 9개 필드(`doc_id`, `category`, `subcategory`, `hint_level_scope`, `language`, `content.definition`, `content.when_to_use`, `content.java_specific_notes`, `applicable_scale`, `distinguishing_from`, `code_signals`, `often_combined_with` — 사실 12개)가 있다. 사용자에게 실제로 전달되는(LLM 프롬프트에 들어가는) 값은 `definition`, `when_to_use`, `java_specific_notes`, `applicable_scale`, `often_combined_with` 5개다. `category`/`subcategory`는 매칭에 쓰이고, `doc_id`/`hint_level_scope`는 빌드 단계에서 버려지고, `language`는 메타데이터로만 남는다. `distinguishing_from`은 빌드 결과물엔 남지만 런타임 조회 코드는 읽지 않는다 — 대신 문제 데이터(`rag/problems/*.json`) 작성 시점에 `approach.alternativeApproaches` 후보를 고르는 참고 자료로 쓴다(`docs/problem-data-authoring-rules.md` §5 표). `code_signals`는 빌드 결과물엔 남지만 지금은 어떤 코드도 읽지 않기로 결정했다(§5-1).

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
| `distinguishing_from` | ✅ (선택) | ✅ (있으면) | ❌ (의도적) | **런타임엔 안 실림 — 대신 문제 데이터 작성 시 `alternativeApproaches` 후보 참고용** (§4 참고) |
| `code_signals` | ✅ (선택) | ✅ (있으면) | ❌ (의도적) | **지금은 쓰지 않기로 결정** — 데이터는 유지, 소비 코드 없음 (§5-1 참고) |
| `often_combined_with` | ✅ (선택) | ✅ (있으면) | ✅ (2026-07-23 이후) | 살아있음 — `related_levels: [3]`, `KnowledgeBaseRagRetrievalService`가 `[{ref,note}]`를 사람이 읽을 문장으로 렌더링 |

## 3. `subcategory` 매칭 — 2026-07-23 구현 완료

> **2026-07-23 완료**: 아래는 원래 "subcategory가 매칭에 전혀 안 쓰인다"는 버그를 기록한 절이었다. `ProblemContextSelector.extractSubcategories()` 추가, `RagRetrievalService.retrieve()`에 `subcategories` 파라미터 추가, `KnowledgeBaseRagRetrievalService`의 매칭 로직 확장으로 이제 실제로 필터링에 쓰인다. 아래 원인 설명은 "왜 이 필드가 필요했는지" 배경으로 남겨두고, 결론만 현재 동작으로 갱신한다.

수정 전 `KnowledgeBaseRagRetrievalService.retrieve()`의 매칭 로직은 category만 봤다:

```java
String category = doc.path("category").asText(null);
if (category == null || !tags.contains(category)) continue;
```

`subcategory`가 있는 카테고리(`dp`, `hash_set`, `greedy`, `simulation`, `trees`, `graph_traversal`, `array`, `string` 등)는 카테고리당 문서가 2~4개씩 있어서(예: `dp` → dp_general/dp_knapsack/dp_path_counting/dp_subsequence), 문제가 `tag: "dp"`로만 분류되면 이 문서들이 전부 매칭돼 한꺼번에 프롬프트에 들어갔다. `rag/README.md`의 "카테고리당 문서가 사실상 1:1" 서술도 이 문제를 근거로 부정확하다고 지적했었다.

**현재 동작**: 문제가 `classification.primary[].subcategory`를 지정하지 않으면(빈 값) 해당 category의 모든 subcategory 문서가 그대로 매칭된다(하위호환). 지정하면 일치하는 subcategory 문서만 좁혀서 매칭된다 — "general" 문서를 자동으로 끼워 넣는 로직은 없고, 필요하면 문제 쪽에서 명시적으로 지정해야 한다. `rag/README.md`의 "1:1 매칭" 서술도 이 동작을 반영해 갱신했다.

## 4. `distinguishing_from` / `code_signals` / `often_combined_with` — 2026-07-23 결정 사항

> 이 절은 원래 "세 필드 다 LLM이 못 본다"는 버그를 기록했었다. 검토 끝에 세 필드를 각각 다르게 처리하기로 결정했다. 결론만 아래로 갱신하고, 판단 과정은 §5-1(code_signals)에 남겨둔다.

**`often_combined_with` — 런타임 소비로 전환.** `KnowledgeBaseRagRetrievalService.buildContent()`가 `field_level_mapping.json`의 `knowledge_base` 키 목록을 순회하는 건 여전하지만, 이제 `often_combined_with`도 그 목록에 있고(`related_levels: [3]`), 배열이라 `asText()`로 못 읽는 문제는 전용 렌더러(`renderOftenCombinedWith()`)로 해결했다 — `[{ref, note}]`를 `"함께 자주 쓰이는 기법: ref1(note1), ref2(note2)"` 형태의 문장으로 직렬화해서 다른 필드들과 같은 방식으로 이어붙인다. Lv3("구현 힌트")에서만 노출된다.

**`distinguishing_from` — 런타임엔 안 씀, 문제 데이터 작성 시점의 참고 자료로 전환.** 이 필드를 그대로 프롬프트에 얹는 방안을 검토했으나, Lv2 정책의 "사용자가 비교를 요청한 경우에만 대안 접근을 짧게 언급"이라는 조건을 강제할 장치가 없어 정책 위반(요청 안 했는데 비교를 먼저 꺼내는 것) 위험이 있다고 판단해 보류했다. 대신 `docs/problem-data-authoring-rules.md`의 `approach.alternativeApproaches`(이미 존재하고 이미 Lv2에 이름만 노출되는 안전한 필드) 작성 규칙에 "해당 category/subcategory KB 문서의 `distinguishing_from[].ref`를 후보로 참고할 것"이라는 지침을 추가했다. `signal`(비교 설명문)은 작성자가 후보를 판단하는 근거로만 쓰이고, 런타임에 어떤 형태로도 노출되지 않는다.

**`code_signals` — 지금은 쓰지 않기로 결정.** §5-1 참고.

## 5. 그래서 `math.json` 작성 시 실제로 신경 써야 할 것 (우선순위 순)

> **2026-07-22 완료**: `rag/data_source/knowledge_base/math.json` 작성, `regenerate_chunks.py` 실행(`knowledge_base_docs.json` 31건으로 갱신), `docs/vector-db-schema.md`·`docs/problem-data-authoring-rules.md`의 통제 어휘 목록 갱신까지 §6 체크리스트 1~4번 완료. subcategory는 §3의 한계를 감안해 `null`(단일 문서)로 시작 — 실제 프로그래머스 수학 문제 분포 조사는 아직 안 함, 필요해지면 재검토.

1. **`category: "math"`** — `classification.primary[].tag`와 정확히 일치해야 매칭된다. `docs/erd.md`가 21개(그중 하나가 math)라고 적어둔 것과 맞추려면, 문제 데이터 쪽에서도 `math`를 tag로 쓰기 전에 이 문서부터 존재해야 한다(순서: knowledge_base 문서 → 문제 데이터에서 tag 사용).
2. **`content.definition` / `when_to_use` / `java_specific_notes`** — 실제로 프롬프트에 실리는 핵심 3필드. Lv2(definition), Lv2~3(when_to_use), Lv3~4(java_specific_notes) 노출 기준에 맞게, "알고리즘 이름을 몰라도 이해되는 정의(definition) → 언제 쓰는지(when_to_use) → 자바 구현 시 흔한 실수(java_specific_notes)" 순서로 난이도가 올라가게 쓴다. (`bfs.json`/`dp_general.json`/`stack.json` 3건 모두 이 패턴을 따르고 있어 그대로 참고하면 된다.)
3. **`applicable_scale`** — "이 접근이 통하는/안 통하는 입력 규모" 기준. math는 다른 카테고리와 달리 "입력 규모"보다 "정수 오버플로우, 부동소수점 오차, 나눗셈 나머지 처리" 같은 수치적 한계가 더 중요할 수 있다 — 이 필드의 의미를 그대로 가져오되, math 특성에 맞게 재해석해서 쓸 것.
4. **`subcategory`** — 이제 §3에 따라 실제로 필터링에 쓰이니, 나눌 경우 문제 데이터 쪽에서 해당 subcategory를 명시해야 그 문서만 좁혀서 매칭된다는 점을 감안해서 설계할 것. `math.json`은 프로그래머스 수학 문제 분포 조사 전이라 우선 `null`(단일 문서)로 시작했고, 필요해지면 재검토한다.
5. **`doc_id`, `hint_level_scope`** — 완전 미사용이라 채워도 기능에 영향 없다. 다만 스키마 일관성(다른 29개 문서와 형태 통일)을 위해 관례대로 채워두는 걸 권장한다 (`doc_id`는 `category`와 동일하게, `hint_level_scope`는 `[2, 3]` 같은 기존 문서들의 패턴을 따름).
6. **`often_combined_with`** — §4에 따라 이제 Lv3에 실제로 노출된다. math와 자주 결합되는 게 있다면(예: bruteforcing, dp) 채워둘 것.
7. **`distinguishing_from`** — 런타임엔 안 쓰이지만, `math` 문제 데이터를 작성할 때 `approach.alternativeApproaches` 후보를 고르는 참고 자료로 쓰인다(§4). 채워두면 나중에 math 문제를 만들 때 유용하다.
8. **`code_signals`** — §5-1에 따라 지금은 쓰지 않기로 결정된 필드. 시간 여유가 있을 때만.

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

- `subcategory` 필터링(§3)과 `often_combined_with` 프롬프트 노출(§4)은 2026-07-23에 구현 완료됐다. `distinguishing_from`은 런타임 대신 문제 데이터 작성 시 참고 자료로 쓰기로 결정했다(§4, `docs/problem-data-authoring-rules.md` 반영 완료). `code_signals`는 §5-1의 한계를 근거로 지금은 쓰지 않기로 결정했다 — 데이터는 유지하되 소비 코드는 만들지 않는다.
- `docs/vector-db-schema.md`가 통제 어휘 개수(20→21) 외에 실제 구현과 어긋나는 부분이 더 있는지는 아직 별도로 점검하지 않았다.
