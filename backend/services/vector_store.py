"""
Vector Store — Phase 3.

Wraps FAISS IndexFlatIP (inner product on L2-normalized vectors = cosine similarity)
with full CRUD: create, add, save, load, search.

Index path:    data/rag/index.faiss
Metadata path: data/rag/metadata.json

The metadata JSON stores a list of dicts — one per chunk — in the same order
as the FAISS index rows. Retrieval is done by mapping FAISS result indices back
to this list.
"""

import json
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Tuple

import numpy as np

from config import settings
from logger import logger


# ---------------------------------------------------------------------------
# Search hit
# ---------------------------------------------------------------------------

@dataclass
class SearchHit:
    """
    A single result returned by VectorStore.search().
    'text' comes from the 'text' key stored in the chunk metadata.
    """
    score: float
    text: str
    metadata: dict


# ---------------------------------------------------------------------------
# Vector Store
# ---------------------------------------------------------------------------

class VectorStore:
    """
    FAISS-backed vector store for RAG chunk retrieval.

    Cosine similarity is achieved by:
      1. L2-normalizing all vectors before insertion (done in EmbeddingService).
      2. Using IndexFlatIP (inner product index).
      For unit vectors: inner_product == cosine_similarity.
    """

    def __init__(self):
        index_dir = Path(settings.rag_index_dir)
        self.index_path = index_dir / "index.faiss"
        self.metadata_path = index_dir / "metadata.json"
        index_dir.mkdir(parents=True, exist_ok=True)

    # -----------------------------------------------------------------------
    # Existence checks
    # -----------------------------------------------------------------------

    def exists(self) -> bool:
        """Returns True only if BOTH the index file and metadata file exist."""
        return self.index_path.exists() and self.metadata_path.exists()

    def chunk_count(self) -> int:
        """
        Returns the number of indexed chunks by reading metadata.json.
        Does NOT load the FAISS index (fast).
        Returns 0 if no index exists.
        """
        if not self.metadata_path.exists():
            return 0
        try:
            data = json.loads(self.metadata_path.read_text(encoding="utf-8"))
            return len(data)
        except Exception:
            return 0

    # -----------------------------------------------------------------------
    # Build
    # -----------------------------------------------------------------------

    def create_index(self, dim: int):
        """
        Creates a new empty FAISS IndexFlatIP of the given dimension.
        Requires faiss-cpu to be installed.
        """
        try:
            import faiss
        except ImportError:
            raise RuntimeError(
                "faiss-cpu is not installed. Run: pip install faiss-cpu"
            )
        index = faiss.IndexFlatIP(dim)
        logger.info(f"[VectorStore] Created IndexFlatIP (dim={dim})")
        return index

    def add_embeddings(self, index, embeddings: np.ndarray, metadata: List[dict]) -> None:
        """
        Adds L2-normalized embeddings and their metadata to the index.
        Both lists must be the same length.
        """
        if len(embeddings) != len(metadata):
            raise ValueError(
                f"Embedding count ({len(embeddings)}) != metadata count ({len(metadata)})"
            )
        index.add(embeddings)
        logger.info(f"[VectorStore] Added {len(embeddings)} vectors. Index total: {index.ntotal}")

    # -----------------------------------------------------------------------
    # Persistence
    # -----------------------------------------------------------------------

    def save(self, index, metadata: List[dict]) -> None:
        """
        Saves the FAISS index to index.faiss and metadata list to metadata.json.
        """
        import faiss
        faiss.write_index(index, str(self.index_path))
        self.metadata_path.write_text(
            json.dumps(metadata, ensure_ascii=False, indent=2),
            encoding="utf-8"
        )
        logger.info(
            f"[VectorStore] Saved index ({index.ntotal} vectors) → {self.index_path}"
        )
        logger.info(f"[VectorStore] Saved metadata ({len(metadata)} entries) → {self.metadata_path}")

    def load(self) -> Tuple:
        """
        Loads the FAISS index and metadata from disk.
        Returns (index, metadata_list).
        Raises FileNotFoundError if either file is missing.
        """
        import faiss
        if not self.exists():
            raise FileNotFoundError(
                f"RAG index not found. Please index a syllabus first via "
                f"POST /api/v1/rag/index. "
                f"(expected: {self.index_path})"
            )
        index = faiss.read_index(str(self.index_path))
        metadata = json.loads(self.metadata_path.read_text(encoding="utf-8"))
        logger.info(f"[VectorStore] Loaded index ({index.ntotal} vectors) from {self.index_path}")
        return index, metadata

    # -----------------------------------------------------------------------
    # Search
    # -----------------------------------------------------------------------

    def search(
        self,
        index,
        metadata: List[dict],
        query_vec: np.ndarray,
        top_k: int,
    ) -> List[SearchHit]:
        """
        Performs a cosine similarity search against the loaded index.

        query_vec: shape (1, D), L2-normalized
        Returns up to top_k SearchHit objects sorted by descending score.
        """
        actual_k = min(top_k, index.ntotal)
        if actual_k == 0:
            return []

        scores, indices = index.search(query_vec, actual_k)

        hits: List[SearchHit] = []
        for score, idx in zip(scores[0], indices[0]):
            if idx < 0 or idx >= len(metadata):
                continue  # FAISS returns -1 for padding
            chunk_meta = metadata[idx]
            hits.append(SearchHit(
                score=float(round(score, 4)),
                text=chunk_meta.get("text", ""),
                metadata=chunk_meta,
            ))

        # Already sorted by FAISS (descending score), but sort again to be safe
        hits.sort(key=lambda h: h.score, reverse=True)
        return hits


# Module-level singleton
vector_store = VectorStore()
