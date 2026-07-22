# 문제 데이터 자동 생성 규칙 (초안 v0.1)

> 목적: `rag/problems/*.json`을 자동/반자동으로 생성하는 파이프라인을 만들기 전에, 기존 데이터(1829.json)·정책 파일(`prompt-policy.json`, `field_level_mapping.json`)·RAG 문서(`knowledge_base`)·ERD(`docs/erd.md`)를 전수 조사해서 도출한 규칙이다. **코드는 없다. 다음 단계(파이프라인 설계·구현)의 입력이 되는 규칙 문서다.**
>
> 조사 대상: `rag/problems/1829.json`, `rag/config/field_level_mapping.json`, `backend/src/main/resources/config/prompt-policy.json`, `rag/README.md`, `docs/erd.md`, `docs/vector-db-schema.md`, `rag/build/knowledge_base_docs.json`, `backend/.../ProblemContextSelector.java`, `backend/.../ProblemMetaMapper.java`.

## 1. 왜 규칙이 먼저인가

문제 메타데이터는 단순 문서가 아니라 **레벨별 노출 정책(`prompt-policy.json`)이 그대로 소비하는 입력**이다. 필드 하나가 어느 힌트 레벨에서 그대로 프롬프트에 실리는지가 이미 정해져 있어서, 생성 파이프라인이 이걸 모르고 콘텐츠를 채우면 특정 레벨에서 "정답을 미리 알려주는" 사고가 난다. 예: `approach.keyInsight`는 Lv1에서 노출되는데, 이 필드에 알고리즘 이름을 쓰면 Lv1의 `forbid`("알고리즘명 직접 공개")를 자동으로 어기게 된다. 그래서 필드 작성 규칙은 반드시 "이 필드가 어느 레벨/단계에서 쓰이는가"와 짝을 이뤄야 한다.

## 2. 파일 위치·기본 규칙

- 경로: `rag/problems/{problemId}.json` (problemId는 프로그래머스 lesson URL의 숫자 ID, `source.url`과 반드시 일치)
- `rag/problems/`는 `.gitignore` 대상(저작권 문제로 비공개 관리) — 자동 생성 결과물도 이 규칙을 따른다.
- `language`는 현재 `"java"` 고정 (다른 언어 지원 시 스키마 자체를 재설계해야 함 — `docs/erd.md`에 명시된 기존 결정).
- `metadataVersion`은 문제 스키마 버전 문자열(예: `"1.1.0"`). `prompt-policy.json`의 `schemaVersion`과는 별개 값이므로 혼동하지 말 것.
- `reviewedBy`는 사람 리뷰 전이면 `null`. 자동 생성 파이프라인의 산출물은 기본적으로 `reviewedBy: null`로 두고, 사람이 검수하면 그때 값을 채우는 흐름을 전제로 한다.

## 3. 최상위 스키마

```
problemId              int       필수 — source.url과 일치
metadataVersion        string    필수
reviewedBy             string|null
lastUpdated            string    필수 (YYYY-MM-DD)
source.platform        string    필수, 현재 "programmers" 고정
source.title           string    필수
source.level           string    필수, "Lv{n}" 형식 (예: "Lv2")
source.url             string    필수
source.language        string    필수, "java" 고정
classification.primary[].tag          string  필수 — §4 통제 어휘
classification.primary[].subcategory  array   선택
classification.difficultyReason       string  필수 — Lv1 노출, §5 제약 적용
approach.recommendedApproach          string  필수 — Lv2/Lv3/Lv4 노출
approach.alternativeApproaches        array<string>  선택 — Lv2 노출
approach.expectedTimeComplexity       string  필수
approach.expectedSpaceComplexity      string  필수
approach.complexityVariables          object  선택 (변수명 → 설명)
approach.keyInsight                   string  필수 — Lv1 노출, §5 제약 적용
solvingSupport.keyDataStructures         array<string>  필수 — Lv3 노출
solvingSupport.implementationCheckpoints array<string>  필수 — Lv3/Lv4 노출
solvingSupport.stuckPointHints           object (point_key → hint)  필수 — Lv3/Lv4 노출
wrongAnswerDiagnosis.commonMistakes[].symptom        string  필수 — §4 통제 어휘(정확히 3종)
wrongAnswerDiagnosis.commonMistakes[].likelyCause     string  필수
wrongAnswerDiagnosis.commonMistakes[].directionHint   string  필수
wrongAnswerDiagnosis.fatalApproachSignals             array<string>  선택 — Lv4 노출
edgeCases               array<string>   선택 — §6 참고(현재 프롬프트 조립에서 미사용)
afterSolve.evaluationCriteria   array<string>  선택 — §6 참고(현재 기능 비활성)
afterSolve.optimizationHints    array<string>  선택 — §6 참고(현재 기능 비활성)
afterSolve.similarProblems      array<string>  선택 — 현재 정책상 항상 빈 배열
```

