# Daily-Learn: Engineering Design Report & Architectural Specification

**Project Title**: Daily-Learn: Autonomous On-Device AI Study Companion  
**Domain**: Mobile Computing, Embedded Artificial Intelligence, Natural Language Processing  
**Target Platform**: Android (API 26-34)  
**Execution Environment**: 100% Offline / Zero Cloud Connectivity  
**Status**: Proof of Concept (POC) Architecture Frozen  

---

## Abstract

Collegiate students often struggle to translate dense, noise-heavy university course syllabi into actionable daily study plans, and lack access to immediate, grounded academic tutoring when studying offline. Cloud-based conversational AI models introduce network latency, recurring API usage fees, data privacy vulnerabilities, and hallucinations when answering syllabus-specific questions.

**Daily-Learn** is an autonomous, on-device Android software system designed to resolve these limitations. It introduces:
1. A deterministic document parsing pipeline capable of stripping Course Outcomes, credit formulas, and bibliographies from academic syllabi.
2. A relational academic knowledge graph implemented via Android Room SQLite.
3. A pure-Java pedagogical study scheduler reserving an 18% exam revision window.
4. An on-device Retrieval-Augmented Generation (RAG) pipeline utilizing Microsoft ONNX Runtime (`model.onnx`).
5. A native C++ inference engine compiling `llama.cpp` via JNI, streaming grounded answers token-by-token on 4 CPU threads.

The application requires zero internet permissions (`android.permission.INTERNET` is omitted), guaranteeing complete data privacy, zero operational cost, and full functionality anywhere.

---

## Table of Contents
1. Introduction & Problem Statement
2. Proposed System Architecture
3. Deterministic Syllabus Cleansing & Parsing
4. Relational Database Architecture
5. Study Plan Scheduling Mathematics
6. Retrieval-Augmented Generation (RAG) Subsystem
7. Native C++ llama.cpp Inference & JNI Architecture
8. Real-Time Streaming & State Management
9. Hardware Benchmarking & Performance Optimization
10. Testing, Verification & Quality Assurance
11. Feature Component Usage Matrix
12. Viva Voce Defense & Technical Rationale
13. Limitations & Future Scope
14. Conclusion

---

## 1. Introduction & Problem Statement

University academic syllabi are published in heterogeneous, unstructured formats (scanned photocopies, image-heavy PDFs, web portals). These documents contain substantial administrative boilerplate:
- Institutional course identifiers (`20MSS33`, `CS8391`)
- Credit formulas (`L T P C 3 0 0 3`, `Contact Hours`)
- Educational objectives (`Course Outcomes`, `CO1`, `CO2`, `Bloom's Taxonomy`)
- Evaluation rubrics, marks allocations, and question paper blueprints
- Lengthy bibliographies and textbook reading lists

When students attempt to organize their revision, or when naive AI systems ingest these files, this administrative metadata is mistakenly treated as academic topics, causing cluttered study plans and hallucinated questions.

Furthermore, commercial cloud AI solutions (OpenAI ChatGPT, Anthropic Claude) suffer from three critical flaws for student learners:
1. **Connectivity Barrier**: Require constant internet access, failing in libraries, hostels, and low-connectivity regions.
2. **Economic Barrier**: Require monthly paid subscriptions or metered API keys.
3. **Hallucination & Lack of Syllabus Grounding**: Explain concepts with open-ended, out-of-scope dissertations rather than sticking to the enrolled curriculum.

Daily-Learn solves these issues by embedding all processing?document OCR, regex cleansing, relational storage, study scheduling, vector embedding, and LLM inference?directly onto the student's smartphone hardware.

---

## 2. Proposed System Architecture

Daily-Learn adheres to Clean Architecture and MVVM (Model-View-ViewModel) principles:

```
+-----------------------------------------------------------------------------------+
|                               SYSTEM ARCHITECTURE LAYERS                          |
+-----------------------------------------------------------------------------------+
|  Presentation Layer: MainActivity, SyllabusUploadFragment, LearningSessionFragment|
|         |                                                                         |
|         v                                                                         |
|  ViewModel Layer: SyllabusAnalysisViewModel, LearningViewModel (LiveData)        |
|         |                                                                         |
|         v                                                                         |
|  Domain Layer: SyllabusAnalyzer, StudyPlanGenerator, AiTutorEngine, RagService    |
|         |                                                                         |
|         v                                                                         |
|  Data Layer: Room SQLite AppDatabase (Student, Subject, Unit, Topic, Embedding)   |
|         |                                                                         |
|         v                                                                         |
|  Native Layer: JNI native-lib.cpp, llama.cpp C++ Engine, 4 CPU Threads            |
+-----------------------------------------------------------------------------------+
```

