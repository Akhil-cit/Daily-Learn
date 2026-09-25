"""
Embedding Service — Phase 3.

Wraps sentence-transformers (all-MiniLM-L6-v2) with:
  - Lazy model loading (loaded once on first call, reused for all requests)
  - L2 normalization so IndexFlatIP computes cosine similarity
  - A module-level singleton instance

Model name is configurable via EMBEDDING_MODEL in .env.
"""

from typing import List, Optional
import numpy as np

from config import settings
from logger import logger


class EmbeddingService:
    """
    Local sentence-transformer embedding service.

    Responsibilities:
      - Load the configured SentenceTransformer model on first use.
      - Embed lists of texts (for indexing).
      - Embed a single query string (for search).
      - L2-normalize all vectors so cosine similarity = dot product.
    """

    def __init__(self, model_name: Optional[str] = None):
        self.model_name = model_name or settings.embedding_model
        self._model = None  # lazy-loaded

    # -----------------------------------------------------------------------
    # Internal model loader
    # -----------------------------------------------------------------------

    def _get_model(self):
        """
        Returns the loaded SentenceTransformer, loading it on first call.
        Raises RuntimeError if sentence_transformers is not installed.
        """
        if self._model is not None:
            return self._model

        try:
            from sentence_transformers import SentenceTransformer
        except ImportError:
            raise RuntimeError(
                "sentence-transformers is not installed. "
                "Run: pip install sentence-transformers"
            )

        logger.info(f"[Embed] Loading model '{self.model_name}' (first use)...")
        self._model = SentenceTransformer(self.model_name)
        dim = self._model.get_sentence_embedding_dimension()
        logger.info(f"[Embed] Model loaded. Embedding dimension: {dim}")
        return self._model

    # -----------------------------------------------------------------------
    # Public API
    # -----------------------------------------------------------------------

    def embed_texts(self, texts: List[str]) -> np.ndarray:
        """
        Embeds a list of text strings and returns an L2-normalized float32 array
        of shape (N, D) where D is the model's embedding dimension.

        Used for building the FAISS index.
        """
        if not texts:
            raise ValueError("embed_texts received an empty list")

        model = self._get_model()
        logger.info(f"[Embed] Embedding {len(texts)} chunk(s)...")
        embeddings = model.encode(
            texts,
            convert_to_numpy=True,
            show_progress_bar=False,
            normalize_embeddings=True,   # L2-normalize in-place
        ).astype(np.float32)
        logger.info(f"[Embed] Done. Shape: {embeddings.shape}")
        return embeddings

    def embed_query(self, query: str) -> np.ndarray:
        """
        Embeds a single query string and returns an L2-normalized float32 array
        of shape (1, D).

        Used for FAISS search at query time.
        """
        if not query or not query.strip():
            raise ValueError("embed_query received an empty query string")

        model = self._get_model()
        vec = model.encode(
            [query.strip()],
            convert_to_numpy=True,
            show_progress_bar=False,
            normalize_embeddings=True,
        ).astype(np.float32)
        return vec  # shape (1, D)

    def embedding_dim(self) -> int:
        """Returns the embedding dimension of the loaded model."""
        return self._get_model().get_sentence_embedding_dimension()

    def model_loaded(self) -> bool:
        """True if the model has been lazily loaded at least once."""
        return self._model is not None


# Module-level singleton — shared across all API requests
embedding_service = EmbeddingService()
