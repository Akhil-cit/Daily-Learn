"""
RAG Service — Phase 3.

Orchestrates the full local RAG pipeline:

  Index:
    Syllabus → RagChunks → Embeddings → FAISS index → disk

  Search:
    Query → Embedding → FAISS → SearchHits

  Ask (full RAG):
    Query → Embedding → FAISS → Top-K chunks → RAG prompt → Ollama → Answer

  Daily Topic:
    Topic name → Query → FAISS → Matching chunks

Uses:
  - services/rag_chunker.py      (no LLM, pure Python)
  - services/embedding_service.py (local sentence-transformers)
  - services/vector_store.py      (local FAISS)
  - services/ollama_client.py     (existing — reused, NOT duplicated)
  - prompts/rag_prompt.txt        (editable template)

No LangChain. No LlamaIndex. No cloud APIs.
"""

from pathlib import Path
from typing import List

from config import BASE_DIR, settings
from logger import logger
from models.syllabus_models import Syllabus
from models.rag_models import RagAskResponse, SourceRef, RagSearchResult
from services.rag_chunker import create_chunks, RagChunk
from services.embedding_service import embedding_service
from services.vector_store import vector_store, SearchHit
from services.ollama_client import ollama_client


# ---------------------------------------------------------------------------
# RAG prompt loader
# ---------------------------------------------------------------------------

RAG_PROMPT_PATH = BASE_DIR / "prompts" / "rag_prompt.txt"


def _load_rag_prompt() -> str:
    """Loads the editable RAG system prompt from prompts/rag_prompt.txt."""
    if not RAG_PROMPT_PATH.exists():
        raise FileNotFoundError(
            f"RAG prompt file not found: {RAG_PROMPT_PATH}. "
            "Please ensure prompts/rag_prompt.txt exists."
        )
    return RAG_PROMPT_PATH.read_text(encoding="utf-8").strip()


# ---------------------------------------------------------------------------
# RAG Service
# ---------------------------------------------------------------------------

