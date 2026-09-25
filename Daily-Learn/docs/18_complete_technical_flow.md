# 18. Complete Technical Flow (A-to-Z Master Architecture)

This document serves as the master engineering reference for Daily-Learn, detailing every operational transition from the user's physical screen touch to the hardware execution on the mobile processor.

---

## 1. The Unified End-to-End Pipeline

```mermaid
flowchart TD
    subgraph UI_Intake["1. User Intake"]
        A[User imports PDF / Text] --> B[SyllabusUploadFragment]
        B --> C[SyllabusUploadViewModel]
    end

    subgraph Deterministic_Parsing["2. Parsing & Cleansing"]
        C --> D[SyllabusAnalyzer / TextStructurer]
        D --> E[Regex Noise Filter: Drop COs, Marks, LTPC, Bibliographies]
        E --> F[Atomic Topic Extraction & Daily Bucket Grouping]
    end

    subgraph Relational_Storage["3. Relational Persistence"]
        F --> G[SyllabusAnalysisViewModel.savePlanAndStart]
        G --> H[(Room SQLite AppDatabase)]
        H --> I[Student -> Subject -> Unit -> Topic Tree]
    end

    subgraph Algorithmic_Scheduling["4. Study Plan Scheduling"]
        I --> J[StudyPlanGenerator.generate]
        J --> K[Pure-Java Math: availableDays - 18% revisionDays]
        K --> L[(StudySession Table)]
    end

    subgraph Semantic_Indexing["5. Semantic RAG Indexing"]
        L --> M[LearningSessionFragment.ensureRagIndexed]
        M --> N[RagChunker: Syllabus Topic Chunks]
        N --> O[EmbeddingService: ONNX model.onnx]
        O --> P[(TopicEmbedding SQLite Table: 384-dim Vectors)]
    end

    subgraph Runtime_Tutoring["6. AI Tutoring & Grounding"]
        Q[Student Query in Chat] --> R[LocalVectorStore.search: Cosine Similarity]
        P --> R
        R --> S[Top-3 Syllabus Chunks]
        S --> T[AiTutorEngine: ChatML Prompt Synthesis]
    end

    subgraph Native_Inference["7. Native Inference & Streaming"]
        T --> U[LocalLLMEngine -> LlamaCppPoC JNI]
        U --> V[native-lib.cpp: llama_memory_seq_rm & sampler_reset]
        V --> W[llama.cpp: 4-Thread CPU Evaluation]
        W --> X[JNI StreamCallback.onToken]
        X --> Y[ChatAdapter.appendStreamingToken in-place]
        Y --> Z[Interactive Chat UI Display]
    end
```

---

## 2. Exhaustive Step-by-Step Technical Analysis

### Step 1: Ingestion & Rendering
- **Trigger**: User picks a document in `SyllabusUploadFragment`.
- **Implementation**: If a PDF is selected, `SyllabusAnalyzer` invokes Android's `PdfRenderer`, opening each page and rendering it onto an in-memory `Bitmap` at 2x resolution (ARGB_8888) to maximize character edge definition.

### Step 2: Optical Character Recognition (OCR)
- **Engine**: Google ML Kit on-device Text Recognition (`TextRecognition.getClient()`).
- **Processing**: The bitmap is wrapped into an `InputImage` and processed via `Tasks.await()`.
- **Output**: Raw string buffer preserving positional line breaks.

### Step 3: Deterministic Filtering & Cleansing
- **Engine**: `SyllabusAnalyzer.processRawText()` & `SyllabusTextStructurer`.
- **Filters**:
  - `COURSE_CODE_LINE`: Extracts alphanumeric course code (`20MSS33`) and title.
  - `CO_CODE_PATTERN`: Strips Course Outcomes (`CO1`, `Col`, `C02`, `COS`).
  - `TAIL_MARKERS`: Truncates references, textbooks, and evaluation schemes.
  - `CONTINUATION_STARTERS`: Drops broken OCR continuation fragments.

### Step 4: Relational Persistence
- **Engine**: Android Room ORM (`AppDatabase.java`).
- **Transactions**: `SyllabusAnalysisViewModel.savePlanAndStart()` runs an asynchronous worker thread inserting `Subject`, `Unit`, `Topic`, `StudyPlan`, and `StudySession` records with cascading foreign keys.

### Step 5: Pure-Java Scheduling
- **Engine**: `StudyPlanGenerator.java`.
- **Logic**: Calculates total available calendar days, reserves max(3, floor(days * 0.18)) revision days, weights topics by difficulty, interleaves subjects, and assigns them across learning days.

### Step 6: Lazy Semantic RAG Indexing
- **Engine**: `RagService.java` & `EmbeddingService.java`.
- **Trigger**: `LearningSessionFragment.ensureRagIndexed()` checks `isIndexed()`.
- **Vectors**: Chunks are processed through `model.onnx` (MiniLM-L6-v2) to yield 384-dimensional dense vectors, serialized into byte blobs, and stored in `topic_embeddings`.

### Step 7: Doubt Retrieval & Context Injection
- **Engine**: `AiTutorEngine.java`.
- **Retrieval**: `LocalVectorStore.search()` computes in-memory dot product similarity against stored syllabus vectors, retrieving top-3 matching chunks.
- **Prompt**: Formats the retrieved context, subject title, topic name, and user doubt into a ChatML template.

### Step 8: Native JNI Inference & Token Streaming
- **Engine**: `native-lib.cpp` and `llama.cpp`.
- **State Reset**: `llama_memory_seq_rm()` clears KV cache; `llama_sampler_reset()` resets sampler.
- **Inference**: Tokenizes prompt, decodes batch, and iteratively samples output tokens on 4 CPU threads.
- **UI Update**: Each token piece is forwarded via JNI to `StreamCallback.onToken()`, posting to `runOnUiThread()` where `ChatAdapter.appendStreamingToken()` appends text in-place without triggering layout rebinds.