---

## 3. Deterministic Syllabus Cleansing & Parsing

The application incorporates two complementary parsers:
- **`SyllabusAnalyzer`** (`domain/ai/SyllabusAnalyzer.java`): User-facing analyzer integrating Android `PdfRenderer` and Google ML Kit OCR to render pages at 2x scale and partition topics into daily buckets.
- **`SyllabusTextStructurer`** (`domain/parser/SyllabusTextStructurer.java`): Pure-Java headless structural parser converting raw text into normalized `ParsedSubject` -> `ParsedUnit` -> `ParsedTopic` trees.

### Critical Regex Filters & Heuristics:
1. **Course Outcome Normalization**: Normalizes OCR substitutions (`l->1`, `O->0`, `S->5`) to catch `CO1`, `Col`, `C02`, `COS`, rejecting them from topics.
2. **Course Code Line Pattern**: Detects patterns like `^[A-Z0-9]{2,4}[A-Z]{2,5}[0-9]{2,4}` to extract metadata and prevent course identifiers from becoming topics.
3. **Tail Section Truncation**: When structural headings such as `REFERENCE BOOKS` or `EVALUATION SCHEME` appear, all subsequent lines are discarded.
4. **Continuation Starters**: Filters broken lines beginning with prepositions and conjunctions (`off`, `and`, `with`, `into`).

---

## 4. Relational Database Architecture

The data layer is managed via Android Room (`data/local/AppDatabase.java`) with strict foreign-key referential integrity:
- `Student (id)` -> `Subject (student_id)` (`CASCADE`)
- `Subject (id)` -> `Unit (subject_id)` (`CASCADE`)
- `Unit (id)` -> `Topic (unit_id)` (`CASCADE`)
- `Topic (id)` -> `TopicEmbedding (topic_id)` (`CASCADE`)
- `StudyPlan (id)` -> `StudySession (plan_id, topic_id)`

Composite query objects such as `SubjectWithUnitsAndTopics` allow the entire course tree to be loaded in a single synchronous query for RAG indexing.

---

## 5. Study Plan Scheduling Mathematics

> **Clarification**: Study plan generation **does NOT use the LLM**. It is a pure-Java mathematical algorithm in `StudyPlanGenerator.java`.

### Scheduling Model:
1. availableDays = floor((examDateMillis - todayMidnightMillis) / 86,400,000)
2. revisionDays = max(3, floor(availableDays * 0.18))
3. learningDays = max(1, availableDays - revisionDays)
4. Weight = difficulty * estimatedMinutes
5. Topics are sorted by weight, interleaved round-robin across subjects, and allocated into daily `StudySession` records respecting the student's daily minute budget. Remaining calendar days are marked as `REVISION` sessions.

---

## 6. Retrieval-Augmented Generation (RAG) Subsystem

- **Chunking**: `RagChunker.java` creates atomic topic chunks containing course title, unit order, unit name, and topic concepts.
- **Embedding Model**: Quantized MiniLM-L6-v2 (`model.onnx`, ~86 MB) executed via Microsoft ONNX Runtime.
- **Tokenizer**: Pure-Java `BertTokenizer` parsing `vocab.txt` (226 KB).
- **Vector Store**: `LocalVectorStore.java` stores 384-dimensional vectors in SQLite table `topic_embeddings` as binary blobs.
- **Similarity Metric**: In-memory cosine dot product ranking top-3 matching chunks for prompt injection.
- **Production Indexing**: Lazily triggered in `LearningSessionFragment.ensureRagIndexed()`, running asynchronously on background executor `aiExecutor`.

---

## 7. Native C++ llama.cpp Inference & JNI Architecture

- **Engine**: Compiled C++ submodule `llama.cpp` linked via CMake 3.22.1 (`native-lib.cpp`).
- **Quantization**: Runs 4-bit GGUF Small Language Models (~258 MB).
- **Thread Configuration**: `n_threads = 4` default.
- **Memory Purge & State Reset**:
  Clearing KV cache via `llama_memory_seq_rm()` and resetting sampler via `llama_sampler_reset()` guarantees sequential generation turns remain completely unpolluted by previous KV states.
