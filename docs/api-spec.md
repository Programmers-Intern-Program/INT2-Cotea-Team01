# 코티(Cotea) API 명세서 (v0.2)

> 이 문서는 팀 논의 내용을 기준으로 갱신됩니다.
> `TBD` 표시된 항목은 아직 확정이 필요합니다.

## 0. 공통 사항

| 항목                                                                | 내용                                                              |
| ------------------------------------------------------------------- | ----------------------------------------------------------------- |
| Base URL                                                            | `http://localhost:8080` (로컬), 배포 후 URL은 TBD                 |
| Content-Type                                                        | `application/json`                                                |
| 인증                                                                | **없음 (확정).**                                                  |
| 로그인/세션 도입하지 않음. 사용횟수 제한 기능은 MVP 스코프에서 제외 |
| 에러 응답 포맷                                                      | **확정.** HTTP 상태 코드(400/500) + body에 `errorCode`, `message` |

**에러 응답 예시**

```json
{
  "errorCode": "INVALID_STAGE",
  "message": "stage 값이 올바르지 않습니다."
}
```

**정의된 errorCode (필요할 때마다 추가)**

- `MISSING_PROBLEM_ID` (400)
- `INVALID_STAGE` (400)
- `AI_SERVICE_ERROR` (500)

**응답 방식**: **단일 JSON 응답 (확정).** 스트리밍(SSE)은 MVP 스코프 아님 — 힌트 텍스트 길이가 짧아 스트리밍 이득이 크지 않다고 판단.

---

## 1. POST /api/hint

핵심 엔드포인트. 힌트 단계별 요청(풀기 전 / 풀이 중 / 오답 진단)을 모두 처리한다.

### 대화 히스토리 정책 (확정)

탭(stage)이나 힌트 단계(hintLevel)를 바꿔도 대화 내역은 사라지지 않는다. **하나의 대화 스레드**를 유지하며, stage/hintLevel은 다음 요청에 실어 보내는 컨텍스트 값일 뿐이다. 1인 학습용 서비스이므로 stage/level별로 히스토리를 분리 관리하지 않는다.
히스토리는 프론트(팝업)가 배열로 들고 있다가, 매 요청마다 `conversationHistory` 전체를 함께 보낸다 (백엔드는 stateless).

### Request Body

| 필드                | 타입          | 필수                          | 설명                                                                                                                          |
| ------------------- | ------------- | ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ----------------------------- | -------------- | ------------- |
| problemId           | int           | Y                             | 문제 식별자. 프로그래머스 문제 URL에서 파싱한 숫자값 (예: `.../lessons/42840` → `42840`)                                      |
| stage               | string (enum) | Y                             | `BEFORE_SOLVE`                                                                                                                | `SOLVING`                     | `WRONG_ANSWER` | `AFTER_SOLVE` |
| hintLevel           | int           | N (선택)                      | 1~4. `BEFORE_SOLVE`에서 사용하는 참고용 필터. **questionType과 독립적** — 특정 레벨을 선택한 상태에서도 자유 텍스트 입력 가능 |
| questionType        | string (enum) | Y                             | `BUTTON`                                                                                                                      | `FREE_TEXT`                   |
| buttonId            | string        | questionType=BUTTON일 때 Y    | 사전 정의 버튼 질문 ID. **목록은 [`button-catalog.md`](./button-catalog.md) 참고** |
| questionText        | string        | questionType=FREE_TEXT일 때 Y | 사용자가 직접 입력한 질문                                                                                                     |
| userCode            | string        | Y                             | 현재 에디터에 작성된 코드 전체                                                                                                |
| language            | string        | Y (없으면 `"Unknown"`)        | 에디터에서 감지된 코드 언어 (예: `"Java"`). 현재는 Java만 정식 지원하며, 향후 지식 베이스 `code_signals` 매칭 등 언어별 분기에 사용 예정              |
| conversationHistory | array         | Y (없으면 빈 배열)            | 지금까지의 대화. `[{ role: "user"                                                                                             | "assistant", text: string }]` |
| submissionResult    | string (enum) | stage=WRONG_ANSWER일 때 Y     | 예: `WRONG_ANSWER`, `TIME_LIMIT_EXCEEDED`, `RUNTIME_ERROR`. **프로그래머스 DOM에서 실제로 뽑아낼 수 있는 값인지 검증 필요**   |

**힌트 레벨 의미 (확정, UI 라벨과 동일)**

- 1단계 (관점 힌트): 알고리즘명 공개 없이, 문제를 바라보는 키워드/관찰만 제공
- 2단계 (접근 힌트): 알고리즘/자료구조 이름을 공개하고 왜 그 방향인지 간단히 설명
- 3단계 (구현 힌트): 반복문/조건문/자료구조 사용 방식을 단계별로 안내, 의사코드까지 허용
- 4단계 (코드 리뷰/디버깅): 사용자가 작성 중인 코드를 기준으로 문제 지점 분석, 필요시 2~3줄 부분 코드 제공

