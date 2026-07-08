"""
data/ 폴더의 지식 베이스 문서를 Gemini 임베딩으로 변환해 Chroma에 저장한다.
문서 내용이 바뀌었을 때만 다시 실행하면 된다 (매번 실행할 필요 없음).

실행 전 준비:
  pip install -r requirements.txt
  .env 파일에 GEMINI_API_KEY 설정
"""

import json
import os
import time

import chromadb
from chromadb import Documents, EmbeddingFunction, Embeddings
from dotenv import load_dotenv
from google import genai
from google.genai import types
from google.genai.errors import ClientError

load_dotenv()
client_genai = genai.Client(api_key=os.environ["GEMINI_API_KEY"])


class GeminiEmbeddingFunction(EmbeddingFunction):
    """Chroma가 요구하는 EmbeddingFunction 인터페이스를 Gemini API로 구현."""

    def __init__(self, task_type: str, batch_size: int = 20, delay_sec: float = 2.0):
        # 저장할 때는 "RETRIEVAL_DOCUMENT", 질문을 검색할 때는 "RETRIEVAL_QUERY"
        # Gemini는 이 둘을 구분해서 임베딩 품질을 높인다.
        self.task_type = task_type
        # 무료 티어는 분당 요청 수가 제한되어 있어(429 RESOURCE_EXHAUSTED),
        # 한 번에 너무 많은 문서를 넣지 않고 배치로 나누어 호출 간 지연을 둔다.
        self.batch_size = batch_size
        self.delay_sec = delay_sec

    def _embed_with_retry(self, batch, max_retries=5):
        for attempt in range(max_retries):
            try:
                result = client_genai.models.embed_content(
                    model="gemini-embedding-001",
                    contents=batch,
                    config=types.EmbedContentConfig(task_type=self.task_type),
                )
                return [e.values for e in result.embeddings]
            except ClientError as e:
                if "RESOURCE_EXHAUSTED" in str(e) and attempt < max_retries - 1:
                    wait = 40  # 429 응답의 retryDelay(약 36초)보다 넉넉하게
                    print(f"    속도 제한 도달. {wait}초 대기 후 재시도 ({attempt + 1}/{max_retries})")
                    time.sleep(wait)
                else:
                    raise

    def __call__(self, input: Documents) -> Embeddings:
        all_embeddings = []
        for i in range(0, len(input), self.batch_size):
            batch = input[i : i + self.batch_size]
            all_embeddings.extend(self._embed_with_retry(batch))
            if i + self.batch_size < len(input):
                time.sleep(self.delay_sec)
        return all_embeddings


def build_knowledge_base(client: chromadb.Client):
    with open("../build/knowledge_base_chunks.json", encoding="utf-8") as f:
        chunks = json.load(f)

    collection = client.get_or_create_collection(
        name="knowledge_base",
        embedding_function=GeminiEmbeddingFunction(task_type="RETRIEVAL_DOCUMENT"),
    )

    # 레벨 정보는 여기서 저장하지 않는다. field(용도)만 저장하고,
    # "이 용도가 어느 레벨과 관련 있는지"는 검색 시점에
    # ../config/field_level_mapping.json에서 조회한다 (레벨 체계가 바뀌어도
    # 이 청크들을 다시 만들 필요가 없도록 하기 위함).
    collection.add(
        ids=[c["chunk_id"] for c in chunks],
        documents=[c["content"] for c in chunks],
        metadatas=[
            {
                "category": c["category"],
                "subcategory": c["subcategory"] or "",
                "field": c["field"],
            }
            for c in chunks
        ],
    )
    print(f"knowledge_base: {len(chunks)}개 청크 저장 완료")


def build_common_pitfalls(client: chromadb.Client):
    with open("../build/common_pitfalls_chunks.json", encoding="utf-8") as f:
        chunks = json.load(f)

    collection = client.get_or_create_collection(
        name="common_pitfalls",
        embedding_function=GeminiEmbeddingFunction(task_type="RETRIEVAL_DOCUMENT"),
    )

    metadatas = []
    for c in chunks:
        meta = {"field": c["field"]}
        for lv in (2, 3, 4):
            key = f"override_lv{lv}"
            if key in c:
                meta[key] = c[key]
        metadatas.append(meta)

    collection.add(
        ids=[c["chunk_id"] for c in chunks],
        documents=[c["content"] for c in chunks],
        metadatas=metadatas,
        # common_pitfalls는 category 필터가 없다. 레벨 관련성도
        # field_level_mapping.json(기본값) + override_lv{N}(개별 예외)를
        # 검색 시점에 조회한다.
    )
    print(f"common_pitfalls: {len(chunks)}개 청크 저장 완료")


if __name__ == "__main__":
    # 주의: 이미 chroma_db/ 폴더가 있는 상태에서 재실행하면 기존 ID와 충돌해
    # 에러가 날 수 있다. 재실행 전에는 chroma_db/ 폴더를 삭제하고 시작할 것.
    #   Windows(PowerShell): Remove-Item -Recurse -Force chroma_db
    client = chromadb.PersistentClient(path="./chroma_db")
    build_knowledge_base(client)
    build_common_pitfalls(client)
    print("인덱싱 완료. query_test.py로 검색을 테스트하세요.")
