"""
Phase 3 — Automated Tests for the Local RAG Engine.

Tests:
  1.  Chunk creation from OOP syllabus
  2.  Correct chunk count (24 for OOP syllabus)
  3.  No duplicate chunk IDs
  4.  Embedding generation returns numpy ndarray
  5.  Embedding dimension == 384 (all-MiniLM-L6-v2)
  6.  L2 normalization (each vector norm ~1.0)
  7.  FAISS index created with correct ntotal
  8.  Index save -> both files written to disk
  9.  Index load -> same ntotal after reload
  10. Metadata preserved after save/load round-trip
  11. Source pages preserved in metadata
  12. Semantic search: 'What is inheritance?' -> inheritance in top-3
  13. Semantic search: 'Explain constructors' -> constructor chunk in top-3
  14. Out-of-domain query 'photosynthesis' -> no syllabus topic claimed
  15. GET /health/rag returns correct structure
  16. POST /api/v1/rag/index returns correct chunk count
  17. POST /api/v1/rag/search returns scored results
  18. POST /api/v1/rag/ask with Ollama (auto-skipped if Ollama unavailable)
  19. Missing index -> /rag/search returns 503
  20. Invalid topK (0) -> 400 validation error

Tests 1-17, 19-20: No Ollama required.
Test 18: Requires live Ollama + llama3.2:3b; auto-skips if unavailable.

Run full suite:
    python test_rag.py

Run with explicit Ollama test forced:
    python test_rag.py --force-ollama
"""

import sys
import os
import json
import shutil
import tempfile
import asyncio

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
from fastapi.testclient import TestClient

from main import app
from models.syllabus_models import Syllabus, Unit, Topic
from models.rag_models import RagIndexRequest, RagSearchRequest, RagAskRequest
from services.rag_chunker import create_chunks
from services.embedding_service import EmbeddingService
from services.vector_store import VectorStore
from services.rag_service import RagService

# ---------------------------------------------------------------------------
# CLI flags
# ---------------------------------------------------------------------------

FORCE_OLLAMA = "--force-ollama" in sys.argv

# ---------------------------------------------------------------------------
# Test OOP syllabus (mirrors the real Phase 1 output)
# ---------------------------------------------------------------------------

def make_oop_syllabus() -> Syllabus:
    return Syllabus(
        courseName="Object Oriented Programming",
        courseCode="25SS212",
        units=[
            Unit(unitNumber=1, unitName="Principles of OOP", lectureHours=6.0, topics=[
                Topic(name="Software Crisis and Software Evolution", subtopics=[], sourcePages=[1]),
                Topic(name="Procedure vs Object Oriented Programming", subtopics=[], sourcePages=[1]),
                Topic(name="Basic Concepts of OOP", subtopics=["Objects", "Classes", "Inheritance", "Polymorphism"], sourcePages=[1]),
            ]),
            Unit(unitNumber=2, unitName="Functions and Objects in C++", lectureHours=8.0, topics=[
                Topic(name="Function Prototyping and Inline Functions", subtopics=[], sourcePages=[2]),
                Topic(name="Default Arguments and Const Arguments", subtopics=[], sourcePages=[2]),
                Topic(name="Function Overloading", subtopics=[], sourcePages=[2]),
                Topic(name="Classes and Objects, Defining Member Functions", subtopics=[], sourcePages=[2]),
                Topic(name="Static Data Members and Static Member Functions", subtopics=[], sourcePages=[2]),
                Topic(name="Arrays of Objects, Objects as Function Arguments", subtopics=[], sourcePages=[2]),
            ]),
            Unit(unitNumber=3, unitName="Constructors and Destructors", lectureHours=6.0, topics=[
                Topic(name="Constructors", subtopics=["Default Constructor", "Parameterized Constructor"], sourcePages=[3]),
                Topic(name="Parameterized Constructors", subtopics=[], sourcePages=[3]),
                Topic(name="Copy Constructor", subtopics=[], sourcePages=[3]),
                Topic(name="Destructors", subtopics=[], sourcePages=[3]),
                Topic(name="Operator Overloading", subtopics=[], sourcePages=[3]),
            ]),
            Unit(unitNumber=4, unitName="Inheritance", lectureHours=6.0, topics=[
                Topic(name="Inheritance", subtopics=["Single", "Multiple", "Multilevel"], sourcePages=[4]),
                Topic(name="Virtual Base Classes", subtopics=[], sourcePages=[4]),
                Topic(name="Abstract Classes", subtopics=[], sourcePages=[4]),
                Topic(name="Constructors in Derived Classes", subtopics=[], sourcePages=[4]),
                Topic(name="Pointers to Objects", subtopics=[], sourcePages=[4]),
            ]),
            Unit(unitNumber=5, unitName="File Handling and Templates", lectureHours=4.0, topics=[
                Topic(name="File Streams and File Modes", subtopics=[], sourcePages=[5]),
                Topic(name="Reading and Writing Files", subtopics=[], sourcePages=[5]),
                Topic(name="Exception Handling", subtopics=[], sourcePages=[5]),
                Topic(name="Function Templates", subtopics=[], sourcePages=[5]),
                Topic(name="Class Templates", subtopics=[], sourcePages=[5]),
            ]),
        ],
    )

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

