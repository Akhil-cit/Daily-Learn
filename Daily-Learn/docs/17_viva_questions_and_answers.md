# 17. Comprehensive Viva Voce Questions & Answers (50+ Questions)

This document contains 50+ rigorous technical questions and answers designed for academic project evaluations, viva voce examinations, and technical reviews.

---

### Category 1: Project Overview & Objectives

#### Q1: What is Daily-Learn in one sentence?
**A**: Daily-Learn is an autonomous, on-device Android learning system that deterministically converts academic syllabi into scheduled study plans and provides an interactive, offline AI Study Tutor using RAG and `llama.cpp`.

#### Q2: What is the main problem Daily-Learn solves?
**A**: It eliminates academic boilerplate from course syllabi, prevents LLM hallucination through local curriculum-grounded RAG, and provides high-quality AI tutoring completely offline with zero internet dependency and zero cloud costs.

#### Q3: Who is the target user for Daily-Learn?
**A**: University students and competitive exam candidates who need structured daily study schedules and private, instant academic doubt clarification without relying on internet connectivity.

#### Q4: Why is Daily-Learn designed to work 100% offline?
**A**: To guarantee zero latency from network drops, zero recurring cloud API fees, complete privacy of student performance data, and accessibility in connectivity-restricted environments (hostels, libraries, rural areas).

#### Q5: Does the application declare the INTERNET permission in AndroidManifest.xml?
**A**: No. The manifest completely omits `android.permission.INTERNET`, providing hardware-level proof that no data is transmitted off the device.

---

### Category 2: Android & Database Architecture

#### Q6: What architectural pattern does Daily-Learn follow?
**A**: Clean Architecture combined with Google's recommended MVVM (Model-View-ViewModel) pattern, isolating Presentation (Fragments), Domain (Schedulers/RAG/AI), and Data (Room DAOs) layers.

#### Q7: Why was Android Room chosen instead of raw SQLite?
**A**: Room provides compile-time verification of SQL statements, eliminates boilerplate object mapping, enforces foreign-key referential integrity, and integrates smoothly with reactive architecture components.

#### Q8: What are the primary entities in the database?
**A**: `Student`, `Subject`, `Unit`, `Topic`, `Exam`, `StudyPlan`, `StudySession`, `Quiz`, `QuizQuestion`, `QuizResult`, `Progress`, and `TopicEmbedding`.

#### Q9: What happens when a Subject is deleted from the database?
**A**: Due to Room foreign key declarations with `onDelete = ForeignKey.CASCADE`, deleting a `Subject` automatically deletes all its associated `Unit`, `Topic`, `StudySession`, and `TopicEmbedding` records, preventing orphaned database entries.

#### Q10: What bug was discovered in early RAG instrumented tests regarding foreign keys?
**A**: Tests attempted to insert `Subject` records with `studentId = 1L` before inserting a matching parent row into the `students` table, causing SQLite to throw a `ForeignKeyConstraintException`. The test harness was corrected to create a real parent `Student` record first.

---

### Category 3: Syllabus Parsing & Cleansing

#### Q11: Why is syllabus parsing difficult?
**A**: Academic syllabi are laden with non-curricular administrative metadata (Course Outcomes, marks schemes, credit formulas like `L T P C 3 0 0 3`, course codes, and reference bibliographies) and OCR errors.

#### Q12: What is the difference between SyllabusAnalyzer and SyllabusTextStructurer?
**A**: `SyllabusAnalyzer` runs in the UI layer, integrating Android `PdfRenderer` and Google ML Kit OCR to display progress and distribute topics into days. `SyllabusTextStructurer` is a pure-Java headless structural parser that converts raw text into database-ready subject/unit/topic trees with zero Android dependencies.

#### Q13: How does the parser prevent Course Outcomes (CO1, CO2) from becoming study topics?
**A**: It utilizes OCR-hardened regex patterns that normalize common scanning swaps (such as `l -> 1`, `O -> 0`, `S -> 5`) to detect `CO1`, `Col`, `C02`, and rejects any lines matching Course Outcome syntax.

