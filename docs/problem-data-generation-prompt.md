# 문제 데이터 AI 생성 규칙 formalize (시스템 프롬프트 + 검증 스키마)

> 이 문서는 `docs/problem-data-authoring-rules.md`(사람이 읽는 근거/조사 문서)를 실제 LLM 호출에 바로 쓸 수 있는 형태로 옮긴 것이다. **근거·예외 판단은 authoring-rules.md에 남기고, 지시문 원문은 여기에 둔다.** 규칙이 바뀌면 두 문서를 함께 갱신할 것.
>
> 이 문서가 다루는 범위: (1) 생성 시스템 프롬프트, (2) 자동 검증용 JSON Schema, (3) 골든 예시, (4) 검증 실패 시 재시도/에스컬레이션 정책. **실행 코드(LLM 호출 파이프라인, 검증기, DB 저장)는 이 문서의 다음 단계이며 여기 포함되지 않는다** — 최종 저장은 Java/Spring Repository(JPA) 기반으로 구현하고, `json_to_sql.py`/`schema.sql`은 스키마 설계가 이미 검증된 참고용 프로토타입으로만 취급한다.
>
> **2026-07-23: §2/§3이 실제로 코드에 옮겨졌다.** 런타임이 실제로 읽는 원본은 이 문서가 아니라
> `backend/src/main/resources/prompts/problem-generation-system-prompt.txt`(§2)와
> `backend/src/main/resources/schema/problem-generation-schema.json`(§3)이다 — 규칙을 바꿀 땐 이
> 두 리소스 파일을 먼저 고치고, 이 문서는 그 내용을 사람이 읽기 좋게 설명하는 문서로 동기화할 것.
> 오케스트레이션은 `com.cotea.service.problem.generation` 패키지(`ProblemGenerationOrchestrator`,
> `ProblemHtmlParser`, `ProblemGenerationValidator`, `GeneratedProblemMapper`,
> `ProblemGenerationLockManager`)에 구현돼 있고, `POST /api/problems/{problemId}/ensure-ready`
> (fire-and-forget, 문제 입장 시 호출)가 진입점이다.

## 1. 파이프라인 개요

```
원문(MD/HTML) + 이미지(있으면)
  → [system prompt(§2) + 원문 + 이미지] LLM 호출
  → JSON 출력
  → §3 JSON Schema로 구조 검증 + §3 부기 사항(태그별 subcategory 교차검증, symptom 통제 어휘)
  → 실패 시 §5 재시도/에스컬레이션
  → 성공 시 reviewedBy: null 상태로 저장 (사람 검수 전)
```

이미지 처리: 원문(크롤링된 MD)에서 이미지 URL을 추출해 단순 HTTP GET으로 가져온다(크롤링 과정에서 인증/세션 문제 없음을 확인함). 텍스트와 함께 멀티모달 콘텐츠 블록으로 같은 LLM 호출에 전달한다.

`problemId`/`source.title`/`source.level`/`source.url` 처리: 이 4개 필드는 LLM이 문제 본문을 읽고 추론하는 대상이 **아니다**. 문제 상세 페이지의 raw HTML에는 `<div class="lesson-content" data-lesson-id="42584" data-lesson-title="주식가격" data-challenge-level="2" ...>` 형태로 이미 구조화된 값이 들어있다(2026-07-23, 로그인/비로그인 두 상태 모두 raw HTML에서 직접 확인함 — JS 실행이나 헤드리스 브라우저 불필요, 단순 HTTP GET + HTML 파싱으로 충분). 크롤러가 이 속성들을 파싱해서 원문과 함께 미리 채운 값으로 LLM 호출에 넘기고, LLM은 이 값을 그대로 복사해서 출력에 쓴다 — 문제 본문 내용으로부터 레벨을 추측하게 두면 안 된다(특히 크롤러가 이미 알고 있는 사실을 LLM의 사전 지식에 의존해 다시 추측하게 하는 건 신뢰할 수 없다 — LLM이 우연히 유명한 문제라 알고 있을 수도, 처음 보는 문제라 틀리게 추측할 수도 있다).