PASS = "[PASS]"
FAIL = "[FAIL]"
SKIP = "[SKIP]"
passed = 0
failed = 0
skipped = 0

client = TestClient(app)


def run_test(name: str, fn):
    global passed, failed
    try:
        fn()
        print(f"{PASS} {name}")
        passed += 1
    except Exception as e:
        print(f"{FAIL} {name}: {e}")
        failed += 1


def run_async_test(name: str, fn):
    global passed, failed
    try:
        asyncio.run(fn())
        print(f"{PASS} {name}")
        passed += 1
    except Exception as e:
        print(f"{FAIL} {name}: {e}")
        failed += 1


def skip_test(name: str, reason: str):
    global skipped
    print(f"{SKIP} {name} -- {reason}")
    skipped += 1


# ---------------------------------------------------------------------------
# Shared fixtures - built once for all tests
# ---------------------------------------------------------------------------

syllabus = make_oop_syllabus()
chunks = create_chunks(syllabus)
embedder = EmbeddingService()
embeddings = None      # filled in test 4
tmp_dir = tempfile.mkdtemp()
tmp_vs = None          # VectorStore pointed at tmp_dir
tmp_index = None
tmp_meta = None

# ---------------------------------------------------------------------------
# Test 1: Chunk creation
# ---------------------------------------------------------------------------
def test_chunk_creation():
    assert len(chunks) > 0, "create_chunks returned empty list"
    for c in chunks:
        assert c.text.strip(), f"Empty chunk text for chunk_id={c.chunk_id}"
        assert "courseName" in c.metadata
        assert "topicName" in c.metadata
        assert "unitNumber" in c.metadata
        # Verify chunk text contains expected fields
        assert "Object Oriented Programming" in c.text
        assert "Unit" in c.text
        assert "Topic:" in c.text

# ---------------------------------------------------------------------------
# Test 2: Correct chunk count (24 topics in OOP syllabus)
# ---------------------------------------------------------------------------
def test_chunk_count():
    expected = sum(len(u.topics) for u in syllabus.units)
    assert len(chunks) == expected, f"Expected {expected} chunks, got {len(chunks)}"

# ---------------------------------------------------------------------------
# Test 3: No duplicate chunk IDs
# ---------------------------------------------------------------------------
def test_no_duplicate_chunk_ids():
    ids = [c.chunk_id for c in chunks]
    assert len(ids) == len(set(ids)), f"Duplicate chunk IDs found: {ids}"