class RagService:
    """
    Orchestrates the full RAG pipeline.

    All external service calls are delegated to dedicated services:
      - rag_chunker: Syllabus → chunks
      - embedding_service: text → vectors
      - vector_store: FAISS CRUD
      - ollama_client: LLM answers (reused from Phase 1)
    """

    # -----------------------------------------------------------------------
    # Index build
    # -----------------------------------------------------------------------

    def index_syllabus(self, syllabus: Syllabus) -> int:
        """
        Converts a syllabus into chunks, embeds them, builds a FAISS index,
        and saves both the index and metadata to disk.

        Returns the number of chunks indexed.
        Raises ValueError if the syllabus has no indexable topics.
        """
        logger.info(f"[RAG] Indexing syllabus: '{syllabus.courseName}'")

        # Step 1 — Create chunks
        chunks: List[RagChunk] = create_chunks(syllabus)
        if not chunks:
            raise ValueError(
                "The syllabus has no topics to index. "
                "Please upload a syllabus with at least one unit and topic."
            )
        logger.info(f"[RAG] Created {len(chunks)} chunks from syllabus")

        # Step 2 — Generate embeddings
        texts = [c.text for c in chunks]
        embeddings = embedding_service.embed_texts(texts)

        # Step 3 — Build FAISS index
        dim = embeddings.shape[1]
        index = vector_store.create_index(dim)
        metadata_list = [c.metadata for c in chunks]
        vector_store.add_embeddings(index, embeddings, metadata_list)

        # Step 4 — Save to disk
        vector_store.save(index, metadata_list)

        logger.info(f"[RAG] Indexing complete: {len(chunks)} chunks saved to {vector_store.index_path}")
        return len(chunks)

    # -----------------------------------------------------------------------
    # Semantic search (no LLM)
    # -----------------------------------------------------------------------

    def search(self, query: str, top_k: int) -> List[SearchHit]:
        """
        Embeds the query and performs a FAISS similarity search.
        Returns up to top_k SearchHit objects.
        Raises FileNotFoundError if the index has not been built yet.
        """
        logger.info(f"[RAG] Search: '{query}' (topK={top_k})")

        # Load index
        index, metadata = vector_store.load()

        # Embed query
        query_vec = embedding_service.embed_query(query)

        # FAISS search
        hits = vector_store.search(index, metadata, query_vec, top_k)
        logger.info(f"[RAG] Found {len(hits)} result(s) for query: '{query}'")
        return hits

    # -----------------------------------------------------------------------
    # Full RAG ask (search + Ollama answer)
    # -----------------------------------------------------------------------

    async def ask(self, query: str, top_k: int) -> RagAskResponse:
        """
        Full RAG pipeline:
          1. Embed query
          2. FAISS search → top-K chunks
          3. Build context from chunk texts
          4. Fill RAG prompt template
          5. Send to llama3.2:3b via existing ollama_client (plain text, not JSON)
          6. Return grounded answer + source metadata

        The Ollama call uses format="" (plain text) at temperature=0.3
        so the model writes a natural explanation, not a JSON blob.
        """
        logger.info(f"[RAG] Ask: '{query}' (topK={top_k})")

        # Step 1+2 — Search
        hits = self.search(query, top_k)

        if not hits:
            return RagAskResponse(
                success=True,
                query=query,
                answer="The syllabus context does not contain enough information about this topic.",
                sources=[],
                chunksUsed=0,
            )

        # Step 3 — Build context block
        context_parts = []
        for i, hit in enumerate(hits, 1):
            context_parts.append(f"[Chunk {i}]\n{hit.text}")
        context = "\n\n".join(context_parts)

        # Step 4 — Fill prompt template
        prompt_template = _load_rag_prompt()
        filled_prompt = prompt_template.replace("{context}", context).replace("{question}", query)

        # Step 5 — Call Ollama (plain text, NOT JSON mode)
        # Reuses the existing ollama_client instance from Phase 1.
        # format="" disables Ollama's JSON output constraint.
        messages = [
            {"role": "user", "content": filled_prompt}
        ]
        logger.info(f"[RAG] Sending to Ollama (plain text mode, {len(hits)} chunks as context)")
        raw_answer = await ollama_client.chat(
            messages=messages,
            format="",      # plain text answer, not JSON
            temperature=0.3,
        )
        answer = raw_answer.strip()

        # Step 6 — Build source references
        sources = []
        seen_sources = set()
        for hit in hits:
            m = hit.metadata
            key = f"{m.get('unitNumber')}::{m.get('topicName')}"
            if key not in seen_sources:
                sources.append(SourceRef(
                    unitNumber=m.get("unitNumber", 0),
                    unitName=m.get("unitName", ""),
                    topicName=m.get("topicName", ""),
                    sourcePages=m.get("sourcePages", []),
                ))
                seen_sources.add(key)

        logger.info(f"[RAG] Answer generated ({len(answer)} chars, {len(sources)} sources)")
        return RagAskResponse(
            success=True,
            query=query,
            answer=answer,
            sources=sources,
            chunksUsed=len(hits),
        )

    # -----------------------------------------------------------------------
    # Daily topic retrieval
    # -----------------------------------------------------------------------

    def get_daily_topic_chunks(
        self,
        topic_name: str,
        unit_number: int | None,
        top_k: int,
    ) -> List[SearchHit]:
        """
        Retrieves chunks related to a specific study-plan topic.

        Strategy:
          1. Build a descriptive query from the topic name (and unit if given).
          2. Perform FAISS search.
          3. Optionally filter results to the specified unit.

        Powers the Android 'Learn Today's Topic' button.
        """
        query = topic_name
        if unit_number is not None:
            query = f"Unit {unit_number}: {topic_name}"

        logger.info(f"[RAG] DailyTopic: '{topic_name}' (unit={unit_number})")
        hits = self.search(query, top_k=max(top_k * 2, 10))

        # Prefer exact match on topicName, then fall back to semantic results
        exact = [h for h in hits if h.metadata.get("topicName", "").lower() == topic_name.lower()]
        if exact:
            return exact[:top_k]

        # If unit_number given, filter to that unit
        if unit_number is not None:
            filtered = [h for h in hits if h.metadata.get("unitNumber") == unit_number]
            if filtered:
                return filtered[:top_k]

        return hits[:top_k]

    # -----------------------------------------------------------------------
    # Status helpers
    # -----------------------------------------------------------------------

    def is_indexed(self) -> bool:
        return vector_store.exists()

    def chunk_count(self) -> int:
        return vector_store.chunk_count()


# Module-level singleton
rag_service = RagService()