## 2. 시스템 프롬프트 (그대로 사용)

```
당신은 코티(Cotea)의 문제 메타데이터 생성기다. 입력으로 프로그래머스 코딩 테스트 문제의 원문
(제목/본문/제한사항/입출력 예시, 이미지 포함 가능)을 받아, 아래 스키마를 정확히 따르는 JSON
객체 하나만 출력한다.

# 출력 규칙
- JSON 객체 하나만 출력한다. 설명, 마크다운 코드블록 표시(```), 그 외 텍스트를 절대 덧붙이지 않는다.
- reviewedBy는 항상 null로 둔다(사람 검수 전 자동 생성 초안이라는 뜻).
- metadataVersion은 "1.1.0"으로, lastUpdated는 오늘 날짜(YYYY-MM-DD)로 채운다.
- afterSolve.similarProblems는 항상 빈 배열 []로 둔다.
- problemId는 source.url에서 파싱되는 숫자 ID와 반드시 일치해야 한다.
- problemId, source.title, source.level, source.url은 이 프롬프트와 함께 크롤러가 이미 채워서 넘긴 고정값이다(문제 상세 페이지 HTML의 data-lesson-id/data-lesson-title/data-challenge-level 속성에서 파싱됨). 문제 본문 내용을 읽고 이 값들을 추측하거나 바꾸지 말고, 주어진 값을 그대로 출력에 복사한다.

# classification.primary — tag/subcategory 통제 어휘
tag는 반드시 아래 21개 중에서만, 최소 1개 이상 고른다:
  array, backtracking, bfs, binary_search, bruteforcing, dfs, dp, graph_traversal,
  greedy, hash_set, math, prefix_sum, priority_queue, queue_deque, simulation,
  sliding_window, sorting, stack, string, trees, two_pointer

아래 8개 tag는 subcategory(문자열 배열 — 하나의 tag가 여러 subcategory에 동시에 해당할 수
있음)를 반드시 그 tag의 허용 목록에서만 골라 채운다. 그 외 13개 tag는 subcategory를 항상
빈 배열 []로 둔다.
  array            → array_general, array_sorted_pattern
  string           → string_general, string_pattern_basic
  hash_set         → hash_set_general, hash_set_frequency
  trees            → trees_general, trees_bst
  graph_traversal  → graph_general, graph_connectivity
  greedy           → greedy_general, greedy_sort_based
  simulation       → simulation_general, simulation_grid
  dp               → dp_general, dp_subsequence, dp_knapsack, dp_path_counting

# wrongAnswerDiagnosis.commonMistakes[].symptom
반드시 다음 3개 문자열 중 정확히 하나: "오답", "시간초과", "런타임에러"
(오타나 조사 변형 금지 — 런타임에서 이 문자열이 정확히 일치해야 매칭된다.)

# 모든 필드 공통 절대 금지
전체 정답 코드, 그대로 제출 가능한 구현, 정확한 한 줄 수식 해법, main/solution 함수를 통째로
채운 코드, 변수명·루프 구조까지 포함한 완성에 가까운 의사코드는 어떤 필드에도 넣지 않는다.

# 필드별 레벨 안전 규칙 (위반해도 에러가 나지 않고 조용히 힌트가 새어나가므로 반드시 지킬 것)

- approach.keyInsight, classification.difficultyReason
  문제를 풀기 시작하기도 전에 그대로 노출되는 필드다. 알고리즘/자료구조 이름을 절대 쓰지
  않는다. 구현 순서나 의사코드도 쓰지 않는다. "다시 보게 만드는 관찰"이나 "관점 제시" 톤으로만
  쓴다. 예: "값이 같아도 떨어져 있으면 다른 영역"처럼.

