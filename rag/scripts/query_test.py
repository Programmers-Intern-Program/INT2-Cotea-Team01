"""
knowledgeBasePolicy.matchStrategy = exact_category_then_semantic 를 재현.

사용법:
  python query_test.py            -> 아래 TEST_CASES를 순서대로 일괄 실행
  python query_test.py --interactive (또는 -i)  -> 터미널에서 직접 질문 입력하며 반복 테스트
"""

import json
import os
import sys
import time

import chromadb
from chromadb import Documents, EmbeddingFunction, Embeddings
from dotenv import load_dotenv
from google import genai
from google.genai import types
from google.genai.errors import ClientError

load_dotenv()
client_genai = genai.Client(api_key=os.environ["GEMINI_API_KEY"])

with open("../config/field_level_mapping.json", encoding="utf-8") as f:
    FIELD_LEVEL_MAPPING = json.load(f)

# 레벨이 일치하는 청크에게 줄 가산점. distance에서 이 값을 빼서 순위를 살짝
# 당겨준다 (하드 필터가 아니라 소프트 부스트 — 레벨이 안 맞아도 후보에서
# 완전히 배제되지는 않는다). 값은 임의로 잡은 것이라 실제 검색 결과를 보며
# 조정이 필요하다.
LEVEL_BOOST = 0.05


class GeminiEmbeddingFunction(EmbeddingFunction):
    def __init__(self, task_type: str):
        self.task_type = task_type

    def __call__(self, input: Documents) -> Embeddings:
        for attempt in range(5):
            try:
                result = client_genai.models.embed_content(
                    model="gemini-embedding-001",
                    contents=input,
                    config=types.EmbedContentConfig(task_type=self.task_type),
                )
                return [e.values for e in result.embeddings]
            except ClientError as e:
                if "RESOURCE_EXHAUSTED" in str(e) and attempt < 4:
                    print("    속도 제한 도달. 40초 대기 후 재시도")
                    time.sleep(40)
                else:
                    raise


DISTANCE_THRESHOLD = None  # 실제 거리 분포를 먼저 확인한 뒤 값을 정한다. None이면 필터링 없음.


def apply_threshold(results):
    if DISTANCE_THRESHOLD is None:
        return results
    return [r for r in results if r[2] <= DISTANCE_THRESHOLD]


CONTROLLED_TAGS = [
    "array", "string", "stack", "queue_deque", "hash_set", "trees",
    "graph_traversal", "bfs", "dfs", "sorting", "binary_search",
    "two_pointer", "sliding_window", "prefix_sum", "greedy", "dp",
    "bruteforcing", "simulation", "backtracking", "priority_queue",
]


def classify_tags(user_question: str, max_retries: int = 5) -> list[str]:
    """
    [테스트 전용 편의 기능] 질문 텍스트만으로 관련 태그를 추론한다.

    주의: 실제 서비스에서는 태그를 질문에서 추론하지 않는다. 태그는 사용자가
    풀고 있는 문제의 classification.primary에 이미 정해져 있는 값이고,
    지식 베이스 검색의 1단계(category 정확 매칭)는 그 값을 그대로 쓴다.
    이 함수는 실제 문제 맥락 없이 자유롭게 질문만 던져 테스트하고 싶을 때만
    쓰는 보조 기능이며, 실제 파이프라인 동작을 정확히 재현하지 않는다.
    """
    prompt = f"""다음은 코딩테스트 알고리즘 카테고리 20개다:
{", ".join(CONTROLLED_TAGS)}

아래 사용자 질문과 관련 있는 카테고리를 이 20개 중에서만 골라라.
관련 있는 게 여러 개면 전부 골라도 되고, 확실한 것만 골라라.
반드시 이 형식으로만 답하라 (다른 설명 없이): ["tag1", "tag2"]
관련 있는 게 하나도 없으면: []

사용자 질문: {user_question}"""

    for attempt in range(max_retries):
        try:
            result = client_genai.models.generate_content(
                model="gemini-2.5-flash",
                contents=prompt,
            )
            text = result.text.strip()
            # 코드블록으로 감싸서 응답하는 경우 대비
            text = text.strip("`").removeprefix("json").strip()
            tags = json.loads(text)
            return [t for t in tags if t in CONTROLLED_TAGS]
        except ClientError as e:
            if "RESOURCE_EXHAUSTED" in str(e) and attempt < max_retries - 1:
                print("    속도 제한 도달. 40초 대기 후 재시도")
                time.sleep(40)
            else:
                raise
        except (json.JSONDecodeError, AttributeError):
            print(f"    태그 추론 응답 파싱 실패 (원문: {text!r}) — 빈 태그로 처리")
            return []