#### Q14: Why did an earlier test falsely reject the topic "collision resolution methods"?
**A**: An overly broad regex checked whether the line started with `"co"`. The logic was refined to strictly require an OCR numeric digit immediately following `"CO"`, preserving valid academic topics like *"collision resolution methods"*.

#### Q15: How are trailing reference books and bibliographies eliminated?
**A**: The parser implements tail truncation: when structural section headers such as `REFERENCE BOOKS`, `TEXT BOOKS`, or `EVALUATION SCHEME` are detected, all subsequent content is discarded.

---

### Category 4: Study Plan & Scheduling Logic

#### Q16: Does the study plan generation use an LLM?
**A**: **No.** Study plan generation is a 100% deterministic, pure-Java algorithm executed by `StudyPlanGenerator.java` in under 50 milliseconds.

#### Q17: How is the revision period calculated in StudyPlanGenerator?
**A**: It calculates $	ext{revisionDays} = \max(3, \lfloor 	ext{availableDays} 	imes 0.18 floor)$, reserving at least 3 days (or 18% of total available days) exclusively for review before an exam.

#### Q18: How does the scheduler handle topic difficulty?
**A**: It weights topics by $	ext{difficulty} 	imes 	ext{estimatedMinutes}$, sorting heavier topics earlier and interleaving subjects round-robin to maintain balanced student engagement.

---

### Category 5: Retrieval-Augmented Generation (RAG)

#### Q19: What is RAG and why is it used here?
**A**: Retrieval-Augmented Generation dynamically searches syllabus chunks relevant to a student's question and injects them into the prompt. This grounds the local LLM in the student's curriculum and prevents hallucination.

#### Q20: What model is used for generating embeddings?
**A**: A quantized MiniLM-L6-v2 transformer model packaged as `model.onnx` (~86 MB) running locally via Microsoft ONNX Runtime.

#### Q21: What is the dimensionality of the generated vectors?
**A**: 384 dimensions (`float[384]`).

#### Q22: Where are the embedding vectors stored?
**A**: In the local Room SQLite table `topic_embeddings` as binary blobs (`byte[]`).

#### Q23: How is semantic similarity calculated?
**A**: `LocalVectorStore` performs an in-memory dot product / cosine similarity search between the 384-dimensional query vector and all stored chunk vectors, ranking matches in descending score order.

#### Q24: When does RAG indexing occur in the production application?
**A**: Lazily in `LearningSessionFragment.ensureRagIndexed()`, executed asynchronously on a background thread when the student opens a study session.

#### Q25: What happens if RAG indexing fails?
**A**: It is caught in a `try/catch` block; the AI Tutor degrades gracefully by falling back to basic course title and topic name context without crashing.

---

### Category 6: Local LLM & llama.cpp Integration

#### Q26: What is llama.cpp?
**A**: An open-source, highly optimized C/C++ tensor inference engine designed for running GGUF-quantized language models locally on consumer CPU and GPU hardware.

#### Q27: What is GGUF?
**A**: GPT-Generated Unified Format?a binary file format for storing quantized model weights, hyperparameters, and tokenizers in a single file optimized for memory-mapped I/O (`mmap`).

#### Q28: How does Android Java communicate with C++ llama.cpp?
**A**: Via the Java Native Interface (JNI). Java class `LlamaCppPoC` declares native methods implemented in C++ file `native-lib.cpp`.

#### Q29: What is the default CPU thread count and why?
**A**: `n_threads = 4`. Hardware benchmarking on the 8-core MediaTek Helio P95 big.LITTLE processor revealed that 4 threads deliver ~17.79 tokens/sec, whereas 8 threads suffer severe lock contention and drop to ~3.93 tokens/sec.

#### Q30: Does the current production POC use GPU acceleration?
**A**: No. The current production POC relies strictly on CPU inference. GPU/Vulkan was investigated but omitted due to mobile driver incompatibilities on the target chipset.

#### Q31: Why do we call llama_memory_seq_rm and llama_sampler_reset before each turn?
**A**: To clear the Key-Value (KV) cache memory and reset the sampler state, ensuring that subsequent questions start clean and are not corrupted by previous generation states.

