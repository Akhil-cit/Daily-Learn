# Daily Learn — Local AI Syllabus Engine
## Phase 1: PDF & Image (JPG/PNG) → Structured Syllabus JSON

The **Daily Learn** backend is a local AI engine that extracts structured academic syllabi (courses, units, lecture hours, topics, subtopics, source pages) directly from uploaded **PDF** and **Image (JPG, JPEG, PNG)** documents.

It runs **100% locally and free** using **FastAPI**, **PyMuPDF**, **Tesseract OCR**, and **Ollama** (`llama3.2:3b`) without any paid cloud APIs, external AI dependencies, or heavy framework abstractions.

---

## 1. Exact Folder Structure

```
ai-rag/
├── backend/
│   ├── main.py                     # FastAPI application & REST endpoint controllers
│   ├── requirements.txt            # Python package dependencies
│   ├── .env.example                # Template for environment configuration
│   ├── .env                        # Local configuration file (Ollama host, Tesseract path, etc.)
│   ├── config.py                   # Pydantic Settings loader & Tesseract path resolver
│   ├── logger.py                   # Structured console logging ([PDF], [OCR], [AI], [API])
│   ├── test_analyzer.py            # Standalone CLI test script (PDF & Image testing)
│   ├── test_api.py                 # FastAPI client endpoint tests (PDF, JPG, JPEG, PNG)
│   │
│   ├── models/
│   │   ├── __init__.py
│   │   └── syllabus_models.py      # Pydantic schemas (Topic, Unit, Syllabus, API envelopes)
│   │
│   ├── services/
│   │   ├── __init__.py
│   │   ├── pdf_extractor.py        # PyMuPDF-based page-by-page text extractor & normalizer
│   │   ├── image_ocr.py            # Local Tesseract OCR extractor for JPG/JPEG/PNG
│   │   ├── ollama_client.py        # Async HTTP client for Ollama API (/api/chat, /api/tags)
│   │   └── syllabus_analyzer.py    # Pipeline coordinator, JSON validator & repair retry
│   │
│   ├── prompts/
│   │   └── syllabus_prompt.txt     # Strict system prompt for academic extraction
│   │
│   └── data/
│       └── uploads/                # Temporary directory for uploaded files and test samples
│
└── README.md                       # Complete setup and architecture documentation
```

---

## 2. Requirements (`backend/requirements.txt`)

```
fastapi>=0.110.0
uvicorn[standard]>=0.28.0
pymupdf>=1.23.0
httpx>=0.27.0
pydantic>=2.6.0
pydantic-settings>=2.2.0
python-dotenv>=1.0.0
python-multipart>=0.0.9
pytesseract>=0.3.10
pillow>=10.0.0
```

---

## 3. Installation & Setup Commands

Ensure you have **Python 3.10+** installed. Open PowerShell in the backend directory:

```powershell
# 1. Navigate to backend folder
cd "e:\Proj\ai rag\backend"

# 2. (Optional but recommended) Create and activate a virtual environment
python -m venv venv
.\venv\Scripts\Activate.ps1

# 3. Install Python dependencies
pip install -r requirements.txt
```

### Installing Local Tesseract OCR (Windows)

Tesseract OCR is used for extracting text from JPG, JPEG, and PNG images.

1. **Option A (winget)**:
   ```powershell
   winget install UB-Mannheim.TesseractOCR
   ```
