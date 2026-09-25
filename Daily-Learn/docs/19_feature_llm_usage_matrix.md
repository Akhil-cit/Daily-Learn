# 19. Feature LLM & Component Usage Matrix

The following matrix provides an architectural breakdown proving **exactly which features use the local LLM, which use RAG, which use the database, and which use deterministic logic**:

| Feature Area | Uses LLM? | Uses RAG? | Uses Database? | Uses Deterministic Logic? | Verified Source Code & Method | Technical Explanation & Proof |
|---|:---:|:---:|:---:|:---:|---|---|
| **Syllabus Document Parsing** | **NO** | **NO** | **NO** | **YES** | `SyllabusAnalyzer.java`<br/>`processRawText()` | Pure-Java regex heuristics and Google ML Kit OCR. Zero LLM tokens consumed. |
| **Noise & Course Outcome Rejection** | **NO** | **NO** | **NO** | **YES** | `SyllabusTextStructurer.java`<br/>`CO_CODE_PATTERN` | Deterministic regex matching and OCR swap normalization (`0->O`, `1->I`, `5->S`). |
| **Course Code & Title Extraction** | **NO** | **NO** | **NO** | **YES** | `SyllabusAnalyzer.java`<br/>`extractCourseInfo()` | Structural pattern matching for alphanumeric codes (e.g. `20MSS33`). |
| **Study Plan Day Allocation** | **NO** | **NO** | **NO** | **YES** | `StudyPlanGenerator.java`<br/>`generate()` | **CRITICAL PROOF**: Pure-Java scheduling math (availableDays - 18% revision). Completes in <50ms without AI. |
| **Adaptive Rescheduling** | **NO** | **NO** | **YES** | **YES** | `AdaptiveRescheduler.java`<br/>`rescheduleIncompleteTopics()` | Mathematical reallocation of overdue topic sessions into remaining calendar days. |
| **Syllabus Data Persistence** | **NO** | **NO** | **YES** | **YES** | `SyllabusAnalysisViewModel.java`<br/>`savePlanAndStart()` | Android Room SQLite transactions writing normalized entities (`Subject`, `Unit`, `Topic`). |
| **Syllabus RAG Chunking** | **NO** | **NO** | **NO** | **YES** | `RagChunker.java`<br/>`createChunks()` | Hierarchical string formatting of syllabus units and topic metadata. |
| **Vector Embedding Generation** | **NO** | **YES** | **NO** | **YES** | `EmbeddingService.java`<br/>`embedTexts()` | ONNX Runtime transformer inference (`model.onnx`). Neural network, but not an LLM. |
| **Syllabus Vector Index Storage** | **NO** | **YES** | **YES** | **YES** | `LocalVectorStore.java`<br/>`saveEmbeddings()` | SQLite binary blob serialization into Room `topic_embeddings` table. |
| **Semantic Similarity Search** | **NO** | **YES** | **YES** | **YES** | `LocalVectorStore.java`<br/>`search()` | In-memory cosine dot product over 384-dimensional embedding vectors. |
| **Tutor Prompt Construction** | **NO** | **YES** | **NO** | **YES** | `AiTutorEngine.java`<br/>`generateResponseStreaming()` | String formatting combining system instructions, retrieved syllabus chunks, and student query. |
| **AI Study Tutor Answering** | **YES** | **YES** | **NO** | **NO** | `LocalLLMEngine.java`<br/>`LlamaCppPoC.generateStreaming()` | **REAL LLM EXECUTION**: Runs GGUF small language model locally via native `llama.cpp` on 4 CPU threads. |
| **Token-by-Token Streaming** | **YES** | **NO** | **NO** | **YES** | `native-lib.cpp`<br/>`StreamCallback.onToken()` | JNI callback bridge dispatching sampled token strings to Android main UI thread. |
| **Chat Session History Cache** | **NO** | **NO** | **NO** | **YES** | `ChatSessionManager.java`<br/>`getHistory()` | In-memory process-scoped `ConcurrentHashMap` preserving chat messages across dialog closures. |
