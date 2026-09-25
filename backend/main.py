import os
import shutil
import uuid
from pathlib import Path
from fastapi import FastAPI, UploadFile, File, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from config import settings
from logger import logger
from models.syllabus_models import (
    SyllabusAnalysisResponse,
    ErrorResponse,
    HealthResponse,
    OllamaHealthResponse,
    OcrHealthResponse,
)
from models.study_plan_models import (
    StudyPlanRequest,
    StudyPlanResponse,
    StudyPlanHealthResponse,
)
from models.rag_models import (
    RagIndexRequest,
    RagIndexResponse,
    RagSearchRequest,
    RagSearchResponse,
    RagSearchResult,
    RagAskRequest,
    RagAskResponse,
    RagDailyTopicRequest,
    RagDailyTopicResponse,
    RagHealthResponse,
)
from models.daily_learn_models import (
    DailyLearnRequest,
    DailyLearnResponse,
)
from services.pdf_extractor import extract_text_from_pdf
from services.image_ocr import extract_text_from_image, check_tesseract_installed
from services.ollama_client import ollama_client
from services.syllabus_analyzer import syllabus_analyzer
from services.study_planner import study_planner
from services.study_plan_storage import study_plan_storage
from services.rag_service import rag_service
from services.embedding_service import embedding_service
from services.daily_learn_service import daily_learn_service


app = FastAPI(
    title="Daily Learn - Local AI Syllabus Engine",
    description=(
        "Phase 1: PDF/Image → Structured Syllabus JSON (local OCR + Ollama).\n"
        "Phase 2: Structured Syllabus + targetDays → Deterministic Daily Study Plan."
    ),
    version="2.0.0"
)

# Enable CORS for local Android development and frontend clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

SUPPORTED_EXTENSIONS = {".pdf", ".jpg", ".jpeg", ".png"}


@app.get("/health", response_model=HealthResponse, tags=["Health"])
async def health_check():
    """
    Standard server health check.
    """
    return HealthResponse(status="ok")


@app.get("/health/ollama", response_model=OllamaHealthResponse, tags=["Health"])
async def ollama_health_check():
    """
    Checks if local Ollama daemon is reachable and whether the configured model is available.
    """
    return await ollama_client.check_health()


@app.get("/health/ocr", response_model=OcrHealthResponse, tags=["Health"])
async def ocr_health_check():
    """
    Checks if local Tesseract OCR engine is installed and available.
    """
    res = check_tesseract_installed()
    return OcrHealthResponse(
        ocr="available" if res["installed"] else "unavailable",
        installed=res["installed"],
        path=res.get("path"),
        version=res.get("version"),
        error=res.get("error")
    )


@app.post(
    "/api/v1/syllabus/analyze",
    response_model=SyllabusAnalysisResponse,
    summary="Analyze Syllabus Document",
    description="Analyze a syllabus PDF or image using local extraction/OCR and local Ollama.",
    responses={
        400: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
        502: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
        504: {"model": ErrorResponse},
    },
    tags=["Syllabus Analysis"]
)
async def analyze_syllabus_endpoint(file: UploadFile = File(...)):
    """
    Analyze a syllabus PDF or image (JPG, JPEG, PNG) using local extraction/OCR and local Ollama.
    """
    # 1. Validate file extension
    filename = file.filename or "unknown"
    ext = Path(filename).suffix.lower()

    if ext not in SUPPORTED_EXTENSIONS:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid file format. Supported formats: PDF, JPG, JPEG, PNG."
        )

    # 2. Save uploaded file to temporary path
    unique_id = uuid.uuid4().hex[:8]
    safe_filename = f"{unique_id}_{filename}"
    temp_file_path = Path(settings.upload_dir) / safe_filename

    try:
        # Stream file to disk while checking file size
        max_bytes = settings.max_upload_size_mb * 1024 * 1024
        file_size = 0

        with open(temp_file_path, "wb") as buffer:
            while chunk := await file.read(1024 * 1024):  # 1MB chunks
                file_size += len(chunk)
                if file_size > max_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_400_BAD_REQUEST,
                        detail=f"File exceeds maximum allowed size of {settings.max_upload_size_mb} MB."
                    )
                buffer.write(chunk)

        logger.info(f"[API] Uploaded '{filename}' ({ext}) saved to '{temp_file_path}' ({file_size} bytes)")

        # 3. Extract text according to file type
        if ext == ".pdf":
            pages = extract_text_from_pdf(temp_file_path, source_name=filename)
        else:
            # JPG, JPEG, PNG image files
            pages = extract_text_from_image(temp_file_path, source_name=filename)

        if not pages or all(not p.text.strip() for p in pages):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Could not extract readable text from the document. The file may be blank or poor quality."
            )

        # 4. Analyze syllabus with local Ollama
        syllabus = await syllabus_analyzer.analyze(pages)

        return SyllabusAnalysisResponse(success=True, data=syllabus)

    except HTTPException:
        raise
    except ConnectionError as ce:
        logger.error(f"[API] Ollama connection error: {str(ce)}")
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=ErrorResponse(
                success=False,
                error="Could not connect to local Ollama. Please ensure Ollama is running ('ollama serve').",
                details=str(ce)
            ).model_dump()
        )
    except TimeoutError as te:
        logger.error(f"[API] Ollama timeout error: {str(te)}")
        return JSONResponse(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            content=ErrorResponse(
                success=False,
                error="Ollama request timed out while processing syllabus.",
                details=str(te)
            ).model_dump()
        )
    except RuntimeError as re:
        logger.error(f"[API] Runtime error (e.g. OCR missing): {str(re)}")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(
                success=False,
                error=str(re)
            ).model_dump()
        )
    except ValueError as ve:
        logger.error(f"[API] Validation/Parsing error: {str(ve)}")
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=ErrorResponse(
                success=False,
                error=str(ve)
            ).model_dump()
        )
    except Exception as e:
        logger.error(f"[API] Unexpected error: {str(e)}", exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=ErrorResponse(
                success=False,
                error="An unexpected internal server error occurred while processing the syllabus.",
                details=str(e)
            ).model_dump()
        )
    finally:
        # Clean up temporary uploaded file
        if temp_file_path.exists():
            try:
                temp_file_path.unlink()
            except Exception as e:
                logger.warning(f"[API] Could not delete temporary file {temp_file_path}: {str(e)}")