#### Q32: What prompt format is used for the AI Tutor?
**A**: The ChatML format: `<|im_start|>system ... <|im_end|> <|im_start|>user ... <|im_end|> <|im_start|>assistant`.

---

### Category 7: Streaming & State Management

#### Q33: How does token streaming work in the chat UI?
**A**: Native C++ invokes the Java callback `StreamCallback.onToken()` for every generated token. The fragment posts this to `runOnUiThread()`, where `ChatAdapter.appendStreamingToken()` appends text directly to the active `TextView`.

#### Q34: How is chat flickering prevented during streaming?
**A**: `ChatAdapter.appendStreamingToken()` updates the existing `ViewHolder` directly in-place rather than triggering a full `RecyclerView` adapter rebind (`notifyItemChanged`).

#### Q35: What is TTFT?
**A**: Time-To-First-Token?the time taken by the LLM to process and decode the entire prompt batch before emitting the first generated output token (~1.8 seconds on the test device).

#### Q36: What is ChatSessionManager?
**A**: A process-scoped in-memory cache (`ConcurrentHashMap<String, List<ChatMessage>>`) that retains conversation history across bottom sheet opens and closes until the application process exits.

---

### Category 8: Testing, Performance & Limitations

#### Q37: How many unit tests are in the project?
**A**: 28 unit tests, all currently passing with 0 failures, 0 errors, and 0 skipped.

#### Q38: What does EndToEndFlowTest prove?
**A**: It proves the complete on-device chain on a physical phone: raw syllabus parsing -> Room DB persistence -> ONNX RAG indexing -> semantic search -> native `llama.cpp` inference -> token streaming.

#### Q39: Why does the local AI tutor stop early when asked complex competitive programming problems?
**A**: The local model is an ultra-lightweight 0.5B Small Language Model (~258 MB) optimized for conceptual explanations and study tips. It lacks the parameters to solve Grandmaster-level algorithmic challenges and emits an End-of-Generation (EOG) token early when exceeding its reasoning capacity.

#### Q40: What is the maximum context length configured for the local LLM?
**A**: `n_ctx = 2048` tokens, chosen to balance memory footprint and prevent mobile out-of-memory errors.

#### Q41: Can Daily-Learn run on low-end smartphones?
**A**: Yes. The entire model (~258 MB) and app runtime require less than 1.2 GB of total RAM, running comfortably on standard 4GB?8GB Android smartphones.

#### Q42: What happens when the user asks a doubt without internet?
**A**: Since all models, vector stores, and engines reside locally on the phone, the query executes identically regardless of whether the phone is online or in Airplane Mode.

#### Q43: How does Daily-Learn ensure student privacy?
**A**: All syllabus documents, chat discussions, study plans, and diagnostic test results remain strictly on the device's private internal sandboxed storage. No telemetry or analytics are transmitted.

#### Q44: What are the prompt chips in the AI Tutor sheet?
**A**: Pre-engineered shortcut prompts: "?? Analogy", "?? Code Example", and "? Complexity" that guide the tutor into specific pedagogical explanation modes.

#### Q45: How does AdaptiveRescheduler work?
**A**: If a student misses scheduled study sessions, `AdaptiveRescheduler` dynamically reallocates incomplete topics into remaining study days without modifying completed topics.

#### Q46: What is the purpose of RevisionPlanner?
**A**: It scans topics marked as "Needs Revision" by the student and schedules targeted spaced-repetition review sessions before exams.

#### Q47: What build tool compiles native C++ code in Daily-Learn?
**A**: CMake 3.22.1, invoked automatically by Gradle via Android NDK.

#### Q48: What is BertTokenizer in the RAG pipeline?
**A**: A pure-Java WordPiece tokenizer that reads `vocab.txt` to convert syllabus text into token IDs required by `model.onnx`.

#### Q49: What is the role of DailyTopicGroup?
**A**: A data container grouping topics, section titles, and estimated minutes into a single day's study unit.

#### Q50: What is the biggest advantage of Daily-Learn over cloud solutions like ChatGPT?
**A**: Absolute reliability with zero internet connection, zero API subscription costs, complete data privacy, and strict grounding in the student's verified curriculum.
