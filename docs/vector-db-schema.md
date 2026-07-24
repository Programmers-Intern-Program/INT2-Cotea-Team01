# 코티(Cotea) 벡터DB 스키마 — RAG 지식 베이스 (v0.1, 2026-07-14 결정으로 대부분 구식화됨)

> "RAG를 위한 데이터 준비" 대화에서 논의된 초기 설계안을 기준으로 작성됐다. **2026-07-14에 벡터DB(Chroma 등)를 안 쓰기로 결정하면서, 이 문서의 임베딩/시맨틱 검색/청킹 관련 서술은 실제 구현과 다르다.** 현재 실제로 동작하는 조회 방식(category+subcategory 정확 매칭 + 힌트 레벨 하드 필터, 임베딩 없음)은 `rag/README.md`의 "검색 로직 요약"이 최신 기준이다. 이 문서는 통제 어휘 목록(§ 21개 통제 어휘) 등 여전히 유효한 부분만 참고하고, 검색 전략/스키마의 `embedding`·`hint_level_scope`·`doc_id` 관련 서술은 아래 각주로 갱신했다.
>
> 문제별 메타데이터(A)는 관계형 DB 대상이라 여기 포함하지 않음 — ERD 참고. 이 문서는 일반 지식 베이스(B)만 다룸.
> (2026-07-23: `common_pitfalls` 컬렉션은 백엔드에 한 번도 연결된 적이 없어 데이터·빌드 스크립트·매핑을 전부 제거했다. 아래 "컬렉션 2" 서술은 삭제했다.)

## 왜 벡터 DB인가 (구식 — 2026-07-14 이후 벡터 DB 자체를 안 씀)

문제별 메타데이터(A)는 problem_id로 바로 조회하는 정형 데이터라 관계형 DB가 맞지만, 일반 지식 문서(B)를 처음 설계할 땐 "이 문제에 어떤 개념 설명이 필요한가"를 의미 기반으로 찾으려 했다. **하지만 2026-07-14에 규모(30개 문서·20개 카테고리)가 작아 임베딩 API·벡터DB 운영 부담이 이득보다 크다고 판단해 벡터 DB 자체를 쓰지 않기로 결정했다 — 그래서 "벡터 DB 종류 선택"은 더 이상 TBD가 아니라 안 쓰는 것으로 확정됐다.** 대신 category(+subcategory) 정확 매칭 + 힌트 레벨 하드 필터 방식을 쓴다 (`rag/README.md` 참고).

---

## 컬렉션 1: knowledge_base (일반 지식 문서, B)

> 아래 표는 최초 설계 당시 스키마다. 실제 구현 기준 각 필드의 생애주기(어디까지 쓰이는지)는 `docs/knowledge-base-authoring-rules.md` §2가 더 정확하다 — 특히 `doc_id`/`hint_level_scope`는 빌드 단계에서 드롭돼 완전 미사용이고, `embedding`은 벡터 DB를 안 쓰기로 하면서 애초에 존재하지 않는다(실제로는 문자열 그대로 저장, 임베딩 생성 없음).

| 필드                        | 타입          | 설명                                                     |
| --------------------------- | ------------- | -------------------------------------------------------- | ------------------------------------------ |
| doc_id                      | string        | 문서 고유 ID (예: `dp_subsequence_lis_definition`) — **실제로는 빌드 시 드롭돼 완전 미사용** |
| category (tag)              | string (enum) | 21개 통제 어휘 중 하나                                   |
| subcategory                 | string        | null                                                     | 카테고리별 접근 방식이 크게 갈릴 때만 사용 |
| hint_level_scope            | array<int>    | 이 문서가 검색 대상이 되는 힌트 레벨 목록 (예: `[2, 3]`) — **실제로는 빌드 시 드롭돼 완전 미사용, 레벨 게이팅은 `field_level_mapping.json`이 필드 단위로 함** |
| content.definition          | string        | 개념 정의                                                |
| content.when_to_use         | string        | 언제 이 접근을 쓰는지                                    |
| content.java_specific_notes | string        | 자바 구현 시 주의점 (레벨 3~4에서 활용)                  |
| language                    | string        | `ko` — 실제로는 매칭·콘텐츠 조립 어디에도 안 쓰이는 메타데이터 |
| ~~embedding~~                | ~~vector~~    | **실제로 존재하지 않음** — 벡터 DB를 안 쓰기로 하면서 임베딩 자체를 생성하지 않는다 |

