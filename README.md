<div align="center">
  <img src="./extension/cotea.svg" alt="Cotea logo" width="140" />

  # 코티 (Cotea)
</div>

정답이 아닌 **"방향성"**을 제시하는, 프로그래머스 코딩테스트 전용 AI 튜터 크롬 익스텐션입니다.

<p>
  <img alt="Java" src="https://img.shields.io/badge/Java-17-007396?style=flat-square&logo=openjdk&logoColor=white" />
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.4.4-6DB33F?style=flat-square&logo=springboot&logoColor=white" />
  <img alt="MySQL" src="https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white" />
  <img alt="Chrome Extension" src="https://img.shields.io/badge/Chrome%20Extension-Manifest%20V3-4285F4?style=flat-square&logo=googlechrome&logoColor=white" />
  <img alt="Claude" src="https://img.shields.io/badge/AI-Claude%20(Anthropic)-D97757?style=flat-square" />
  <img alt="Kakao" src="https://img.shields.io/badge/Login-Kakao%20OAuth-FEE500?style=flat-square&logo=kakaotalk&logoColor=black" />
  <img alt="AWS" src="https://img.shields.io/badge/Infra-AWS%20EC2-232F3E?style=flat-square&logo=amazonaws&logoColor=white" />
</p>

## 한 줄 소개

**코드티처 + 코치 = 코티.** 문제를 풀어주는 대신, 막힌 지점에 딱 맞는 만큼만 힌트를 줘서 스스로 사고를 진전시키게 돕습니다. (현재 Java 문제만 지원)

## 왜 만들었나 — 다른 AI 코딩 도우미와의 차이

코딩테스트를 독학하다 막히면 보통 둘 중 하나를 하게 됩니다 — 검색해서 답을 그대로 베끼거나, 너무 오래 붙잡고 있다가 포기하거나. 기존 AI 코딩 도우미들은 질문하면 바로 완성된 코드를 던져줘서 "학습"보다는 "정답 복사"에 가까운 사용 패턴을 유도합니다. 코티는 그 사이 지점을 겨냥합니다.

- **묻지 않은 건 먼저 말 안 함**: 오류 원인이나 최적화 방법은 사용자가 직접 물어보기 전엔 먼저 알려주지 않습니다. 답을 얻는 것보다 "무엇을 물어야 할지 스스로 판단하는 과정" 자체를 학습으로 봅니다.
- **한 방에 정답을 주지 않는 4단계 힌트**: 키워드 → 접근 방식 → 구현 순서 → 자유 질의. 지금 막힌 단계에 맞는 만큼만 개입합니다.
- **지금 짜고 있는 코드를 기준으로 답함**: 매 요청마다 사용자의 현재 코드를 함께 전달해, "일반적인 정답"이 아니라 "지금 이 코드에서 다음으로 뭘 해야 하는지"를 진단합니다.
- **문제별 사전 전처리 메타데이터 + 통제된 지식 베이스로 근거 있는 힌트**: 임베딩 기반 유사도 검색 대신, 문제별 요구 알고리즘/난이도 메타데이터(관계형 DB)와 21개 통제 카테고리로 정리된 알고리즘 지식 베이스를 정확 매칭해 프롬프트에 근거로 붙입니다 — 그럴듯한 말이 아니라 문제 특성에 맞는 힌트를 위해서입니다.

## 주요 기능

- **단계별 힌트**: 풀기 전(1~4단계) / 풀이 중 막힘 / 오답·시간초과 진단
- **챗봇형 UI**: 자주 나오는 질문은 버튼으로, 그 외엔 자유 텍스트로 질문
- **문제·코드 자동 동기화**: 프로그래머스 문제 페이지에서 문제 정보와 작성 중인 코드를 실시간으로 수집
- **카카오 로그인**: 카카오 OAuth로 로그인해 개인화된 학습 흐름 유지
- **RAG 기반 힌트 생성**: 문제별 메타데이터 + 알고리즘 지식 베이스를 결합해 답변 생성

## 기술 스택

