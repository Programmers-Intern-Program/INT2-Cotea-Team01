# 버튼 질문 카탈로그 (buttonId)

> 확정일: 2026-07-09 | 담당: B (프롬프트)  
> A에게 전달: `QuestionResolver`, `api-spec.md` validation 반영 필요

## BEFORE_SOLVE / SOLVING — 힌트 레벨 버튼

| buttonId | 표시 라벨 (UI) | 매핑 질문 | 권장 hintLevel |
|----------|----------------|-----------|----------------|
| `hint_level_1` | 1단계 관점 힌트 | 이 문제를 어떤 관점에서 바라봐야 할지 모르겠어요 | 1 |
| `hint_level_2` | 2단계 접근 힌트 | 어떤 알고리즘으로 접근해야 할지 모르겠어요 | 2 |
| `hint_level_3` | 3단계 구현 힌트 | 구현 순서가 잘 안 잡혀요 | 3 |
| `hint_level_4` | 4단계 코드 리뷰 | 제 코드에서 문제가 있는지 봐주세요 | 4 |

- `questionType=BUTTON`일 때 `buttonId` 필수
- `hintLevel`은 UI에서 선택한 탭/필터 값을 함께 보냄 (buttonId와 독립)

## WRONG_ANSWER — 채점 결과 후 버튼

| buttonId | 표시 라벨 (UI) | 매핑 질문 | 비고 |
|----------|----------------|-----------|------|
| `wrong_result_only` | (자동 표시) | 채점이 끝났어요 | 이유 질문 **전**. 원인 설명 금지 |
| `why_wrong` | 왜 틀렸나요? | 왜 틀렸는지 알려주세요 | `submissionResult=WRONG_ANSWER` |
| `why_tle` | 왜 시간초과? | 왜 시간초과가 났는지 알려주세요 | `submissionResult=TIME_LIMIT_EXCEEDED` |
| `why_runtime_error` | 왜 런타임 에러? | 왜 런타임 에러가 났는지 알려주세요 | `submissionResult=RUNTIME_ERROR` |

## FREE_TEXT

- `questionText`에 사용자 입력 그대로 사용
- 오답 이유 질문 판별: `왜`, `이유`, `원인`, `뭐가 틀`, `어디가 틀`, `잘못` 포함 시 → `afterUserAskReason` 정책

## 예시 요청 파일

| 시나리오 | 파일 |
|----------|------|
| Lv1 | `backend/examples/hint_request_1829_l1.json` |
| Lv2 | `backend/examples/hint_request_1829_l2.json` |
| Lv3 | `backend/examples/hint_request_1829_l3.json` |
| Lv4 | `backend/examples/hint_request_1829_l4.json` |
| 오답 + 이유 질문 | `backend/examples/hint_request_1829_wrong.json` |
| 오답 + 이유 질문 전 | `backend/examples/hint_request_1829_wrong_no_reason.json` |
| 시간초과 + 이유 질문 | `backend/examples/hint_request_1829_tle.json` |
| dryRun | 각 JSON에 `"dryRun": true` 추가 |