**버튼 ID 목록 (확정)** — 상세는 [`button-catalog.md`](./button-catalog.md)

| buttonId | stage | 설명 |
|----------|-------|------|
| `hint_level_1` ~ `hint_level_4` | BEFORE_SOLVE, SOLVING | 힌트 레벨별 사전 질문 |
| `wrong_result_only` | WRONG_ANSWER | 채점 직후, 이유 질문 전 |
| `why_wrong` | WRONG_ANSWER | 오답 원인 질문 |
| `why_tle` | WRONG_ANSWER | 시간초과 원인 질문 |
| `why_runtime_error` | WRONG_ANSWER | 런타임 에러 원인 질문 |

**요청 예시 (풀기 전, 1단계 힌트 선택)**

```json
{
  "problemId": 42840,
  "stage": "BEFORE_SOLVE",
  "questionType": "BUTTON",
  "buttonId": "hint_level_1",
  "userCode": "",
  "conversationHistory": []
}
```

**요청 예시 (1단계를 선택한 채로 자유 텍스트 질문)**

```json
{
  "problemId": 42840,
  "stage": "BEFORE_SOLVE",
  "questionType": "FREE_TEXT",
  "buttonId": "hint_level_1",
  "questionText": "완전 탐색 접근법이 안 떠올라요",
  "userCode": "",
  "conversationHistory": [...]
}
```

### Response Body

| 필드         | 타입   | 설명                                       |
| ------------ | ------ | ------------------------------------------ |
| responseText | string | AI가 생성한 힌트/답변                      |
| stage        | string | 요청받은 stage 그대로 echo                 |
| hintLevel    | int    | 요청받은 hintLevel 그대로 echo (있는 경우) |

**응답 예시**

```json
{
  "responseText": "이 문제는 각 숫자마다 +/- 두 가지 선택지가 있고, 모든 조합을 확인해야 해요. 이런 유형은 보통 완전탐색(DFS/BFS) 방식으로 접근합니다. 어떤 방식이 익숙하신가요?",
  "stage": "BEFORE_SOLVE",
  "hintLevel": 1
}
```

### 에러 케이스

- `400 MISSING_PROBLEM_ID` — problemId 누락
- `400 INVALID_STAGE` — stage 값 오류
- `400 INVALID_HINT_LEVEL` — hintLevel이 1~4 범위를 벗어남
- `400 INVALID_QUESTION_TYPE` — questionType 값 오류
- `400 MISSING_BUTTON_ID` — `questionType=BUTTON`인데 buttonId 누락
- `400 INVALID_BUTTON_ID` — buttonId 값 오류 또는 현재 stage에서 사용할 수 없는 buttonId
- `400 MISSING_QUESTION_TEXT` — `questionType=FREE_TEXT`인데 questionText 누락
- `400 MISSING_SUBMISSION_RESULT` — `stage=WRONG_ANSWER`인데 submissionResult 누락
- `400 INVALID_SUBMISSION_RESULT` — submissionResult 값 오류 또는 오답 원인 버튼과 submissionResult 불일치
- `429 AI_RATE_LIMITED` — Claude API 요청 한도 초과
- `500 AI_SERVICE_ERROR` — AI 응답 형식 오류
- `502 AI_SERVICE_ERROR` — Claude API 네트워크 요청 실패 또는 예상하지 못한 4xx 오류
- `502 AI_AUTH_ERROR` — Claude API 인증 설정 오류
- `502 AI_SERVICE_UNAVAILABLE` — Claude API 5xx 계열 오류
- `504 AI_SERVICE_ERROR` — Claude API 응답 시간 초과

---

## 2. GET /api/problems/{problemId} (내부용 추정)

`{problemId}`는 int (프로그래머스 URL 파싱값).

전처리된 문제 메타데이터(난이도, 핵심 알고리즘, 요구 풀이 방식 등)를 조회.
**익스텐션이 직접 호출할 필요가 있는지, 아니면 백엔드 내부에서만 조회해서 /api/hint 처리 시 참고하는지 결정 필요.**

### classification 필드 (RAG 데이터 준비 논의에서 확정된 부분만 반영)

| 필드                   | 타입  | 설명                                                                     |
| ---------------------- | ----- | ------------------------------------------------------------------------ | ------------- |
| classification.primary | array | 문제에 해당하는 알고리즘 태그 목록. `[{ tag: string, subcategory: string | null }]` 형태 |

