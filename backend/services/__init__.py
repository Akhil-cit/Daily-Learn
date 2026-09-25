from .pdf_extractor import extract_text_from_pdf, clean_page_text
from .image_ocr import extract_text_from_image, check_tesseract_installed
from .ollama_client import ollama_client, OllamaClient
from .syllabus_analyzer import syllabus_analyzer, SyllabusAnalyzer
from .study_planner import study_planner, StudyPlanner
from .rag_chunker import create_chunks, RagChunk
from .embedding_service import embedding_service, EmbeddingService
from .vector_store import vector_store, VectorStore, SearchHit
from .rag_service import rag_service, RagService
from .study_plan_storage import study_plan_storage, StudyPlanStorage
from .daily_learn_service import daily_learn_service, DailyLearnService

__all__ = [
    # Phase 1 — PDF/Image extraction
    "extract_text_from_pdf",
    "clean_page_text",
    "extract_text_from_image",
    "check_tesseract_installed",
    "ollama_client",
    "OllamaClient",
    "syllabus_analyzer",
    "SyllabusAnalyzer",
    # Phase 2 — Study planner
    "study_planner",
    "StudyPlanner",
    # Phase 3 — RAG
    "create_chunks",
    "RagChunk",
    "embedding_service",
    "EmbeddingService",
    "vector_store",
    "VectorStore",
    "SearchHit",
    "rag_service",
    "RagService",
    # Phase 4 — Daily Learn
    "study_plan_storage",
    "StudyPlanStorage",
    "daily_learn_service",
    "DailyLearnService",
]