| 영역          | 기술                                                             |
| ------------- | ---------------------------------------------------------------- |
| 백엔드        | Spring Boot 3.4.4 (Java 17), Spring WebFlux, Spring Data JPA      |
| 데이터베이스  | MySQL (문제 메타데이터, 사용자 정보)                              |
| 프론트엔드    | Chrome Extension (Manifest V3) — content script / side panel     |
| AI            | Anthropic Claude API (`/v1/messages`) — 힌트·코드 분석 핵심 엔진  |
| 로그인        | Kakao OAuth 2.0                                                   |
| 지식 베이스   | 정적 JSON 지식 문서 + category/subcategory 정확 매칭 (임베딩·벡터DB 미사용) |
| 인프라        | AWS EC2                                                           |
| 대상 플랫폼   | Programmers (Java)                                                |

> 💡 지식 베이스는 초기엔 임베딩 기반 벡터 검색(Chroma)을 검토했지만, 문서 규모(약 30개) 대비 운영 부담이 커서 category 기반 정확 매칭 방식으로 확정했습니다. 자세한 내용은 [`docs/vector-db-schema.md`](./docs/vector-db-schema.md), [`rag/README.md`](./rag/README.md) 참고.

## 문서

| 문서                                        | 설명                                  |
| ------------------------------------------- | ------------------------------------- |
| [기획서](./docs/project-plan.md)            | 프로젝트 개요, 일정, 범위             |
| [API 명세서](./docs/api-spec.md)            | 익스텐션 ↔ 백엔드 API 계약            |
| [Hint API 구현](./docs/java-hint-api.md)    | `POST /api/hint` 동작 흐름            |
| [ERD](./docs/erd.md)                        | 문제 메타데이터 관계형 DB 설계        |
| [벡터DB 스키마](./docs/vector-db-schema.md) | RAG 지식 베이스 구조 및 결정 히스토리 |

## 폴더 구조

```
cotea/
├── docs/           # 기획, API, ERD, 벡터DB 문서
├── backend/        # Spring Boot 서버
├── extension/      # Chrome 익스텐션 (content script / background / side panel)
└── rag/            # 지식 베이스 원본 데이터 및 빌드 스크립트
```

## 팀

**Team1 — 어벤져스**

|                                                                                                     김민호                                                                                                     |                                                                                                        김민국                                                                                                        |                                                                                                     윤석규                                                                                                      |                                                                                                       손주현                                                                                                        |
| :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------: |
|                                                    <img src="https://github.com/Mi-no-Kim.png" width="90" height="90" style="border-radius:50%;" alt="Mi-no-Kim" />                                                    |                                                    <img src="https://github.com/minguk0825.png" width="90" height="90" style="border-radius:50%;" alt="minguk0825" />                                                    |                                                    <img src="https://github.com/skyun-ui.png" width="90" height="90" style="border-radius:50%;" alt="skyun-ui" />                                                    |                                                    <img src="https://github.com/0cha-0cha.png" width="90" height="90" style="border-radius:50%;" alt="0cha-0cha" />                                                    |
| [![Mi-no-Kim](https://img.shields.io/badge/GitHub-Mi--no--Kim-181717?style=flat-square&logo=github)](https://github.com/Mi-no-Kim) | [![minguk0825](https://img.shields.io/badge/GitHub-minguk0825-181717?style=flat-square&logo=github)](https://github.com/minguk0825) | [![skyun-ui](https://img.shields.io/badge/GitHub-skyun--ui-181717?style=flat-square&logo=github)](https://github.com/skyun-ui) | [![0cha-0cha](https://img.shields.io/badge/GitHub-0cha--0cha-181717?style=flat-square&logo=github)](https://github.com/0cha-0cha) |
| • 데이터 전처리<br>• RAG 지식 베이스 구축<br>• 문서화                                                                                 | • AWS<br>• AI/프롬프트 엔지니어링                                                                                                       | • AWS<br>• AI/프롬프트 엔지니어링                                                                                                   | • Spring Boot 백엔드<br>• Chrome 익스텐션 프론트엔드                                                                                  |