def _apply_level_boost(all_results, metadatas, collection_name, hint_level, top_k):
    """
    field_level_mapping.json의 필드 기본값을 쓰되, 청크에 개별
    override_lv{N} boolean(있는 경우만)이 있으면 그걸 우선한다.
    레벨이 안 맞아도 후보에서 제거하지는 않는다 (하드 필터 아님).
    """
    mapping = FIELD_LEVEL_MAPPING.get(collection_name, {})
    boosted = []
    for (chunk_id, content, distance), meta in zip(all_results, metadatas):
        field = meta.get("field")
        override_key = f"override_lv{hint_level}"
        has_override = any(f"override_lv{lv}" in meta for lv in (2, 3, 4))
        if has_override:
            level_matches = meta.get(override_key, False)
        else:
            related_levels = mapping.get(field, {}).get("related_levels", [])
            level_matches = hint_level in related_levels
        adjusted = distance - LEVEL_BOOST if level_matches else distance
        boosted.append((chunk_id, content, distance, adjusted))

    boosted.sort(key=lambda x: x[3])
    return [(c, d, dist) for c, d, dist, _adj in boosted[:top_k]]


def retrieve_knowledge(client, tags, hint_level, user_question, top_k=3, pool_size=9):
    """1단계: category 정확 매칭 -> 2단계: 시맨틱 검색 -> 3단계: 레벨 소프트 부스트로 재정렬."""
    collection = client.get_collection(
        name="knowledge_base",
        embedding_function=GeminiEmbeddingFunction(task_type="RETRIEVAL_QUERY"),
    )
    where_filter = {"category": {"$in": tags}}

    # top_k보다 넉넉하게 뽑아서, 레벨 부스트로 재정렬할 여지를 만든다.
    results = collection.query(
        query_texts=[user_question], n_results=pool_size, where=where_filter
    )
    all_results = list(zip(results["ids"][0], results["documents"][0], results["distances"][0]))
    metadatas = results["metadatas"][0]
    all_results = _apply_level_boost(all_results, metadatas, "knowledge_base", hint_level, top_k)
    return apply_threshold(all_results)


def retrieve_pitfalls(client, hint_level, user_question, top_k=2, pool_size=6):
    """common_pitfalls는 category 필터 없음. 레벨은 소프트 부스트로만 반영."""
    collection = client.get_collection(
        name="common_pitfalls",
        embedding_function=GeminiEmbeddingFunction(task_type="RETRIEVAL_QUERY"),
    )
    results = collection.query(query_texts=[user_question], n_results=pool_size)
    all_results = list(zip(results["ids"][0], results["documents"][0], results["distances"][0]))
    metadatas = results["metadatas"][0]
    all_results = _apply_level_boost(all_results, metadatas, "common_pitfalls", hint_level, top_k)
    return apply_threshold(all_results)


def assemble_prompt(problem_title, problem_level, hint_level, user_question, retrieved):
    kb_ids = ", ".join(r[0] for r in retrieved)
    return "\n".join([
        f"[problem: {problem_title} / {problem_level}]",
        f"[hintLevel: {hint_level}]",
        f"[knowledgeBase: {kb_ids}]",
        f"[userQuestion: {user_question}]",
    ])


