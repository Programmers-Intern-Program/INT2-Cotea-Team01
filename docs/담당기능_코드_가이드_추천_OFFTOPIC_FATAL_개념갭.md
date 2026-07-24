# 담당 기능 코드 가이드 (추천 · OFF_TOPIC · FATAL · 개념갭)

팀원이 **무엇을 위해 / 어디서 / 이 줄이 왜 있는지** 빠르게 따라갈 수 있게 정리한 문서입니다.  
더 긴 기획·플로는 `docs/관련_유형_문제_추천_구조.md`, `docs/개인화_추천_기능_제안_통합.md`를 참고하세요.

---

## 0. 한눈에 보기

| 기능 | 목적 | 핵심 진입점 |
|------|------|-------------|
| 유사 문제 추천 | 같은 태그(유형)의 연습 문제 제안 | `GET /api/recommend` → `RecommendationService` |
| 추천 개인화 | 로그인 시 자주 막힌 태그에 가산점 | `JwtTokenProvider` + `HintLogUserWeaknessProvider` |
| 개념갭 → 추천 칩 | “유형 자체를 모름”이면 FE에 추천 유도 | `HintResponse.suggestConceptDrill` |
| OFF_TOPIC | 잡담/무관 질문을 힌트 파이프에서 분리 | `HintService.isOffTopicRoute` |
| FATAL 접근 경고 | 답이 구조적으로 안 나오는 접근이면 선제 경고 | `FatalApproachLlmSignal` |

힌트 요청 한 번의 큰 줄기 (`HintService.generate`):

```
POST /api/hint
  ├─ (1) OFF_TOPIC? → 거절/유도 응답 (Claude 힌트 파이프 X)
  └─ (2) RELATED
        ├─ 개념갭 룰 판정
        ├─ problemContext 조립 (+ FATAL이면 signals 강제 포함)
        ├─ 시스템 프롬프트에 CONCEPT_GAP / FATAL / Lv1 금지개념 지시 피기백
        ├─ Claude 호출
        ├─ 마커 파싱·제거 → (FATAL YES면 경고 prefix)
        └─ suggestConceptDrill = 룰 OR LLM
```

---

## 1. 유사 문제 추천

### 1.1 목적 / 용도

- 사용자가 **지금 풀고 있는 문제**와 **같은 알고리즘 태그**인 다른 문제를 골라 준다.
- 개념이 부족할 때 FE 칩 → 이 API를 호출한다.
- **로그인 없이도** 동작한다. 로그인하면 약점 태그 가산점만 추가된다.

### 1.2 주요 파일

| 파일 | 역할 |
|------|------|
| `RecommendationController` | `GET /api/recommend?problemId=&tags=&limit=` |
| `RecommendationService` | 후보 수집 · 점수 · Top-N |
| `UserWeaknessProvider` | 개인화 프로필 인터페이스 |
| `HintLogUserWeaknessProvider` | 최근 7일 `user_hint_log` 태그 → `weakTagCounts` |
| `UserRecommendationProfile` | `solvedProblemIds` + `weakTagCounts` record |

### 1.3 코드가 하는 일 (핵심 구간)

`RecommendationService.recommend(...)` 요지:

```text
1) Authorization → userId 있으면 프로필 로드, 없으면 empty
2) source 문제 로드 (없으면 404 MISSING_PROBLEM_ID)
3) source 태그 결정 (또는 tags= 쿼리로 덮어쓰기)
4) 같은 태그인 다른 problem_id 후보 조회
5) (프로필에 solved가 있으면) 푼 문제 제외  ← 현재는 보통 비어 있음
6) 점수 정렬 후 limit개 반환
```

중요한 설계 결정:

```java
// HintLogUserWeaknessProvider
return new UserRecommendationProfile(Set.of(), weakTagCounts);
```

- `solvedProblemIds`는 **비운다**.
- 이유: 힌트만 본 문제를 “이미 푼 것”처럼 빼면 연습 후보가 사라짐 (리뷰 반영).
- 약점 가산점(`weakTagCounts`)만 쓴다.

점수 감각 (구현 기준):

- 서브카테고리 일치 → 가점
- 더 쉬운 레벨 → 가점
- weak 태그면 로그 횟수에 비례 가점 (태그당 상한 있음, `WEAK_TAG_SCORE_CAP`)

### 1.4 팀원이 볼 때

- 추천 결과 이상 → `[RECOMMEND]` 로그의 `tags / candidates / personalized`
- DB에 문제·classification이 있어야 후보가 생김. JSON 폴백만으로는 추천 DB 쿼리가 안 됨.