# ---------------------------------------------------------------------------
# Phase 2 — Study Plan Endpoints
# ---------------------------------------------------------------------------

@app.get(
    "/api/v1/study-plan/health",
    response_model=StudyPlanHealthResponse,
    summary="Study Plan Scheduler Health",
    description="Confirms the deterministic study plan scheduler is available.",
    tags=["Study Plan"],
)
async def study_plan_health():
    """
    Returns the scheduler type and version.
    No external dependencies — always succeeds if the server is running.
    """
    return StudyPlanHealthResponse(
        status="ok",
        scheduler="deterministic-python",
        version="2.0.0",
    )


@app.post(
    "/api/v1/study-plan/generate",
    response_model=StudyPlanResponse,
    summary="Generate Daily Study Plan",
    description=(
        "Convert a structured syllabus (from Phase 1) into a Day 1 → Day N "
        "deterministic study plan. No LLM is involved in scheduling — the "
        "algorithm is pure Python and uses only the extracted syllabus data."
    ),
    responses={
        400: {"model": ErrorResponse},
        422: {"description": "Validation error (malformed request body)"},
        500: {"model": ErrorResponse},
    },
    tags=["Study Plan"],
)
async def generate_study_plan(request: StudyPlanRequest):
    """
    Generate a deterministic daily study plan from structured syllabus data.

    - **syllabus**: Phase 1 output (structured syllabus JSON)
    - **targetDays**: Total days until exam (required, >= 1)
    - **revisionBuffer**: Fraction of days reserved for revision (default 15%)
    - **startDate**: Optional ISO date to attach calendar dates per day
    - **examDate**: Optional ISO exam date (stored, not yet used for scheduling)
    """
    try:
        result = study_planner.generate(request)
        # Phase 4: Save the plan to local JSON storage for Daily Learn retrieval
        study_plan_storage.save_plan(result)
        return StudyPlanResponse(success=True, studyPlan=result)

    except ValueError as ve:
        logger.error(f"[API] Study plan validation error: {ve}")
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=ErrorResponse(
                success=False,
                error=str(ve)
            ).model_dump(),
        )
    except AssertionError as ae:
        # Scheduler invariant violated — this is an algorithm bug, not user error
        logger.error(f"[API] Study plan scheduling invariant failed: {ae}", exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=ErrorResponse(
                success=False,
                error="Internal scheduling error. Please report this issue.",
                details=str(ae),
            ).model_dump(),
        )
    except Exception as e:
        logger.error(f"[API] Unexpected study plan error: {e}", exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=ErrorResponse(
                success=False,
                error="An unexpected error occurred while generating the study plan.",
                details=str(e),
            ).model_dump(),
        )


# ---------------------------------------------------------------------------
# Phase 3 — RAG Endpoints
# ---------------------------------------------------------------------------