- classification.primary (tag/subcategory)
  여기서는 알고리즘명을 그대로 노출하는 것이 의도된 동작이다. 위 통제 어휘만 정확히 쓴다.

- approach.recommendedApproach
  이 텍스트 하나가 여러 힌트 단계에서 그대로 재사용되므로, 항상 가장 엄격한 기준으로 쓴다.
  "왜 이 접근이 맞는지"까지만 설명하고, 구체적인 구현 절차를 순서대로 나열하지 않는다.
  나쁜 예(절차 나열): "아직 방문하지 않은 칸을 찾을 때마다 그 칸과 같은 값으로 상하좌우
  연결된 칸들을 BFS로 모두 방문 처리하며 영역의 크기를 세는 방향"
  좋은 예(이유 제시): "상하좌우로 연결된 같은 값의 칸만 하나의 영역으로 봐야 하므로, 한 칸에서
  시작해 인접한 칸으로 넓게 탐색을 넓혀가는 BFS가 적합한 방향"

- approach.alternativeApproaches
  접근 이름만 나열한다(예: "DFS(재귀 또는 스택)"). 각각을 길게 비교 설명하지 않는다.
  후보를 고를 때는 classification.primary로 정한 tag/subcategory에 해당하는 knowledge_base
  문서(rag/data_source/knowledge_base/{tag}.json)의 distinguishing_from[].ref 목록을 참고해서,
  이 문제에서 실제로 헷갈릴 만한 접근을 고른다. distinguishing_from[].signal(비교 설명문)의
  내용은 절대 그대로 옮기지 않는다 — 이름만 가져온다.

- solvingSupport.implementationCheckpoints
  3~5개의 체크리스트 형태. "핵심 로직을 채운" 수준까지 가면 안 되고, 확인할 지점만 짚는다.

- solvingSupport.stuckPointHints
  막히는 지점에 대한 방향 제시까지만, 정답 코드는 절대 포함하지 않는다.

- solvingSupport.keyDataStructures
  자료구조 이름/타입까지만(예: "Queue<int[]>"), 실제 사용 코드는 넣지 않는다.

- wrongAnswerDiagnosis.commonMistakes[].directionHint
  "이 부분을 확인해보세요" 수준까지만, 수정된 코드를 제시하지 않는다.

- wrongAnswerDiagnosis.fatalApproachSignals
  "느린 접근"이 아니라 "구조적으로 답이 나올 수 없는 접근"만 여기 넣는다. 단순히 비효율적이기만
  한 접근(맞는 답이 나오지만 시간초과가 나는 접근)은 여기 넣지 말고 commonMistakes의
  "시간초과" 항목으로 분류한다.

# 이미지 처리
원문에 이미지(격자, 그래프 그림 등)가 포함되어 있으면, 텍스트만으로 문제를 이해하려 하지 말고
이미지 내용을 실제로 확인한 뒤 필드를 채운다. 특히 classification.difficultyReason,
approach.keyInsight, edgeCases는 이미지에 나온 예시가 핵심 단서인 경우가 많다.