---

## 2. 개념갭 (suggestConceptDrill)

### 2.1 목적 / 용도

- “이 유형 **개념 자체**를 모르는 상태”로 보이면 FE에 **관련 유형 추천 칩**을 띄운다.
- 힌트 레벨(Lv1~4)과는 무관하다. 레벨은 “얼마나 자세히”이고, 개념갭은 “유형을 아느냐”다.

### 2.2 하이브리드

```
ConceptGapClassifier (룰, 빠름)
    OR
ConceptGapLlmSignal  (같은 Claude 호출에 [[CONCEPT_GAP: YES|NO]] 피기백)
    ↓
suggestConceptDrill
```

### 2.3 룰 (`ConceptGapClassifier`) — 이 줄들의 뜻

적용 조건:

- `FREE_TEXT`만
- `AFTER_SOLVE` 제외
- 오답/디버깅·“내 코드” 질문은 제외

시그널 예시:

| 종류 | 예 | 의미 |
|------|----|------|
| 정체 질문 | `BFS가 뭐예요?` | 알고리즘이 뭔지 물음 |
| 전면 부재 | `아예 감이 안 잡혀요` | 시작조차 못 함 |
| 설명 요청 | `BFS에 대해 알려주세요` | 개념 학습 요청 |

**오탐 주의 (Fix/be#118):**  
예전에는 `"알려"` 단독 매칭 때문에  
`개선점만 짧게 알려주세요` 도 개념갭으로 잡혔다.  
지금은 `대해 알려`, `설명해`, `개념 설명`처럼 **개념을 가르치는 구** 위주로 좁혔다.

### 2.4 LLM 피기백 (`ConceptGapLlmSignal`)

```java
// HintService.generateRelated
systemPrompt = systemPrompt + conceptGapLlmSignal.instruction();  // 지시만 붙임
// ... Claude 호출 ...
ConceptGapLlmSignal.Parsed parsed = conceptGapLlmSignal.parse(rawText);
// [[CONCEPT_GAP: ...]] 제거 + YES/NO 읽기
suggestConceptDrill = conceptGap || llmConceptGap;  // 룰 OR LLM
```

- **추가 API 호출 없음.** 힌트 답변 맨 끝에 마커를 붙이라고 시키고, 백엔드가 떼어낸다.
- 지시문에 “방향 검증·개선점·코드 리뷰는 **반드시 NO**”를 넣어 오탐을 줄였다.

### 2.5 FE 쪽

- `suggestConceptDrill === true` → 칩 노출 → 클릭 시 `GET /api/recommend`

---

## 3. OFF_TOPIC (무관 질문)

### 3.1 목적 / 용도

- 날씨·잡담 등 **문제와 무관한 질문**을 본 힌트(Claude + 메타 + 코드) 파이프에 태우지 않는다.
- 스토리형 문제에 “주식/연애” 소재가 있을 수 있어, **규칙만으로 하드 OFF_TOPIC 하지 않는다.**

### 3.2 흐름

```
OffTopicQuestionClassifier (규칙)
  RELATED  → 정상 힌트
  OFF_TOPIC → (현재 규칙은 사실상 거의 안 씀)
  AMBIGUOUS → OffTopicRouteLlmClassifier (OpenAI 등) → RELATED | OFF_TOPIC
```

`HintService.isOffTopicRoute`:

```java
Verdict verdict = offTopicQuestionClassifier.classify(...);
if (RELATED) return false;           // 힌트 계속
if (OFF_TOPIC) return true;          // 오프토픽 응답
// AMBIGUOUS
llmVerdict = offTopicRouteLlmClassifier.classify(...);
return llmVerdict == OFF_TOPIC;
```

### 3.3 규칙 클래스에서 볼 줄

```java
// BUTTON·빈 질문 → RELATED (오프토픽 판정 안 함)
// RELATED 정규식(힌트|코드|오답|...) 매칭 → RELATED
// bfs/dfs/dp/큐/스택은 조사 경계(KoreanBoundaryMatcher)로만 RELATED
// 그 외 → 전부 AMBIGUOUS  (하드 UNRELATED 없음)
```

“주식만 나와도 OFF” 같은 false positive를 피하려고 AMBIGUOUS로 넘긴 뒤 LLM이 맥락 판단한다.

### 3.4 설정

`application.yml` / local:

```yaml
cotea:
  off-topic:
    enabled: true
    llm-route-enabled: true   # false면 AMBIGUOUS를 RELATED로 처리 (LLM 비용 0)
```

---

## 4. FATAL 접근 경고

### 4.1 목적 / 용도

- 사용자 코드가 문제 메타의 **`fatalApproachSignals`**(구조적으로 답이 안 나오는 접근)에 해당하면  
  답변 **맨 앞에 경고 문구를 강제**한다.
- 역시 **피기백** (`[[FATAL_APPROACH: YES|NO]]`). 추가 LLM 호출 없음.

### 4.2 적용 조건 (`FatalApproachLlmSignal.isApplicable`)

```text
userCode 있음
AND 문제 메타에 fatalApproachSignals 배열이 비어 있지 않음
AND stage ≠ AFTER_SOLVE
```

### 4.3 꼭 알아야 할 한 가지 (Fix/be#111 후속)

판정 ON이면 시스템 프롬프트에 “signals 보고 YES/NO” 지시가 붙는다.  
그런데 `problemContext`는 힌트 Lv/stage 정책(`usesProblemFields`)에 따라 **signals가 빠질 수 있다** (예: Lv1~3).

그래서 applicable일 때:

```java
fatalApproachLlmSignal.ensureSignalsInContext(problemContext, problem);
```

→ `fields`에 `wrongAnswerDiagnosis.fatalApproachSignals`를 **강제로 넣는다.**  
지시만 있고 기준 목록이 없는 추측 판정을 막기 위함이다.

지시문에는 “신호는 YES/NO에만 쓰고, 답변에 목록 나열·복사 금지”도 있다.

### 4.4 응답 후처리

```java
parse → 마커 제거
YES면 ensureWarningPrefix(...)  // "답이 나오기 어려운 접근일 수 있어요..." 를 앞에 붙임
```

로그: `[FATAL_APPROACH] problemId=... fatal=true/false`

---

## 5. HintService에서 “피기백 마커” 순서

마커가 가드레일/셀프리뷰에 들어가면 오염되므로 **먼저 제거**한다.

대략 순서:

1. CONCEPT_GAP 파싱·제거  
2. FATAL_APPROACH 파싱·제거 (+ 경고 prefix)  
3. FORBIDDEN_CONCEPT (Lv1) 파싱·제거  
4. 가드레일 / 셀프리뷰  

`dryRun=true`면 LLM을 안 부르고 `systemPrompt`/`userMessage`만 돌려준다.  
로컬에서 FATAL signals 포함 여부·지시문 삽입 확인용으로 쓰기 좋다.

---

## 6. 로컬에서 빠르게 확인하는 방법

### 추천

```bash
curl "http://localhost:8080/api/recommend?problemId=1829&limit=3"
```

### 힌트 dryRun (FATAL signals 강제 포함 확인)

```bash
# userCode + fatal 메타 있는 문제
# response.systemPrompt 에 FATAL_APPROACH
# response.userMessage 에 fatalApproachSignals
```

### 개념갭

- `BFS가 뭐예요?` → `suggestConceptDrill` true 기대  
- `개선점만 짧게 알려주세요` + 정상 코드 → false 기대 (#118)

### OFF_TOPIC

- `오늘 서울 날씨 어때?` → `route=OFF_TOPIC` (LLM 라우트)  
- `예제에서 같은 색이 떨어져 있으면?` → `RELATED`

---

## 7. 관련 이슈 / PR (참고)

| 주제 | 이슈/PR 예 |
|------|------------|
| 추천 API · FE 칩 | #64, #66 |
| 개인화 밑작업 · 힌트로그 약점 | #80/#88, #90, #95/#96 |
| OFF_TOPIC 하이브리드 | #97/#98 |
| FATAL 피기백 · signals 강제 포함 | #111/#113, #116 |
| 개념갭 오탐 완화 | #118/#119 |

---

## 8. 수정할 때 체크리스트

- [ ] 정책/스코어/판정 문구를 바꾸면 **단위 테스트**도 같이 (`*ClassifierTest`, `*LlmSignalTest`, `RecommendationServiceTest`)
- [ ] FATAL·개념갭은 **마커가 사용자에게 안 보이는지** (parse 제거)
- [ ] OFF_TOPIC 규칙을 세게 조이면 스토리 문제 false positive 재발 가능 → AMBIGUOUS+LLM 취지 유지
- [ ] 추천 개인화: 힌트 로그만으로 `solvedProblemIds`를 채우지 말 것
- [ ] `ddl-auto: validate` — 엔티티/컬럼 바꾸면 SQL 스키마도 같이

---

문의·보강이 필요하면 이 문서에 섹션을 추가하거나, 위 상세 문서 링크를 늘리면 됩니다.