2. **Option B (Installer Download)**:
   Download the installer from [UB-Mannheim Tesseract Wiki](https://github.com/UB-Mannheim/tesseract/wiki) and run the installer.
3. **Configure path (if not in PATH)**:
   Add your Tesseract path to `backend/.env`:
   ```env
   TESSERACT_CMD=C:\Program Files\Tesseract-OCR\tesseract.exe
   ```

---

## 4. How to Start Ollama

Ensure Ollama is running and has the `llama3.2:3b` model downloaded:

```powershell
# Start Ollama service
ollama serve

# Ensure the llama3.2:3b model is pulled:
ollama pull llama3.2:3b
```

---

## 5. How to Start the FastAPI Backend

```powershell
cd "e:\Proj\ai rag\backend"

# Start the uvicorn server
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

The API will be available at:
- **API Base**: `http://localhost:8000`
- **Swagger Documentation**: `http://localhost:8000/docs`
- **ReDoc**: `http://localhost:8000/redoc`

---

## 6. Health Check Endpoints

### Server Health (`/health`)
```powershell
curl http://localhost:8000/health
```
```json
{
  "status": "ok"
}
```

### Ollama Status (`/health/ollama`)
```powershell
curl http://localhost:8000/health/ollama
```
```json
{
  "ollama": "connected",
  "model": "llama3.2:3b",
  "available": true,
  "models_available": ["llama3.2:3b"],
  "error": null
}
```

### OCR Status (`/health/ocr`)
```powershell
curl http://localhost:8000/health/ocr
```
```json
{
  "ocr": "available",
  "installed": true,
  "path": "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
  "version": "5.4.0",
  "error": null
}
```

---

## 7. How to Upload a Syllabus (PDF or Image)

### A. Upload PDF via `curl`
```powershell
curl -X POST "http://localhost:8000/api/v1/syllabus/analyze" `
  -F "file=@data/uploads/sample_oop_syllabus.pdf"
```

### B. Upload JPG/PNG Image via `curl`
```powershell
curl -X POST "http://localhost:8000/api/v1/syllabus/analyze" `
  -F "file=@data/uploads/sample_oop_syllabus.jpg"
```

### C. Run Standalone CLI Analyzer (`test_analyzer.py`)
```powershell
cd "e:\Proj\ai rag\backend"

# Test PDF
python test_analyzer.py --pdf "data/uploads/sample_oop_syllabus.pdf"

# Test Image (JPG/PNG)
python test_analyzer.py --image "data/uploads/sample_oop_syllabus.jpg"
```

### D. Run Automated Test Suite (`test_api.py`)
```powershell
cd "e:\Proj\ai rag\backend"
python test_api.py
```

---

## 8. Example JSON Output

```json
{
  "success": true,
  "data": {
    "courseName": "OBJECT ORIENTED PROGRAMMING",
    "courseCode": "25SS212",
    "units": [
      {
        "unitNumber": 1,
        "unitName": "PRINCIPLES OF OBJECT ORIENTED PROGRAMMING",
        "lectureHours": 6.0,
        "topics": [
          {
            "name": "Software Crisis and Software Evolution",
            "subtopics": [],
            "sourcePages": [1]
          },
          {
            "name": "Procedure-Oriented vs Object-Oriented Programming Paradigm",
            "subtopics": [],
            "sourcePages": [1]
          },
          {
            "name": "Basic Concepts of OOP",
            "subtopics": [
              "Objects",
              "Classes",
              "Data Abstraction",
              "Encapsulation",
              "Inheritance",
              "Polymorphism"
            ],
            "sourcePages": [1]
          },
          {
            "name": "Benefits and Applications of OOP",
            "subtopics": [],
            "sourcePages": [1]
          }
        ]
      },
      {
        "unitNumber": 2,
        "unitName": "FUNCTIONS AND OBJECTS IN C++",
        "lectureHours": 8.0,
        "topics": [
          {
            "name": "Function Prototyping and Inline Functions",
            "subtopics": [],
            "sourcePages": [1]
          },
          {
            "name": "Default Arguments and Const Arguments",
            "subtopics": [],
            "sourcePages": [1]
          },
          {
            "name": "Function Overloading",
            "subtopics": [],
            "sourcePages": [1]
          },
          {
            "name": "Classes and Objects",
            "subtopics": [
              "Specifying a Class",
              "Defining Member Functions"
            ],
            "sourcePages": [1]
          }
        ]
      }
    ]
  }
}
```

---

## 9. File-by-File Explanation

| File | Purpose |
|---|---|
| [`main.py`](backend/main.py) | FastAPI app exposing `/health`, `/health/ollama`, `/health/ocr`, and `POST /api/v1/syllabus/analyze`. Validates file formats (`.pdf`, `.jpg`, `.jpeg`, `.png`), routes to appropriate extractor, and returns structured syllabus JSON. |
| [`config.py`](backend/config.py) | Application settings loader with auto-detection for Tesseract OCR binaries on Windows. |
| [`logger.py`](backend/logger.py) | Structured logging utility (`[PDF]`, `[OCR]`, `[AI]`, `[API]`). |
| [`models/syllabus_models.py`](backend/models/syllabus_models.py) | Pydantic v2 schemas for `Topic`, `Unit`, `Syllabus`, `PageExtractionResult`, and health envelopes. |
| [`prompts/syllabus_prompt.txt`](backend/prompts/syllabus_prompt.txt) | System prompt enforcing strict anti-hallucination rules, non-academic filtering, and raw JSON schema output. |
| [`services/pdf_extractor.py`](backend/services/pdf_extractor.py) | PyMuPDF text extractor for PDF documents. Preserves page boundaries and headings. |
| [`services/image_ocr.py`](backend/services/image_ocr.py) | Local Tesseract OCR service for JPG, JPEG, and PNG images. Performs image preprocessing, text extraction, cleaning, and assigns `sourcePages: [1]`. |
| [`services/ollama_client.py`](backend/services/ollama_client.py) | Async HTTP client for Ollama's `/api/chat` and `/api/tags` with native JSON mode and timeout protection. |
| [`services/syllabus_analyzer.py`](backend/services/syllabus_analyzer.py) | Coordinates prompt construction, Ollama inference, Pydantic validation, and 1-step repair retries. |
| [`test_analyzer.py`](backend/test_analyzer.py) | Standalone CLI testing script for testing PDF and image extraction directly. |
| [`test_api.py`](backend/test_api.py) | Automated test suite for all FastAPI endpoints. |