@app.get(
    "/health/rag",
    response_model=RagHealthResponse,
    summary="RAG Engine Health",
    description="Returns local FAISS index status and embedding model information.",
    tags=["Health"],
)
async def rag_health():
    """
    Reports whether the RAG index has been built and how many chunks are indexed.
    Does NOT require Ollama to be running.
    """
    exists = rag_service.is_indexed()
    count = rag_service.chunk_count()
    from services.vector_store import vector_store as vs
    return RagHealthResponse(
        rag="ready" if exists else "not_indexed",
        embeddingModel=settings.embedding_model,
        indexExists=exists,
        chunksIndexed=count,
        indexPath=str(vs.index_path),
    )


@app.post(
    "/api/v1/rag/index",
    response_model=RagIndexResponse,
    summary="Build RAG Index",
    description=(
        "Convert a structured syllabus (Phase 1 output) into local embeddings "
        "and build a FAISS cosine-similarity index. "
        "Call this once per syllabus; subsequent questions reuse the saved index."
    ),
    responses={
        400: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
    },
    tags=["RAG"],
)
async def rag_index(request: RagIndexRequest):
    """
    Build the local RAG index:
    - **syllabus**: Structured syllabus from POST /api/v1/syllabus/analyze
    """
    try:
        chunks_indexed = rag_service.index_syllabus(request.syllabus)
        from services.vector_store import vector_store as vs
        return RagIndexResponse(
            success=True,
            chunksIndexed=chunks_indexed,
            embeddingModel=settings.embedding_model,
            indexPath=str(vs.index_path),
        )
    except ValueError as ve:
        logger.error(f"[API] RAG index error: {ve}")
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content=ErrorResponse(success=False, error=str(ve)).model_dump(),
        )
    except RuntimeError as re:
        logger.error(f"[API] RAG index runtime error: {re}")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(success=False, error=str(re)).model_dump(),
        )
    except Exception as e:
        logger.error(f"[API] RAG index unexpected error: {e}", exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=ErrorResponse(
                success=False,
                error="Unexpected error building RAG index.",
                details=str(e),
            ).model_dump(),
        )


@app.post(
    "/api/v1/rag/search",
    response_model=RagSearchResponse,
    summary="Semantic Syllabus Search",
    description=(
        "Perform a local semantic search against the FAISS index. "
        "Returns ranked syllabus chunks with cosine similarity scores. "
        "No Ollama call is made — pure vector retrieval only."
    ),
    responses={
        400: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
    },
    tags=["RAG"],
)
async def rag_search(request: RagSearchRequest):
    """
    Semantic search:
    - **query**: Student's search string
    - **topK**: Number of results (1–20, default 5)
    """
    try:
        hits = rag_service.search(request.query, request.topK)
        results = [
            RagSearchResult(
                score=h.score,
                text=h.text,
                metadata=h.metadata,
            )
            for h in hits
        ]
        return RagSearchResponse(
            success=True,
            query=request.query,
            results=results,
        )
    except FileNotFoundError as fe:
        logger.error(f"[API] RAG search: index not found: {fe}")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(
                success=False,
                error="RAG index not found. Please build the index first via POST /api/v1/rag/index.",
            ).model_dump(),
        )
    except RuntimeError as re:
        logger.error(f"[API] RAG search runtime error: {re}")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(success=False, error=str(re)).model_dump(),
        )
    except Exception as e:
        logger.error(f"[API] RAG search unexpected error: {e}", exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=ErrorResponse(
                success=False,
                error="Unexpected error during semantic search.",
                details=str(e),
            ).model_dump(),
        )


@app.post(
    "/api/v1/rag/ask",
    response_model=RagAskResponse,
    summary="RAG Ask (Retrieval + Ollama Answer)",
    description=(
        "Full RAG pipeline: embed query → FAISS search → retrieve top-K syllabus chunks → "
        "build grounded context → llama3.2:3b answer. "
        "Answer is grounded in the indexed syllabus; the model cannot fabricate topics."
    ),
    responses={
        400: {"model": ErrorResponse},
        502: {"model": ErrorResponse},
        503: {"model": ErrorResponse},
        504: {"model": ErrorResponse},
    },
    tags=["RAG"],
)
async def rag_ask(request: RagAskRequest):
    """
    Full RAG answer:
    - **query**: Student question (e.g. 'Explain inheritance')
    - **topK**: Chunks to retrieve as context (default 5)
    """
    try:
        response = await rag_service.ask(request.query, request.topK)
        return response
    except FileNotFoundError as fe:
        logger.error(f"[API] RAG ask: index not found: {fe}")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(
                success=False,
                error="RAG index not found. Please build the index first via POST /api/v1/rag/index.",
            ).model_dump(),
        )
    except ConnectionError as ce:
        logger.error(f"[API] RAG ask: Ollama unreachable: {ce}")
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=ErrorResponse(
                success=False,
                error="Could not connect to Ollama. Please ensure Ollama is running ('ollama serve').",
                details=str(ce),
            ).model_dump(),
        )
    except TimeoutError as te:
        logger.error(f"[API] RAG ask: Ollama timeout: {te}")
        return JSONResponse(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            content=ErrorResponse(
                success=False,
                error="Ollama timed out while generating the RAG answer.",
                details=str(te),
            ).model_dump(),
        )
    except RuntimeError as re:
        logger.error(f"[API] RAG ask runtime error: {re}")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(success=False, error=str(re)).model_dump(),
        )
    except Exception as e:
        logger.error(f"[API] RAG ask unexpected error: {e}", exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=ErrorResponse(
                success=False,
                error="Unexpected error during RAG answer generation.",
                details=str(e),
            ).model_dump(),
        )