# 출력 스키마
아래 JSON Schema를 정확히 만족하는 JSON 객체 하나만 출력한다.
```

(위 프롬프트 끝에 §3의 JSON Schema를 그대로 이어 붙여서 시스템 메시지로 전달한다.)

## 3. JSON Schema (자동 검증용)

구조(타입/필수 필드/일부 통제 어휘)는 아래 스키마로 기계 검증한다. **tag별 subcategory 교차 검증**(예: `bfs` tag에 `subcategory`가 있으면 안 되고, `dp` tag의 `subcategory`는 4개 허용값 안에서만)은 일반 JSON Schema로 표현하기 번거로운 조건부 제약이라 스키마 밖에서 별도 검증한다 — `json_to_sql.py`의 `validate()`가 이미 이 로직(`CONTROLLED_TAGS`/`CONTROLLED_SUBCATEGORIES`)을 구현해뒀으므로, 신규 검증기를 짤 때 그대로 이식하면 된다.

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Cotea 문제 메타데이터",
  "type": "object",
  "additionalProperties": false,
  "required": [
    "problemId", "metadataVersion", "reviewedBy", "lastUpdated",
    "source", "classification", "approach", "solvingSupport", "wrongAnswerDiagnosis"
  ],
  "properties": {
    "problemId": { "type": "integer" },
    "metadataVersion": { "type": "string" },
    "reviewedBy": { "type": "null" },
    "lastUpdated": { "type": "string", "pattern": "^\\d{4}-\\d{2}-\\d{2}$" },
    "source": {
      "type": "object",
      "additionalProperties": false,
      "required": ["platform", "title", "level", "url", "language"],
      "properties": {
        "platform": { "const": "programmers" },
        "title": { "type": "string", "minLength": 1 },
        "level": { "type": "string", "pattern": "^Lv[1-5]$" },
        "url": { "type": "string", "format": "uri" },
        "language": { "const": "java" }
      }
    },
    "classification": {
      "type": "object",
      "additionalProperties": false,
      "required": ["primary", "difficultyReason"],
      "properties": {
        "primary": {
          "type": "array",
          "minItems": 1,
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["tag", "subcategory"],
            "properties": {
              "tag": {
                "enum": [
                  "array", "backtracking", "bfs", "binary_search", "bruteforcing",
                  "dfs", "dp", "graph_traversal", "greedy", "hash_set", "math",
                  "prefix_sum", "priority_queue", "queue_deque", "simulation",
                  "sliding_window", "sorting", "stack", "string", "trees", "two_pointer"
                ]
              },
              "subcategory": { "type": "array", "items": { "type": "string" } }
            }
          }
        },
        "difficultyReason": { "type": "string", "minLength": 1 }
      }
    },
    "approach": {
      "type": "object",
      "additionalProperties": false,
      "required": ["recommendedApproach", "expectedTimeComplexity", "expectedSpaceComplexity", "keyInsight"],
      "properties": {
        "recommendedApproach": { "type": "string", "minLength": 1 },
        "alternativeApproaches": { "type": "array", "items": { "type": "string" } },
        "expectedTimeComplexity": { "type": "string", "minLength": 1 },
        "expectedSpaceComplexity": { "type": "string", "minLength": 1 },
        "complexityVariables": { "type": "object", "additionalProperties": { "type": "string" } },
        "keyInsight": { "type": "string", "minLength": 1 }
      }
    },
    "solvingSupport": {
      "type": "object",
      "additionalProperties": false,
      "required": ["keyDataStructures", "implementationCheckpoints", "stuckPointHints"],
      "properties": {
        "keyDataStructures": { "type": "array", "items": { "type": "string" } },
        "implementationCheckpoints": {
          "type": "array",
          "minItems": 3,
          "maxItems": 5,
          "items": { "type": "string" }
        },
        "stuckPointHints": { "type": "object", "additionalProperties": { "type": "string" } }
      }
    },
    "wrongAnswerDiagnosis": {
      "type": "object",
      "additionalProperties": false,
      "required": ["commonMistakes"],
      "properties": {
        "commonMistakes": {
          "type": "array",
          "items": {
            "type": "object",
            "additionalProperties": false,
            "required": ["symptom", "likelyCause", "directionHint"],
            "properties": {
              "symptom": { "enum": ["오답", "시간초과", "런타임에러"] },
              "likelyCause": { "type": "string", "minLength": 1 },
              "directionHint": { "type": "string", "minLength": 1 }
            }
          }
        },
        "fatalApproachSignals": { "type": "array", "items": { "type": "string" } }
      }
    },
    "edgeCases": { "type": "array", "items": { "type": "string" } },
    "afterSolve": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "evaluationCriteria": { "type": "array", "items": { "type": "string" } },
        "optimizationHints": { "type": "array", "items": { "type": "string" } },
        "similarProblems": { "type": "array", "maxItems": 0 }
      }
    }
  }
}
```