DB(MySQL 등) 저장 시 `docs/erd.md`의 12개 테이블(PROBLEM, PROBLEM_CLASSIFICATION, APPROACH_ALTERNATIVE, COMPLEXITY_VARIABLE, KEY_DATA_STRUCTURE, SOLVING_CHECKPOINT, STUCK_POINT_HINT, WRONG_ANSWER_MISTAKE, FATAL_APPROACH_SIGNAL, EDGE_CASE, EVALUATION_CRITERIA, OPTIMIZATION_HINT, SIMILAR_PROBLEM)와 1:1로 대응된다. 파일 스키마와 DB 스키마가 어긋나면 `ProblemMetaMapper.toJson()`이 깨지므로, 생성 파이프라인은 이 문서의 필드명(camelCase, JSON 기준)을 그대로 따라야 한다.

## 4. 통제 어휘 (반드시 지켜야 함 — 틀리면 조용히 기능이 죽는다)

### 4.1 `classification.primary[].tag`

`rag/build/knowledge_base_docs.json`에 실제로 존재하는 카테고리 21개만 유효하다:

```
array, backtracking, bfs, binary_search, bruteforcing, dfs, dp, graph_traversal,
greedy, hash_set, math, prefix_sum, priority_queue, queue_deque, simulation,
sliding_window, sorting, stack, string, trees, two_pointer
```

(2026-07-22: `math` 추가 완료 — `docs/knowledge-base-authoring-rules.md` 참고. 이전에 여기 적혀있던 "ERD는 21개인데 실제론 20개" 불일치는 해소됨.)

RAG 조회(`KnowledgeBaseRagRetrievalService`)는 태그 정확 매칭이라, 이 목록에 없는 카테고리를 tag로 쓰면 에러 없이 그냥 RAG 검색 결과가 0건이 되어(silent degradation) 힌트 품질만 조용히 떨어진다. 새 카테고리가 필요하면 문제 데이터 생성보다 먼저 `knowledge_base`에 문서를 추가하는 게 선행 작업이다.

`subcategory`가 있는 카테고리는 8개뿐이다: `dp`(dp_general/dp_knapsack/dp_path_counting/dp_subsequence), `hash_set`(hash_set_general/hash_set_frequency), `greedy`(greedy_general/greedy_sort_based), `simulation`(simulation_general/simulation_grid), `trees`(trees_general/trees_bst), `graph_traversal`(graph_general/graph_connectivity), `array`(array_general/array_sorted_pattern), `string`(string_general/string_pattern_basic). 나머지 12개 카테고리는 subcategory 없이 단일 문서다.

### 4.2 `wrongAnswerDiagnosis.commonMistakes[].symptom`

정확히 이 3개 문자열 중 하나여야 한다: `"오답"`, `"시간초과"`, `"런타임에러"`.

이유: `ProblemContextSelector.filterCommonMistakesBySubmissionResult()`가 `submissionResult`(WRONG_ANSWER/TIME_LIMIT_EXCEEDED/RUNTIME_ERROR)를 이 3개 문자열로 매핑해서 정확히 문자열이 일치하는 항목만 골라낸다. 오타(`"오답이요"`, `"시간 초과"` 등)가 나면 그 항목은 어떤 submissionResult가 와도 절대 선택되지 않는다 — 에러가 안 나서 알아채기 어렵다.

## 5. 레벨별 콘텐츠 안전 규칙

아래는 `prompt-policy.json`의 `hintLevelPolicy`를 필드 작성 시점 기준으로 재정리한 것이다. **필드 내용 자체가 해당 레벨의 `forbid`를 어기면 안 된다.**

| 필드 | 노출 레벨/단계 | 이 필드를 쓸 때 지킬 것 |
|---|---|---|
| `approach.keyInsight` | Lv1 | 알고리즘/자료구조 이름 금지, 구현 순서·의사코드·코드 금지. "관찰"이나 "다시 보게 하는 질문" 톤으로만 — 예: "값이 같아도 떨어져 있으면 다른 영역"처럼 관점 제시 |
| `classification.difficultyReason` | Lv1 | 위와 동일. 왜 어려운지 "구조적으로" 설명하되 풀이법을 암시하지 않기 |
| `classification.primary` (tag/subcategory) | Lv2 | 여기서는 알고리즘명 노출이 **의도된 동작**이다. tag 값 자체가 태그를 그대로 읽어주는 데 쓰이므로 §4.1 통제 어휘를 반드시 지킬 것 |
| `approach.recommendedApproach` | Lv2/Lv3/Lv4 | Lv2에서는 "왜 이 접근인지"까지만, 구체적 구현 순서·의사코드는 넣지 말 것(Lv2 forbid). 텍스트 자체는 레벨 구분 없이 하나뿐이므로, **가장 낮은 레벨(Lv2)의 제약을 기준으로 작성**해야 안전하다 |
| `approach.alternativeApproaches` | Lv2 | 이름만 나열, 각각을 길게 비교 설명하지 않기 |
| `solvingSupport.implementationCheckpoints` | Lv3/Lv4 | 체크리스트 형태(3~5단계)는 허용되지만 "핵심 로직을 모두 채운" 수준이면 안 됨 |
| `solvingSupport.stuckPointHints` | Lv3/Lv4 | 막히는 지점에 대한 "방향 제시"까지, 정답 코드 금지 |
| `solvingSupport.keyDataStructures` | Lv3 | 자료구조 이름/타입까지만 (예: `Queue<int[]>`), 실제 사용 코드는 넣지 않기 |
| `wrongAnswerDiagnosis.commonMistakes[].directionHint` | Lv4 + WRONG_ANSWER 이유질문 | "이 부분을 확인해보세요" 수준까지, 수정된 코드 제시 금지 |
| `wrongAnswerDiagnosis.fatalApproachSignals` | Lv4 + WRONG_ANSWER 이유질문 | "느린 접근"이 아니라 "구조적으로 답이 안 나오는 접근"만 해당 — 단순 비효율은 `commonMistakes`(시간초과)로 분류할 것 |

