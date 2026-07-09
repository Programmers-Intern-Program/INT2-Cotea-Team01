# Cotea Backend (Spring Boot)

`/api/hint` 힌트 API 서버입니다. 문제 메타 + prompt-policy + Gemini로 힌트를 생성합니다.

## 실행

```bash
cd backend

# 문제 메타 준비 (gitignore)
cp ../rag/problems/1829.json ../rag/problems/1829.json  # 이미 있으면 생략

export GEMINI_API_KEY=your_key
# 선택: 문제 메타 경로 (기본 ../rag/problems)
export COTEA_PROBLEM_META_DIR=../rag/problems

./gradlew bootRun
```

## API

### POST /api/hint

`docs/api-spec.md`와 동일 + 로컬 테스트용 `dryRun: true` 지원.

**dry-run 예시 (API 키 불필요)**

```bash
curl -s http://localhost:8080/api/hint \
  -H "Content-Type: application/json" \
  -d @examples/hint_request_1829_l2_dryrun.json
```

`dryRun: true`를 요청 body에 추가하면 프롬프트만 확인할 수 있습니다.
예시 파일: `examples/hint_request_1829_*.json`

## 패키지 구조

```
com.cotea
├── controller/HintController.java       # POST /api/hint
├── service/hint/
│   ├── HintService.java               # run_hint() 오케스트레이션
│   ├── PromptAssembler.java           # system/user 프롬프트 조립
│   ├── ProblemContextSelector.java    # 메타 필드 선택
│   ├── HintLevelResolver.java
│   └── QuestionResolver.java
├── service/problem/ProblemMetaService.java
├── service/policy/PromptPolicyLoader.java
├── service/rag/RagRetrievalService.java  # 인터페이스 (MVP: NoOp)
└── client/GeminiClient.java
```

## RAG

`cotea.rag.enabled=false` (기본) — 메타데이터만으로 힌트 생성.
Java RAG 구현 시 `RagRetrievalService` 구현체를 추가하고 `enabled=true`로 전환.