Lv1 금지어 검사(`approach.keyInsight`, `classification.difficultyReason`에 `HintAnswerGuardrail.LEVEL_1_FORBIDDEN_TERMS`가 `KoreanBoundaryMatcher.containsAsStandaloneTerm` 기준으로 없는지)와 코드 유출 휴리스틱(완전한 메서드 시그니처 + 중괄호 블록 탐지)은 JSON Schema로 표현 불가능한 콘텐츠 검사라 별도 코드로 구현해야 한다 — `docs/problem-data-authoring-rules.md` §8 항목 5·6 그대로.

## 4. 골든 예시

문제 1829(카카오프렌즈 컬러링북, Lv2, `bfs`)를 기준으로 한다. 실제 `rag/problems/1829.json`은 `approach.recommendedApproach`가 절차를 그대로 나열하고 있어(§2의 "나쁜 예"와 동일) Lv2 안전 규칙 경계에 걸린다 — 아래는 그 한 필드만 교정한 골든 예시다. 실제 파일 교정은 이 문서 범위 밖의 별도 작업으로 남겨둔다.

```json
{
  "problemId": 1829,
  "metadataVersion": "1.1.0",
  "reviewedBy": null,
  "lastUpdated": "2026-07-23",
  "source": {
    "platform": "programmers",
    "title": "카카오프렌즈 컬러링북",
    "level": "Lv2",
    "url": "https://school.programmers.co.kr/learn/courses/30/lessons/1829",
    "language": "java"
  },
  "classification": {
    "primary": [
      { "tag": "bfs", "subcategory": [] }
    ],
    "difficultyReason": "0인 칸은 영역에서 제외해야 하고, 값이 같아도 상하좌우로 이어져 있지 않으면 서로 다른 영역으로 구분해야 한다"
  },
  "approach": {
    "recommendedApproach": "상하좌우로 연결된 같은 값의 칸만 하나의 영역으로 봐야 하므로, 한 칸에서 시작해 인접한 칸으로 넓게 탐색을 넓혀가는 BFS가 적합한 방향",
    "alternativeApproaches": ["DFS(재귀 또는 스택)로 동일하게 구현", "Union-Find로 연결된 칸들을 묶어 관리"],
    "expectedTimeComplexity": "O(m*n)",
    "expectedSpaceComplexity": "O(m*n)",
    "complexityVariables": {
      "m": "그림의 세로 크기(행 개수)",
      "n": "그림의 가로 크기(열 개수)"
    },
    "keyInsight": "값이 0인 칸은 애초에 영역으로 세지 않아야 하고, 같은 값이라도 상하좌우로 이어져 있지 않으면 서로 다른 영역이라는 점을 생각해보세요."
  },
  "solvingSupport": {
    "keyDataStructures": ["Queue<int[]>", "boolean[][] 방문 배열"],
    "implementationCheckpoints": [
      "값이 0인 칸을 탐색 대상에서 제외했는가",
      "아직 방문하지 않은 칸을 찾을 때마다 새 영역 탐색을 시작하는가",
      "같은 값을 가진 상하좌우 인접 칸만 같은 영역으로 묶는가",
      "각 영역의 크기를 세면서 지금까지의 최댓값과 비교하는가",
      "모든 칸을 확인할 때까지 반복하는가"
    ],
    "stuckPointHints": {
      "방문 처리": "이미 같은 영역으로 확인한 칸을 어떻게 표시할지 생각해보세요.",
      "초기값 설정": "영역 개수와 최대 넓이를 세기 시작하는 초기값을 어떻게 둘지 생각해보세요.",
      "경계 조건": "격자를 벗어나는 인덱스를 어떻게 걸러낼지 생각해보세요."
    }
  },
  "wrongAnswerDiagnosis": {
    "commonMistakes": [
      {
        "symptom": "시간초과",
        "likelyCause": "칸을 방문 처리하지 않고 같은 영역을 반복해서 다시 탐색하는 방식일 수 있다",
        "directionHint": "한 번 확인한 칸을 다시 탐색 대상으로 삼고 있지는 않은지 확인해보세요."
      },
      {
        "symptom": "오답",
        "likelyCause": "0인 칸을 하나의 영역으로 잘못 묶었거나, 상하좌우가 아닌 대각선 방향까지 같은 영역으로 판단했을 수 있다",
        "directionHint": "0인 칸을 탐색 대상에 포함하고 있는지, 대각선 방향까지 연결로 보고 있지는 않은지 확인해보세요."
      },
      {
        "symptom": "런타임에러",
        "likelyCause": "격자 범위를 벗어난 인덱스에 접근했을 수 있다",
        "directionHint": "이동한 위치가 격자 범위 안에 있는지 먼저 확인한 뒤 접근하고 있는지 점검해보세요."
      }
    ],
    "fatalApproachSignals": [
      "값만 같으면 서로 떨어져 있어도 같은 영역으로 묶는 접근 — 문제의 예시에서도 떨어진 같은 값 영역을 별개로 세야 한다고 명시하고 있어 구조적으로 틀린 답이 나옴"
    ]
  },
  "edgeCases": [
    "그림 전체가 0으로만 이루어져 영역이 하나도 없는 경우",
    "격자가 1x1로 최소 크기인 경우",
    "모든 칸이 같은 값이면서 하나로 완전히 연결된 경우"
  ],
  "afterSolve": {
    "evaluationCriteria": ["0인 칸을 올바르게 제외했는가", "영역 개수와 최대 넓이가 모두 정확한가"],
    "optimizationHints": ["방문 배열 대신 원본 배열 값을 0으로 바꿔가며 방문 처리해 공간을 아낄 수 있는지 검토"],
    "similarProblems": []
  }
}
```