### 21개 통제 어휘 (category)

`array`, `string`, `stack`, `queue_deque`, `hash_set`, `trees`, `graph_traversal`, `bfs`, `dfs`, `sorting`, `binary_search`, `two_pointer`, `sliding_window`, `prefix_sum`, `greedy`, `dp`, `bruteforcing`, `simulation`, `backtracking`, `priority_queue`, `math`

(2026-07-22: `math` 추가. `docs/erd.md`는 이전부터 21개로 기록했지만 실제 `knowledge_base` 문서가 없어 불일치 상태였음 — `rag/data_source/knowledge_base/math.json` 추가로 해소. 작성 규칙은 `docs/knowledge-base-authoring-rules.md` 참고.)

### subcategory가 있는 카테고리 (접근 방식이 크게 갈리는 경우만)

- `dp`: dp_subsequence, dp_knapsack, dp_path_counting, dp_general
- `hash_set`: hash_set_general, hash_set_frequency
- `greedy`: greedy_general, greedy_sort_based
- `simulation`: simulation_general, simulation_grid
- `trees`: trees_general, trees_bst
- `graph_traversal`: graph_general, graph_connectivity
- `array`: array_general, array_sorted_pattern
- `string`: string_general, string_pattern_basic

나머지 11개 카테고리는 subcategory 구분 없이 단일 문서.

### 청킹 전략 (구식 — 실제로는 청킹 자체를 안 함)

원래 설계는 아래와 같았지만, 벡터 DB를 안 쓰기로 하면서 "청크 분할"이라는 개념 자체가 없어졌다 — 문서 하나가 그대로 결과 하나다 (`rag/README.md` "build 산출물 관련 참고" 참고).

- ~~청크마다 자기완결적(self-contained)이어야 함~~
- ~~`hint_level_scope`로 힌트 레벨별 검색 범위를 필터링~~ → 실제로는 `field_level_mapping.json`이 필드 단위로 이 역할을 한다.

---

## 검색 전략 (matchStrategy) — 구식, 실제 동작은 `rag/README.md`의 "검색 로직 요약" 참고

원래 설계는 아래(`exact_category_then_semantic`)였지만, 2번(시맨틱 검색) 단계는 실제로 구현되지 않았다 — 벡터 DB를 안 쓰기로 하면서 랭킹/유사도 검색 개념 자체가 사라졌고, 1번(정확 매칭)과 3번(레벨 필터)만 하드 필터로 남았다. 실제 구현은 `KnowledgeBaseRagRetrievalService.java`.

```
exact_category_then_semantic   (2번 semantic 단계는 미구현 — 실제로는 exact_category_only)
```

1. `PROBLEM_CLASSIFICATION`의 `tag`/`subcategory`로 **정확히 일치**하는 문서만 1차 필터링
2. ~~그 안에서 사용자 질문과의 **시맨틱(임베딩 유사도) 검색**으로 최종 문서 선정~~ — 미구현, 벡터 DB 미사용 결정으로 폐기
3. `hint_level_scope`가 아니라 `field_level_mapping.json`의 필드별 `related_levels`가 현재 요청의 `hintLevel`을 포함하지 않는 필드는 제외

---

## 프롬프트 조립에서의 위치 (참고)

```
[tutorIdentity]
[coreRules]
[doNotReveal]
[hintLevelPolicy.{N}]
---
[problem: 문제명 / 난이도]
[phase: stage]
[hintLevel: N]
[problemMeta: classification + approach]   ← 관계형 DB(A)
[knowledgeBase: 매칭된 문서 목록]           ← knowledge_base(B), 정확 매칭 방식 (벡터 DB 아님)
[userQuestion: 사용자 질문]
```

## 남은 TBD

1. ~~벡터 DB 제품 선택 (Pinecone / pgvector / Weaviate 등)~~ — 2026-07-14에 벡터 DB 자체를 안 쓰기로 결정되면서 해소됨
2. `knowledge_base` 문서 작성 우선순위 — 21개 카테고리 중 어떤 것부터 채울지