@app.post(
    "/api/v1/rag/daily-topic",
    response_model=RagDailyTopicResponse,
    summary="Retrieve Daily Topic Chunks",
    description=(
        "Retrieve pre-indexed syllabus chunks for a specific study-plan topic. "
        "Powers the Android 'Learn Today's Topic' button."
    ),
    responses={
        503: {"model": ErrorResponse},
    },
    tags=["RAG"],
)
async def rag_daily_topic(request: RagDailyTopicRequest):
    """
    Retrieve chunks for today's study topic:
    - **topicName**: Exact topic name from the study plan
    - **unitNumber**: Optional unit number to narrow search
    - **topK**: Number of results (default 3)
    """
    try:
        hits = rag_service.get_daily_topic_chunks(
            topic_name=request.topicName,
            unit_number=request.unitNumber,
            top_k=request.topK,
        )
        results = [
            RagSearchResult(
                score=h.score,
                text=h.text,
                metadata=h.metadata,
            )
            for h in hits
        ]
        return RagDailyTopicResponse(
            success=True,
            topicName=request.topicName,
            results=results,
        )
    except FileNotFoundError as fe:
        logger.error(f"[API] RAG daily-topic: index not found: {fe}")
        return JSONResponse(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            content=ErrorResponse(
                success=False,
                error="RAG index not found. Please build the index first via POST /api/v1/rag/index.",
            ).model_dump(),
        )
    except Exception as e:
        logger.error(f"[API] RAG daily-topic error: {e}", exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=ErrorResponse(
                success=False,
                error="Unexpected error retrieving daily topic chunks.",
                details=str(e),
            ).model_dump(),
        )


# ---------------------------------------------------------------------------
# Phase 4 — Daily Learn Orchestration
# ---------------------------------------------------------------------------

@app.post(
    "/api/v1/daily-learn/today",
    response_model=DailyLearnResponse,
    summary="Get Today's Learning Content",
    description="Orchestrates Study Plan lookup, RAG retrieval, and Ollama generation for a specific day's topics.",
    responses={
        400: {"model": ErrorResponse},
        404: {"model": ErrorResponse},
        500: {"model": ErrorResponse},
    },
    tags=["Daily Learn"],
)
async def get_daily_learning_content(request: DailyLearnRequest):
    """
    Fetch the day's topics and generate learning content.
    - **courseCode**: The course to look up the saved study plan for.
    - **dayNumber**: The day in the plan to retrieve topics for.
    - **topK**: Number of chunks to retrieve per topic for context.
    """
    try:
        response = await daily_learn_service.get_daily_content(request)
        return response
    except ValueError as ve:
        # E.g. Plan not found, Invalid Day
        logger.warning(f"[API] Daily Learn Error (400/404): {ve}")
        # Return 404 if plan not found, else 400
        status_code = status.HTTP_404_NOT_FOUND if "not found" in str(ve).lower() or "no study plan found" in str(ve).lower() else status.HTTP_400_BAD_REQUEST
        return JSONResponse(
            status_code=status_code,
            content=ErrorResponse(success=False, error=str(ve)).model_dump()
        )
    except RuntimeError as re:
        # E.g. Ollama failure
        logger.error(f"[API] Daily Learn Error (500): {re}")
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=ErrorResponse(success=False, error=str(re)).model_dump()
        )
    except Exception as e:
        logger.error(f"[API] Unexpected Daily Learn error: {e}", exc_info=True)
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=ErrorResponse(
                success=False,
                error="An unexpected error occurred while generating learning content.",
                details=str(e),
            ).model_dump(),
        )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