def run_one_case(client, title, level, tags, hint_level, question):
    print("=" * 60)
    print(f"문제: {title} / {level}  |  태그: {tags}  |  힌트 레벨: {hint_level}")
    print(f"질문: {question}")
    print("-" * 60)

    if hint_level == 4:
        results = retrieve_pitfalls(client, hint_level, question)
        print("검색된 common_pitfalls 청크 (거리가 작을수록 유사):")
    else:
        results = retrieve_knowledge(client, tags, hint_level, question)
        print("검색된 knowledge_base 청크 (거리가 작을수록 유사):")

    if not results:
        msg = "카테고리/레벨 조합에 맞는 청크 자체가 없음" if DISTANCE_THRESHOLD is None else f"distance <= {DISTANCE_THRESHOLD} 만족 청크 없음"
        print(f"  (결과 없음 — {msg})")
    for chunk_id, content, distance in results:
        print(f"  - {chunk_id} (distance={distance:.4f})")
        print(f"    {content[:70]}...")

    print()
    print("[조립된 프롬프트]")
    print(assemble_prompt(title, level, hint_level, question, results))
    print()


# 여러 문제/레벨/질문 조합을 한 번에 돌려보고 싶을 때 여기에 추가하면 됩니다.
TEST_CASES = [
    {
        "title": "타겟 넘버", "level": "Lv2",
        "tags": ["bruteforcing", "dfs"], "hint_level": 2,
        "question": "완전 접근법이 안 떠올라요",
    },
    {
        "title": "타겟 넘버", "level": "Lv2",
        "tags": ["bruteforcing", "dfs"], "hint_level": 3,
        "question": "재귀로 어떻게 구현해야 할지 모르겠어요",
    },
    {
        "title": "완주하지 못한 선수", "level": "Lv1",
        "tags": ["hash_set"], "hint_level": 2,
        "question": "이름을 어떻게 비교해야 할지 감이 안 잡혀요",
    },
    {
        "title": "완주하지 못한 선수", "level": "Lv1",
        "tags": [], "hint_level": 4,  # 레벨 4는 common_pitfalls라 tags 불필요
        "question": "제 코드가 시간초과가 나는데 이유를 알려주세요",
    },
]


def run_batch():
    client = chromadb.PersistentClient(path="./chroma_db")
    for case in TEST_CASES:
        run_one_case(
            client, case["title"], case["level"], case["tags"],
            case["hint_level"], case["question"],
        )


def run_interactive():
    client = chromadb.PersistentClient(path="./chroma_db")
    print("대화식 테스트 모드입니다. 빈 입력 후 Enter로 종료합니다.")
    print("(태그는 자동 추론됩니다 — 실제 서비스에서는 문제 데이터에서 가져오는 값이라")
    print(" 이 자동 추론은 테스트 편의 기능일 뿐, 정확도가 보장되지 않습니다)\n")
    while True:
        question = input("질문 입력: ").strip()
        if not question:
            print("종료합니다.")
            break

        level_input = input("  힌트 레벨 (2/3/4): ").strip()
        hint_level = int(level_input) if level_input in ("2", "3", "4") else 2

        if hint_level == 4:
            tags = []  # common_pitfalls는 태그 불필요
        else:
            print("  태그 추론 중...")
            tags = classify_tags(question)
            print(f"  -> 추론된 태그: {tags if tags else '(없음 — 20개 카테고리와 관련성 낮음)'}")
            override = input("  이 태그로 진행할까요? 다르게 하려면 직접 입력 (Enter=그대로): ").strip()
            if override:
                tags = [t.strip() for t in override.split(",") if t.strip()]

        run_one_case(client, "임의 문제", "Lv?", tags, hint_level, question)


if __name__ == "__main__":
    if "-i" in sys.argv or "--interactive" in sys.argv:
        run_interactive()
    else:
        run_batch()
