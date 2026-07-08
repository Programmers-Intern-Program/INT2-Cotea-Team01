# 코티(Cotea) 벡터DB 스키마 — RAG 지식 베이스 (v0.1)

> "RAG를 위한 데이터 준비" 대화에서 논의된 내용을 기준으로 정리. AI팀과의 협의 내용이 더 나오면 갱신 필요.
> 문제별 메타데이터(A)는 관계형 DB 대상이라 여기 포함하지 않음 — ERD 참고. 이 문서는 일반 지식 베이스(B)와 `common_pitfalls` 컬렉션만 다룸.

## 왜 벡터 DB인가

문제별 메타데이터(A)는 problem_id로 바로 조회하는 정형 데이터라 관계형 DB가 맞지만, 일반 지식 문서(B)는 "이 문제에 어떤 개념 설명이 필요한가"를 의미 기반으로 찾아야 해서 임베딩 유사도 검색이 필요합니다. **벡터 DB 종류(Pinecone, pgvector, Weaviate 등) 자체는 아직 미정 — TBD.**

---

## 컬렉션 1: knowledge_base (일반 지식 문서, B)

| 필드                        | 타입          | 설명                                                     |
| --------------------------- | ------------- | -------------------------------------------------------- | ------------------------------------------ |
| doc_id                      | string        | 문서 고유 ID (예: `dp_subsequence_lis_definition`)       |
| category (tag)              | string (enum) | 20개 통제 어휘 중 하나                                   |
| subcategory                 | string        | null                                                     | 카테고리별 접근 방식이 크게 갈릴 때만 사용 |
| hint_level_scope            | array<int>    | 이 문서가 검색 대상이 되는 힌트 레벨 목록 (예: `[2, 3]`) |
| content.definition          | string        | 개념 정의                                                |
| content.when_to_use         | string        | 언제 이 접근을 쓰는지                                    |
| content.java_specific_notes | string        | 자바 구현 시 주의점 (레벨 3~4에서 활용)                  |
| language                    | string        | `ko` (한국어 본문 + 임베딩 매칭용 영어 기술 용어 포함)   |
| embedding                   | vector        | 자동 생성, 수작업 입력 아님                              |

### 20개 통제 어휘 (category)

`array`, `string`, `stack`, `queue_deque`, `hash_set`, `trees`, `graph_traversal`, `bfs`, `dfs`, `sorting`, `binary_search`, `two_pointer`, `sliding_window`, `prefix_sum`, `greedy`, `dp`, `bruteforcing`, `simulation`, `backtracking`, `priority_queue`

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

### 청킹 전략

- 청크마다 자기완결적(self-contained)이어야 함 — 앞뒤 청크 없이도 의미가 통해야 함
- `hint_level_scope`로 힌트 레벨별 검색 범위를 필터링

---

## 컬렉션 2: common_pitfalls (자바 흔한 실수, Level 4 전용)

| 필드                | 타입   | 설명                                                             |
| ------------------- | ------ | ---------------------------------------------------------------- |
| pitfall_id          | string | 고유 ID                                                          |
| mistake_description | string | 실수 설명 (예: 정수 오버플로우, `equals` vs `==`, O(N²) 함정 등) |
| detection_signal    | string | 코드에서 이 실수를 식별할 수 있는 신호 (Level 4 코드 리뷰용)     |

카테고리 특정적이지 않은 **cross-category** 컬렉션 — 특정 알고리즘 태그에 종속되지 않고 자바 언어 자체의 흔한 실수를 다룸.

---

## 검색 전략 (matchStrategy)

```
exact_category_then_semantic
```

1. `PROBLEM_CLASSIFICATION`의 `tag`/`subcategory`로 **정확히 일치**하는 문서만 1차 필터링
2. 그 안에서 사용자 질문과의 **시맨틱(임베딩 유사도) 검색**으로 최종 문서 선정
3. `hint_level_scope`가 현재 요청의 `hintLevel`을 포함하지 않는 문서는 제외

`common_pitfalls`는 카테고리 필터 없이, Level 4 요청일 때만 별도로 검색 대상에 포함.

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
[knowledgeBase: 매칭된 문서 목록]           ← 벡터 DB(B), 이 문서의 대상
[userQuestion: 사용자 질문]
```

## 남은 TBD

1. 벡터 DB 제품 선택 (Pinecone / pgvector / Weaviate 등)
2. `common_pitfalls`가 Level 4 로직에 실제로 어떻게 통합되는지 (AI팀과 협의 예정 항목 중 하나로 남아있음)
3. `knowledge_base` 문서 작성 우선순위 — 20개 카테고리 중 어떤 것부터 채울지