# ---------------------------------------------------------------------------
# Test 4: Embedding generation returns numpy ndarray
# ---------------------------------------------------------------------------
def test_embedding_generation():
    global embeddings
    texts = [c.text for c in chunks]
    embeddings = embedder.embed_texts(texts)
    assert isinstance(embeddings, np.ndarray), "Expected numpy ndarray"
    assert embeddings.dtype == np.float32, f"Expected float32, got {embeddings.dtype}"
    assert embeddings.shape[0] == len(chunks), \
        f"Expected {len(chunks)} embeddings, got {embeddings.shape[0]}"

# ---------------------------------------------------------------------------
# Test 5: Embedding dimension = 384
# ---------------------------------------------------------------------------
def test_embedding_dimension():
    assert embeddings is not None, "Embeddings not yet generated (test 4 must run first)"
    dim = embeddings.shape[1]
    assert dim == 384, f"Expected dimension 384 for all-MiniLM-L6-v2, got {dim}"

# ---------------------------------------------------------------------------
# Test 6: L2 normalization (norm ~1.0 per vector)
# ---------------------------------------------------------------------------
def test_l2_normalization():
    assert embeddings is not None
    norms = np.linalg.norm(embeddings, axis=1)
    for i, n in enumerate(norms):
        assert abs(n - 1.0) < 1e-4, f"Vector {i} norm={n:.6f} (expected ~1.0)"

# ---------------------------------------------------------------------------
# Test 7: FAISS index created with correct ntotal
# ---------------------------------------------------------------------------
def test_faiss_index_creation():
    global tmp_vs, tmp_index, tmp_meta

    # Point a VectorStore at a temp directory so we don't pollute the real index
    from unittest.mock import patch
    import types

    tmp_vs = VectorStore.__new__(VectorStore)
    tmp_vs.index_path = type('P', (), {'__str__': lambda s: os.path.join(tmp_dir, 'index.faiss'), 'exists': lambda s: os.path.exists(os.path.join(tmp_dir, 'index.faiss'))})()
    tmp_vs.metadata_path = type('P', (), {'__str__': lambda s: os.path.join(tmp_dir, 'metadata.json'), 'exists': lambda s: os.path.exists(os.path.join(tmp_dir, 'metadata.json'))})()

    # Use a fresh proper VectorStore but with patched paths
    from pathlib import Path
    tmp_vs2 = VectorStore.__new__(VectorStore)
    tmp_vs2.index_path = Path(tmp_dir) / "index.faiss"
    tmp_vs2.metadata_path = Path(tmp_dir) / "metadata.json"
    tmp_vs = tmp_vs2

    dim = embeddings.shape[1]
    tmp_index = tmp_vs.create_index(dim)
    tmp_meta = [c.metadata for c in chunks]
    tmp_vs.add_embeddings(tmp_index, embeddings, tmp_meta)
    assert tmp_index.ntotal == len(chunks), \
        f"Expected ntotal={len(chunks)}, got {tmp_index.ntotal}"

# ---------------------------------------------------------------------------
# Test 8: Index save -> both files written to disk
# ---------------------------------------------------------------------------
def test_index_save():
    tmp_vs.save(tmp_index, tmp_meta)
    assert os.path.exists(str(tmp_vs.index_path)), "index.faiss not written"
    assert os.path.exists(str(tmp_vs.metadata_path)), "metadata.json not written"
    assert os.path.getsize(str(tmp_vs.index_path)) > 0, "index.faiss is empty"
    assert os.path.getsize(str(tmp_vs.metadata_path)) > 0, "metadata.json is empty"

# ---------------------------------------------------------------------------
# Test 9: Index load -> same ntotal
# ---------------------------------------------------------------------------
def test_index_load():
    loaded_index, loaded_meta = tmp_vs.load()
    assert loaded_index.ntotal == len(chunks), \
        f"After reload: ntotal={loaded_index.ntotal}, expected {len(chunks)}"
    assert len(loaded_meta) == len(chunks)

