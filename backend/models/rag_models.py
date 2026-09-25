"""
Pydantic models for Phase 3 — Local RAG Engine.

Covers:
  - RAG index build request/response
  - Semantic search request/response
  - RAG ask (retrieval + Ollama) request/response
  - Daily topic retrieval request/response
  - RAG health response
"""

from typing import Any, Dict, List, Optional
from pydantic import BaseModel, Field, field_validator

from .syllabus_models import Syllabus


# ---------------------------------------------------------------------------
# Shared building block
# ---------------------------------------------------------------------------

class SourceRef(BaseModel):
    """
    Reference to the syllabus source for a retrieved chunk.
    All fields come directly from the syllabus — nothing invented.
    """
    unitNumber: int
    unitName: str
    topicName: str
    sourcePages: List[int] = Field(default_factory=list)


# ---------------------------------------------------------------------------
# Index endpoint
# ---------------------------------------------------------------------------

class RagIndexRequest(BaseModel):
    """
    Request body for POST /api/v1/rag/index.
    Takes the structured syllabus from Phase 1 and builds a local FAISS index.
    """
    syllabus: Syllabus = Field(description="Structured syllabus from Phase 1 analysis")


class RagIndexResponse(BaseModel):
    """
    Response from POST /api/v1/rag/index.
    """
    success: bool = True
    chunksIndexed: int = Field(description="Number of topic chunks embedded and indexed")
    embeddingModel: str = Field(description="Local embedding model used")
    indexPath: str = Field(description="Path where the FAISS index was saved")


# ---------------------------------------------------------------------------
# Search endpoint
# ---------------------------------------------------------------------------

class RagSearchRequest(BaseModel):
    """
    Request body for POST /api/v1/rag/search.
    Performs semantic search against the FAISS index without calling Ollama.
    """
    query: str = Field(description="Search query string", min_length=1)
    topK: int = Field(
        default=5,
        description="Number of top results to return (1–20)",
        ge=1,
        le=20,
    )

    @field_validator("query")
    @classmethod
    def query_not_blank(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("query must not be blank")
        return v.strip()


class RagSearchResult(BaseModel):
    """
    A single result from the FAISS similarity search.
    """
    score: float = Field(description="Cosine similarity score (0.0–1.0)")
    text: str = Field(description="The chunk text that was retrieved")
    metadata: Dict[str, Any] = Field(description="Full chunk metadata from the syllabus")


class RagSearchResponse(BaseModel):
    """
    Response from POST /api/v1/rag/search.
    """
    success: bool = True
    query: str
    results: List[RagSearchResult]


# ---------------------------------------------------------------------------
# Ask endpoint (RAG: search + Ollama answer)
# ---------------------------------------------------------------------------

class RagAskRequest(BaseModel):
    """
    Request body for POST /api/v1/rag/ask.
    Retrieves relevant syllabus chunks, then sends them to llama3.2:3b for a grounded answer.
    """
    query: str = Field(description="Student's question", min_length=1)
    topK: int = Field(
        default=5,
        description="Number of syllabus chunks to retrieve as context (1–20)",
        ge=1,
        le=20,
    )

    @field_validator("query")
    @classmethod
    def query_not_blank(cls, v: str) -> str:
        if not v.strip():
            raise ValueError("query must not be blank")
        return v.strip()


class RagAskResponse(BaseModel):
    """
    Response from POST /api/v1/rag/ask.
    Includes the LLM-generated answer and the syllabus sources used.
    """
    success: bool = True
    query: str
    answer: str = Field(description="Plain-text answer from llama3.2:3b grounded in syllabus")
    sources: List[SourceRef] = Field(
        description="Syllabus chunks retrieved and used as context"
    )
    chunksUsed: int = Field(description="Number of chunks used as context")


# ---------------------------------------------------------------------------
# Daily topic endpoint
# ---------------------------------------------------------------------------

class RagDailyTopicRequest(BaseModel):
    """
    Request body for POST /api/v1/rag/daily-topic.
    Retrieves pre-indexed chunks for a specific topic from the study plan.
    This powers the Android 'Learn Today's Topic' button.
    """
    topicName: str = Field(description="Exact topic name from the study plan")
    unitNumber: Optional[int] = Field(
        default=None,
        description="Unit number to narrow the search (optional)"
    )
    topK: int = Field(
        default=3,
        description="Number of related chunks to retrieve",
        ge=1,
        le=10,
    )


class RagDailyTopicResponse(BaseModel):
    """
    Response from POST /api/v1/rag/daily-topic.
    """
    success: bool = True
    topicName: str
    results: List[RagSearchResult]


# ---------------------------------------------------------------------------
# Health endpoint
# ---------------------------------------------------------------------------

class RagHealthResponse(BaseModel):
    """
    Response from GET /health/rag.
    """
    rag: str = Field(description="'ready' if index exists, 'not_indexed' otherwise")
    embeddingModel: str
    indexExists: bool
    chunksIndexed: int
    indexPath: str