전체 공통 금지(모든 필드에 적용, `doNotReveal` 그대로): 전체 정답 코드, 복사해서 제출 가능한 구현, 정확한 수식 한 줄 해법, main/solution 함수를 통째로 채운 코드, 변수명·루프 구조까지 포함한 완성에 가까운 의사코드.

## 6. 현재 파이프라인이 소비하지 않는 필드 (생성은 하되 우선순위 낮음)

다음 필드는 `prompt-policy.json`의 어떤 `usesProblemFields`에도 등장하지 않는다 — 즉 지금 시점엔 실제 힌트 생성에 전혀 쓰이지 않는다:

- `edgeCases` — 어느 레벨/단계에도 연결 안 됨. 향후 기능(테스트케이스 생성, 사람 리뷰 참고용 등) 대비 자산으로만 생성.
- `afterSolve.*` (evaluationCriteria/optimizationHints/similarProblems) — `phasePolicy.AFTER_SOLVE.enabled`가 `false`라 이 단계 자체가 꺼져 있음. `similarProblems`는 ERD 문서에 "현재 방침상 항상 빈 배열"이라고 명시돼 있으므로, 생성 파이프라인도 빈 배열로 채우면 충분하다.

생성 파이프라인이 리소스(LLM 호출 등)를 아껴야 한다면, 이 필드들은 **정확도를 덜 신경 써도 되는 영역**으로 후순위에 둘 수 있다. 다만 스키마 자체는 채워야 `ProblemMetaMapper`/DB 매핑이 깨지지 않는다.

## 7. RAG 연동 시 필수 확인 사항

- `classification.primary[].tag`가 `knowledge_base_docs.json`에 실제 존재하는 카테고리와 정확히 일치해야 `KnowledgeBaseRagRetrievalService`가 문서를 찾는다. 새 카테고리가 필요하면 문제 데이터보다 `knowledge_base` 문서 추가가 선행돼야 한다(§4.1).
- `common_pitfalls`는 카테고리 무관(cross-category)이라 문제별로 신경 쓸 필요 없음 — 다만 이 컬렉션 자체가 아직 백엔드에 연결 안 된 상태(`docs/vector-db-schema.md`의 TBD #2)라, 지금은 문제 데이터 쪽에서 할 일이 없다.

## 8. 기계적 검증 체크리스트 (생성 후 자동 검사용)

1. **JSON 스키마 검증**: §3의 필수 필드 전부 존재하는지.
2. **problemId 일치**: `problemId` == `source.url`에서 파싱한 숫자.
3. **tag 통제 어휘**: `classification.primary[].tag` ⊆ §4.1의 20개 목록.
4. **symptom 통제 어휘**: `commonMistakes[].symptom` ⊆ {"오답","시간초과","런타임에러"} (정확한 문자열 일치, 공백/조사 변형 금지).
5. **Lv1 금지어 린트**: `approach.keyInsight`, `classification.difficultyReason`에 `HintAnswerGuardrail.LEVEL_1_FORBIDDEN_TERMS`(BFS/DFS/DP/Union-Find/유니온 파인드/큐/스택/visited/방문 배열)가 `KoreanBoundaryMatcher.containsAsStandaloneTerm` 기준으로 안 걸리는지 — **이미 있는 가드레일 로직을 그대로 재사용할 수 있다.**
6. **코드 유출 휴리스틱**: 어떤 필드든 `class Solution`, `public int[] solution` 같은 완전한 메서드 시그니처 + 중괄호 블록이 통째로 들어있으면 반려(완성 코드 유출 의심).
7. **`afterSolve.similarProblems`**: 항상 빈 배열인지(현재 정책).

## 9. 열린 질문 (파이프라인 설계 단계에서 결정 필요)

- 생성 소스: 프로그래머스 문제 본문을 어디서 가져올지(크롤링/수동 입력/API)는 이 문서 범위 밖.
- `approach.recommendedApproach`가 Lv2~Lv4에서 재사용되는데, 레벨별로 톤을 분리하고 싶다면 지금의 단일 문자열 스키마로는 부족할 수 있음 — 스키마 확장 여부는 별도 논의 필요.
- 생성된 데이터의 사람 검수 절차(`reviewedBy` 채우는 시점/주체)는 미정.
- `math` 카테고리를 실제로 쓸 계획이 있다면 `knowledge_base` 문서 작성이 선행 과제로 별도 티켓화 필요.