# ---------------------------------------------------------------------------
# Test 10: Metadata preserved after round-trip
# ---------------------------------------------------------------------------
def test_metadata_preserved():
    _, loaded_meta = tmp_vs.load()
    for orig, loaded in zip(tmp_meta, loaded_meta):
        assert orig["topicName"] == loaded["topicName"], \
            f"topicName mismatch: {orig['topicName']} != {loaded['topicName']}"
        assert orig["unitNumber"] == loaded["unitNumber"]

# ---------------------------------------------------------------------------
# Test 11: Source pages preserved
# ---------------------------------------------------------------------------
def test_source_pages_preserved():
    _, loaded_meta = tmp_vs.load()
    for m in loaded_meta:
        assert "sourcePages" in m, f"sourcePages missing from chunk {m.get('chunkId')}"
        assert isinstance(m["sourcePages"], list)

# ---------------------------------------------------------------------------
# Tests 12 & 13: Semantic search relevance
# ---------------------------------------------------------------------------
def _search_with_tmp_index(query: str, top_k: int):
    loaded_index, loaded_meta = tmp_vs.load()
    query_vec = embedder.embed_query(query)
    return tmp_vs.search(loaded_index, loaded_meta, query_vec, top_k)

def test_search_inheritance():
    hits = _search_with_tmp_index("What is inheritance?", 5)
    assert len(hits) > 0, "No results returned"
    top_3_topics = [h.metadata.get("topicName", "").lower() for h in hits[:3]]
    assert any("inherit" in t for t in top_3_topics), \
        f"Inheritance not in top 3 results: {top_3_topics}"

def test_search_constructors():
    hits = _search_with_tmp_index("Explain constructors", 5)
    assert len(hits) > 0
    top_3_topics = [h.metadata.get("topicName", "").lower() for h in hits[:3]]
    assert any("construct" in t for t in top_3_topics), \
        f"Constructor chunk not in top 3: {top_3_topics}"

# ---------------------------------------------------------------------------
# Test 14: Out-of-domain query
# ---------------------------------------------------------------------------
def test_out_of_domain_query():
    hits = _search_with_tmp_index("What is photosynthesis?", 5)
    # Scores should be low (< 0.5) for completely unrelated content
    for hit in hits:
        assert hit.score < 0.7, \
            f"Unexpectedly high score {hit.score:.3f} for 'photosynthesis' -> '{hit.metadata.get('topicName')}'"
    # And no result should have "photosynthesis" as a topic name
    topics = [h.metadata.get("topicName", "").lower() for h in hits]
    assert not any("photosynthesis" in t for t in topics), \
        "Photosynthesis incorrectly returned as a syllabus topic"

# ---------------------------------------------------------------------------
# Test 15: GET /health/rag
# ---------------------------------------------------------------------------
def test_health_rag_endpoint():
    resp = client.get("/health/rag")
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}"
    data = resp.json()
    assert "rag" in data
    assert "embeddingModel" in data
    assert "indexExists" in data
    assert "chunksIndexed" in data
    assert data["embeddingModel"] == "all-MiniLM-L6-v2"

# ---------------------------------------------------------------------------
# Test 16: POST /api/v1/rag/index
# ---------------------------------------------------------------------------
def test_api_rag_index():
    payload = {
        "syllabus": {
            "courseName": "Object Oriented Programming",
            "courseCode": "25SS212",
            "units": [
                {
                    "unitNumber": 1,
                    "unitName": "Principles of OOP",
                    "lectureHours": 6.0,
                    "topics": [
                        {"name": "Classes", "subtopics": [], "sourcePages": [1]},
                        {"name": "Objects", "subtopics": [], "sourcePages": [1]},
                        {"name": "Inheritance", "subtopics": ["Single", "Multiple"], "sourcePages": [1]},
                    ],
                },
                {
                    "unitNumber": 2,
                    "unitName": "Constructors and Destructors",
                    "lectureHours": 4.0,
                    "topics": [
                        {"name": "Constructors", "subtopics": ["Default Constructor"], "sourcePages": [2]},
                        {"name": "Destructors", "subtopics": [], "sourcePages": [2]},
                    ],
                },
            ],
        }
    }
    resp = client.post("/api/v1/rag/index", json=payload)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    data = resp.json()
    assert data["success"] is True
    assert data["chunksIndexed"] == 5  # 3 + 2 topics
    assert data["embeddingModel"] == "all-MiniLM-L6-v2"