- **Prompt Format**: ChatML (`<|im_start|>system ... <|im_end|> <|im_start|>user ... <|im_end|> <|im_start|>assistant`).

---

## 8. Real-Time Streaming & State Management

- **Streaming Bridge**: Native C++ invokes Java callback `StreamCallback.onToken()` for every generated token.
- **Flicker-Free UI**: `ChatAdapter.appendStreamingToken()` updates the active `TextView` directly in-place, bypassing expensive `RecyclerView` rebinds.
- **Session Persistence**: `ChatSessionManager` maintains an in-memory `ConcurrentHashMap<String, List<ChatMessage>>`, preserving discussion history across bottom sheet opens and closes until application process termination.

---

## 9. Hardware Benchmarking & Performance Optimization

Benchmarks conducted on physical **OPPO CPH2119 (MediaTek Helio P95 big.LITTLE, 8GB RAM)**:
- **4 Threads (CPU4)**: **~17.79 tokens/second** (excluding TTFT), TTFT **~1,867 ms**.
- **8 Threads (CPU8)**: **~3.93 tokens/second**, suffering a 4.5x throughput drop due to thread migration and lock contention across asymmetrical Cortex-A75 big cores and Cortex-A55 LITTLE cores.
- **Production Decision**: 4 threads established as the permanent production default.

---

## 10. Testing & Quality Assurance

- **Local JVM Unit Tests**: **28 tests passing (100% success rate, 0 failures, 0 errors, 0 skipped)** covering parser regression, OCR error normalization, scheduling mathematics, and ONNX vector embeddings.
- **Instrumented Android Tests**: `EndToEndFlowTest` validates the complete on-device chain on physical hardware, asserting that real LLM generation occurs (`totalElapsed > 5000ms`), tokens stream, and zero noise leaks into RAG chunks.

---

## 11. Feature Component Usage Matrix

| Feature Area | Uses LLM? | Uses RAG? | Uses Database? | Uses Deterministic Logic? |
|---|:---:|:---:|:---:|:---:|
| **Syllabus Parsing & OCR** | NO | NO | NO | **YES** |
| **Noise & CO Rejection** | NO | NO | NO | **YES** |
| **Study Plan Scheduling** | NO | NO | NO | **YES** |
| **Data Persistence** | NO | NO | **YES** | **YES** |
| **RAG Indexing & Search** | NO | **YES** | **YES** | **YES** |
| **AI Tutor Answering** | **YES** | **YES** | NO | NO |
| **Token Streaming** | **YES** | NO | NO | **YES** |
| **Chat History Cache** | NO | NO | NO | **YES** |

---

## 12. Viva Voce Defense Summary

1. **Why Offline?**: Guarantees zero latency, zero cloud costs, absolute privacy, and total reliability without internet.
2. **Why Not Use LLM for Study Planning?**: An LLM would be slow (~minutes on mobile), nondeterministic, and consume excessive battery. A deterministic Java scheduler takes <50ms and is mathematically verifiable.
3. **Why RAG on Mobile?**: Grounds the 0.5B small language model in the student's verified syllabus, completely eliminating hallucinations.
4. **Why 4 Threads Instead of 8?**: Heterogeneous big.LITTLE mobile core topologies suffer from thread contention when oversaturated; 4 threads maximize throughput on high-performance cores.

---

## 13. Limitations & Future Scope

### Limitations:
- Local 0.5B model capacity is optimized for explanations and concepts, not for solving Grandmaster competitive programming challenges.
- Context window is constrained to 2,048 tokens to safeguard mobile RAM.

### Future Scope:
- Neural Processing Unit (NPU) acceleration via Android NNAPI or Qualcomm QNN.
- Dynamic model swapping (0.5B vs. 1.5B) based on available device RAM.
- Adaptive diagnostic quiz generation via local SLM.

---

## 14. Conclusion

Daily-Learn successfully demonstrates that autonomous, private, high-performance artificial intelligence can run effectively on standard consumer mobile hardware. By fusing deterministic parsing heuristics, relational data integrity, on-device vector retrieval, and native C++ LLM inference, Daily-Learn provides university students with an offline learning and study companion that is robust, responsive, and completely grounded in their academic curriculum.