- `tag`: 20개 통제 어휘 중 하나 (예: `array`, `dfs`, `dp`, `greedy`, `hash_set`, `two_pointer` 등)
- `subcategory`: 카테고리별로 접근 방식이 크게 갈리는 경우에만 사용 (예: `dp_knapsack`, `dp_path_counting`, `hash_set_frequency`). 해당 없으면 `null`
- 배열 구조인 이유: 한 문제가 여러 알고리즘을 요구하는 경우(예: dfs + greedy)를 포지션 의존 없이 표현하기 위함

**참고**: RAG 지식 베이스(B, 일반 지식 문서) 쪽 청크에는 `hint_level_scope` 필드가 있어 힌트 레벨별로 검색 범위를 필터링하지만, 이건 백엔드 내부 검색 로직에 쓰이는 값이라 이 응답 스키마에는 포함하지 않음.

**여전히 TBD**: 난이도, 요구 풀이 방식(`approach`/`solvingSupport` 등) 필드는 ERD·AI팀 스키마가 더 다듬어져야 확정 가능.

---

## 3. POST /api/analysis (여유 기능, 미확정)

풀이 완료 후 코드 분석(모범 답안 대비 평가, 유사 문제 추천).
MVP 이후 기능이라 상세 필드는 생략.

## + GET /api/problems/supported

**핵심 엔드포인트.** 익스텐션이 활성화될 때 최초 1회 호출하여 코티(Cotea)가 지원하는 문제(Java 580문제)의 ID 목록을 조회한다. 익스텐션은 이 목록을 캐싱해두고, 현재 접속한 프로그래머스 문제 ID가 목록에 없을 경우(예: SQL 문제, 미지원 문제) 사이드 패널을 열지 않거나 비활성화 상태를 유지한다.

**공통 사항**

- **Method:** `GET`
- **Content-Type:** `application/json`
- **인증:** 없음

**Request**

- 별도의 Path Variable이나 Query Parameter, Request Body 없음.

**Response Body**

- **타입:** `Array of integers`
- **설명:** 전처리가 완료되어 코티가 지원하는 프로그래머스 문제 ID(숫자) 목록

**응답 예시 (200 OK)**

```json
[42840, 42842, 42839, 12906, 1845]
```

_(실제 응답은 580개의 정수가 포함된 배열로 내려가며, 데이터 크기는 수 KB 수준으로 매우 가볍습니다.)_

**에러 케이스**

- **500 INTERNAL_SERVER_ERROR** — DB 조회 실패 등 백엔드 내부 오류 발생 시 (기존 에러 응답 포맷과 동일)

```json
{
  "errorCode": "INTERNAL_SERVER_ERROR",
  "message": "지원 문제 목록을 불러오는 중 서버 오류가 발생했습니다."
}
```

**💡 코티 익스텐션(프론트엔드) 적용 가이드**

- 익스텐션의 `Background Service Worker`가 초기 로드될 때 이 API를 호출하여 결과를 `chrome.storage.local`에 저장(캐싱)해 두는 것을 권장합니다.
- 사용자가 프로그래머스 문제 페이지에 진입할 때마다 매번 API를 찌르지 않고, 스토리지에 저장된 배열에 `problemId`가 포함되어 있는지(`includes()`)만 검사하여 UI를 제어하면 성능과 서버 부하 모두 최적화할 수 있습니다!

---

## 4. 확정된 사항 요약

- problemId: int, 프로그래머스 URL 파싱값
- 인증: 없음 (MVP), 사용횟수 제한 기능 제외
- 에러 포맷: HTTP 상태 코드 + `errorCode`/`message`
- 응답 방식: 단일 JSON (SSE 스트리밍 아님)
- 대화 히스토리: stage/hintLevel 구분 없이 하나의 스레드로 유지, 프론트가 보관 후 매 요청에 동봉
- 힌트 레벨: BEFORE_SOLVE 전용, 선택 사항, questionType과 독립적

## 5. 남은 TBD

1. `STUCK` / `WRONG_ANSWER` 단계용 버튼 질문 목록(`questionId` enum) — AI/프롬프트 담당과 협의
2. 프로그래머스 제출 결과 DOM 파싱 가능 여부 검증 → `submissionResult` enum 확정
3. `/api/problems/{problemId}` 를 익스텐션이 직접 호출할지 여부. 응답 스키마 중 `classification.primary`는 RAG 데이터 준비 논의에서 확정됐지만, 난이도·요구 풀이 방식 등 나머지 필드는 ERD 확정 후 채워야 함
4. `assistant` 역할이 여러 hintLevel/stage를 넘나들 때 conversationHistory에 stage/hintLevel 메타를 같이 저장할지 여부 (현재는 role/text만 저장하는 걸로 가정)