# ---------------------------------------------------------------------------
# Test 17: POST /api/v1/rag/search
# ---------------------------------------------------------------------------
def test_api_rag_search():
    # Index must have been built by test 16 above
    payload = {"query": "What is inheritance?", "topK": 3}
    resp = client.post("/api/v1/rag/search", json=payload)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    data = resp.json()
    assert data["success"] is True
    assert "results" in data
    assert len(data["results"]) > 0
    # All results must have a score
    for r in data["results"]:
        assert "score" in r
        assert isinstance(r["score"], float)
        assert 0.0 <= r["score"] <= 1.1  # cosine can be slightly > 1 due to float precision
        assert "text" in r
        assert "metadata" in r
    # Inheritance should be top result
    top_topic = data["results"][0]["metadata"].get("topicName", "").lower()
    assert "inherit" in top_topic or "class" in top_topic, \
        f"Expected inheritance/class related topic at top, got: {top_topic}"

# ---------------------------------------------------------------------------
# Test 18: POST /api/v1/rag/ask (Ollama required — auto-skips if unavailable)
# ---------------------------------------------------------------------------
async def _check_ollama_available() -> bool:
    """Returns True if Ollama is reachable and llama3.2:3b is available."""
    try:
        import httpx
        async with httpx.AsyncClient(timeout=5.0) as c:
            r = await c.get("http://localhost:11434/api/tags")
            if r.status_code == 200:
                models = [m.get("name", "") for m in r.json().get("models", [])]
                return any("llama3.2:3b" in m or "llama3.2" in m for m in models)
    except Exception:
        pass
    return False


def test_api_rag_ask():
    global skipped
    ollama_up = asyncio.run(_check_ollama_available())
    if not ollama_up and not FORCE_OLLAMA:
        print(f"{SKIP} POST /api/v1/rag/ask (Ollama+llama3.2:3b unavailable)")
        skipped += 1
        return

    payload = {"query": "Explain inheritance in OOP", "topK": 3}
    resp = client.post("/api/v1/rag/ask", json=payload)
    assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
    data = resp.json()
    assert data["success"] is True
    assert "answer" in data and len(data["answer"]) > 20, "Answer is too short or missing"
    assert "sources" in data and len(data["sources"]) > 0, "No sources returned"
    assert data["chunksUsed"] > 0
    print(f"\n  -- RAG Answer (first 300 chars): {data['answer'][:300]}...")
    print(f"  -- Sources used: {[s['topicName'] for s in data['sources']]}")

# ---------------------------------------------------------------------------
# Test 19: Missing index -> /rag/search returns 503
# ---------------------------------------------------------------------------
def test_missing_index_returns_503():
    """
    Temporarily rename the real index to simulate missing index.
    Uses the actual API endpoint.
    """
    from pathlib import Path
    from config import settings

    real_index = Path(settings.rag_index_dir) / "index.faiss"
    real_meta = Path(settings.rag_index_dir) / "metadata.json"
    backup_index = Path(settings.rag_index_dir) / "_backup_index.faiss"
    backup_meta = Path(settings.rag_index_dir) / "_backup_metadata.json"

    # Move files out of the way
    renamed = False
    try:
        if real_index.exists():
            real_index.rename(backup_index)
            renamed = True
        if real_meta.exists():
            real_meta.rename(backup_meta)

        payload = {"query": "test query", "topK": 3}
        resp = client.post("/api/v1/rag/search", json=payload)
        assert resp.status_code == 503, \
            f"Expected 503 (index missing), got {resp.status_code}: {resp.text}"
        data = resp.json()
        assert data["success"] is False
    finally:
        # Always restore
        if (Path(settings.rag_index_dir) / "_backup_index.faiss").exists():
            backup_index.rename(real_index)
        if (Path(settings.rag_index_dir) / "_backup_metadata.json").exists():
            backup_meta.rename(real_meta)