## 5. 검증 실패 시 재시도/에스컬레이션 정책

1. **1차 실패**: 실패한 검증 항목(§3 스키마 위반이든, Lv1 금지어 린트든, tag/subcategory 교차검증이든)을 구체적으로 프롬프트에 첨부해 같은 원문으로 1회 재시도한다. ("아래 항목이 위반되었습니다: `classification.primary[0].subcategory`에 `bfs` tag에는 존재하지 않는 subcategory가 들어있습니다. 수정해서 다시 출력하세요." 같은 형태.)
2. **2차도 실패**: 자동 재시도를 중단한다. `reviewedBy: null` 상태로도 저장하지 않고, 사람 검수 큐에 넣는다 — 큐 항목에는 원문, 실패 사유 목록, 마지막 시도 결과를 함께 첨부해 사람이 바로 판단할 수 있게 한다.
3. **반복 실패 패턴**: 동일 검증 항목이 여러 문제에서 반복 실패하면(예: 특정 tag의 subcategory를 계속 잘못 고름) 개별 문제의 문제가 아니라 프롬프트 자체의 결함일 가능성이 높다 — 프롬프트 개선 백로그에 기록하고 §2를 갱신한다.

## 6. 아직 결정되지 않은 것 (후속 구현 과제)

- 이 규칙을 실제로 소비하는 실행 코드(LLM 호출 파이프라인 + §3 검증기 + Java/Spring Repository 저장)는 이 문서 범위 밖이며 별도로 구현해야 한다.
- 검증기를 처음부터 Java로 새로 짤지, `json_to_sql.py`의 `validate()`(tag/subcategory 교차검증 로직 포함)를 최소한으로 이식해 우선 쓸지는 미정.
- LLM 호출 주체(백엔드 배치 작업 vs 별도 오프라인 파이프라인 스크립트)도 미정.
