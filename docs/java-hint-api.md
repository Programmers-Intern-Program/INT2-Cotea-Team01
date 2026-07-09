# Java Hint API 구현 및 동작 흐름

> Spring Boot `/api/hint` 엔드포인트 구현 문서  
> 작성: 윤석규 | 최종 갱신: 2026-07-09

---

## 1. 개요

코티(Cotea) 힌트 API는 **문제별 메타데이터(A층) + 공통 prompt-policy + Claude**를 조합해  
정답 코드 없이 **방향성 힌트**를 생성합니다.

| 항목 | 내용 |
|------|------|
| 엔드포인트 | `POST /api/hint` |
| API 명세 | [`api-spec.md`](./api-spec.md) |
| 구현 위치 | `backend/` (Spring Boot) |
| LLM | Claude `claude-sonnet-4-6` |
| RAG (B층) | MVP 비활성 (`cotea.rag.enabled=false`) |

---

## 2. 전체 흐름

```text
[Postman / Chrome Extension]
        POST /api/hint + JSON
              ↓
       HintController
              ↓
       HintService (오케스트레이션)
   ┌──────────┼──────────┐
   ↓          ↓          ↓
prompt-   1829.json   RagRetrieval
policy    (문제 메타)  (현재 NoOp)
   └──────────┼──────────┘
              ↓
      PromptAssembler
   (system prompt + user message)
              ↓
        ClaudeClient
              ↓
      HintResponse (responseText)
```

---

## 3. 요청 / 응답 예시

### Request (Lv2, 1829)

```json
{
  "problemId": 1829,
  "stage": "BEFORE_SOLVE",
  "hintLevel": 2,
  "questionType": "FREE_TEXT",
  "questionText": "어떤 알고리즘으로 접근해야 할지 모르겠어요",
  "userCode": "",
  "conversationHistory": []
}
```

### Response

```json
{
  "responseText": "이 문제는 ... DFS/BFS ... 어떤 접근 방식이 더 편하실 것 같으신가요?",
  "stage": "BEFORE_SOLVE",
  "hintLevel": 2
}
```

### dry-run (LLM 호출 없음)

요청 body에 `"dryRun": true` 추가 → `systemPrompt`, `userMessage`만 반환.

---

## 4. 컴포넌트별 역할

| 클래스 | 역할 |
|--------|------|
| `HintController` | HTTP 요청 수신, DTO 변환 |
| `HintService` | 전체 파이프라인 오케스트레이션 |
| `PromptPolicyLoader` | `prompt-policy.json` 로드 (튜터 규칙, Lv1~4) |
| `ProblemMetaService` | `rag/problems/{problemId}.json` 로드 |
| `HintLevelResolver` | hintLevel 결정 (미입력 시 stage 기본값) |
| `QuestionResolver` | FREE_TEXT / BUTTON 질문 추출, 오답 이유 질문 판별 |
| `ProblemContextSelector` | hintLevel별 메타 필드 선택 |
| `PromptAssembler` | system / user 프롬프트 문자열 조립 |
| `RagRetrievalService` | RAG 검색 인터페이스 (MVP: `NoOpRagRetrievalService`) |
| `ClaudeClient` | Anthropic Messages API 호출 |

---

## 5. hintLevel별 메타 필드

| Lv | policy 규칙 | 1829.json에서 사용하는 필드 |
|----|-------------|----------------------------|
| 1 관점 | 알고리즘명 금지 | `keyInsight`, `difficultyReason` |
| 2 접근 | 알고리즘명 OK | `classification.primary`, `recommendedApproach` |
| 3 구현 | 의사코드 OK | `solvingSupport.*`, `recommendedApproach` |
| 4 리뷰 | 코드 분석 | `solvingSupport.*`, `wrongAnswerDiagnosis.*`, `userCode` |

---

## 6. 데이터 3층 구조

| 층 | 파일 | 상태 |
|----|------|------|
| 공통 Policy | `backend/.../config/prompt-policy.json` | ✅ 적용 |
| 문제별 메타 (A) | `rag/problems/{id}.json` | ✅ 적용 |
| 지식 베이스 (B) | `rag/build/knowledge_base_chunks.json` | ⬜ Java RAG 미연동 |

---

## 7. 로컬 실행

```bash
cd backend

# API Key: application-local.yml (gitignore)
#   cotea.claude.api-key: your-key

./gradlew bootRun
```

`bootRun`은 기본으로 `local` 프로파일을 사용해 `application-local.yml`을 자동 로드합니다.

### Postman / curl 테스트

```bash
# 실제 힌트
curl -s http://localhost:8080/api/hint \
  -H "Content-Type: application/json" \
  -d @examples/hint_request_1829_l2.json

# 프롬프트만 (API Key 불필요)
curl -s http://localhost:8080/api/hint \
  -H "Content-Type: application/json" \
  -d @examples/hint_request_1829_l2_dryrun.json
```

---

## 8. 설정 파일

| 파일 | 용도 |
|------|------|
| `application.yml` | 포트, Claude 모델, 메타 경로 |
| `application-local.yml` | API Key (local, **커밋 금지**) |
| `prompt-policy.json` | 튜터 정체성, Lv별 allow/forbid |
| `rag/problems/1829.json` | 문제별 전처리 메타 (gitignore) |

---

## 9. 향후 작업

- [ ] `RagRetrievalService` Java 구현 (pgvector 또는 Chroma HTTP)
- [ ] 문제 메타 DB 연동 (ERD 기준)
- [ ] Chrome Extension ↔ `/api/hint` 연동
- [ ] `buttonId` enum 확정 및 매핑

---

## 10. 검증 결과 (1829 Lv2)

- [x] `./gradlew compileJava` 성공
- [x] `dryRun: true` — 프롬프트 조립 확인
- [x] Claude 호출 — `responseText` 정상 (dfs/bfs 힌트 생성 확인)