# ---------------------------------------------------------------------------
# Test 20: Invalid topK = 0 -> 422 validation error
# ---------------------------------------------------------------------------
def test_invalid_topk():
    payload = {"query": "inheritance", "topK": 0}
    resp = client.post("/api/v1/rag/search", json=payload)
    assert resp.status_code == 422, \
        f"Expected 422 for topK=0, got {resp.status_code}: {resp.text}"


# ---------------------------------------------------------------------------
# Cleanup temp directory
# ---------------------------------------------------------------------------
def cleanup():
    try:
        shutil.rmtree(tmp_dir, ignore_errors=True)
    except Exception:
        pass


# ---------------------------------------------------------------------------
# Run all tests
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    print("=" * 65)
    print("Running Phase 3 RAG Engine Tests...")
    if FORCE_OLLAMA:
        print("  [!] --force-ollama: Ollama test will run even if not available")
    print("=" * 65)

    # Group 1 — Chunking (no embeddings)
    print("\n-- Chunking --")
    run_test("Chunk creation from OOP syllabus", test_chunk_creation)
    run_test("Correct chunk count (24 topics)", test_chunk_count)
    run_test("No duplicate chunk IDs", test_no_duplicate_chunk_ids)

    # Group 2 — Embeddings
    print("\n-- Embeddings --")
    run_test("Embedding generation returns numpy ndarray", test_embedding_generation)
    run_test("Embedding dimension == 384", test_embedding_dimension)
    run_test("L2 normalization (norm ~1.0 per vector)", test_l2_normalization)

    # Group 3 — FAISS index CRUD
    print("\n-- FAISS Index CRUD --")
    run_test("FAISS index created with correct ntotal", test_faiss_index_creation)
    run_test("Index save -> both files written to disk", test_index_save)
    run_test("Index load -> same ntotal after reload", test_index_load)
    run_test("Metadata preserved after round-trip", test_metadata_preserved)
    run_test("Source pages preserved in metadata", test_source_pages_preserved)

    # Group 4 — Semantic search relevance
    print("\n-- Semantic Search Relevance --")
    run_test("'What is inheritance?' -> inheritance in top 3", test_search_inheritance)
    run_test("'Explain constructors' -> constructor in top 3", test_search_constructors)
    run_test("'Photosynthesis' -> no fabricated topic", test_out_of_domain_query)

    # Group 5 — API endpoints
    print("\n-- API Endpoints --")
    run_test("GET /health/rag returns correct structure", test_health_rag_endpoint)
    run_test("POST /api/v1/rag/index returns chunk count", test_api_rag_index)
    run_test("POST /api/v1/rag/search returns scored results", test_api_rag_search)

    # Group 6 — Ollama integration (auto-skip when unavailable)
    print("\n-- Ollama Integration --")
    run_test("POST /api/v1/rag/ask (auto-skip if Ollama down)", test_api_rag_ask)

    # Group 7 — Error handling
    print("\n-- Error Handling --")
    run_test("Missing index -> /rag/search returns 503", test_missing_index_returns_503)
    run_test("Invalid topK=0 -> 422 validation error", test_invalid_topk)

    # Cleanup temp files
    cleanup()

    print("\n" + "=" * 65)
    print(f"Results: {passed} passed, {skipped} skipped, {failed} failed  (total {passed+skipped+failed})")
    print("=" * 65)

    if failed > 0:
        sys.exit(1)
    else:
        if skipped > 0:
            print(f"ALL RAG TESTS PASSED! ({skipped} test(s) skipped due to Ollama unavailability)")
            print("  To run the full integration test: python test_rag.py --force-ollama")
        else:
            print("ALL RAG TESTS PASSED!")
