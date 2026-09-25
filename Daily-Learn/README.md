# Daily-Learn: Autonomous On-Device AI Study Companion

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-brightgreen.svg)](https://developer.android.com)
[![Offline](https://img.shields.io/badge/Network-100%25%20Offline%20(Zero%20Internet)-blue.svg)]()
[![Inference](https://img.shields.io/badge/AI%20Inference-llama.cpp%20%7C%20JNI%20%7C%20CPU4-orange.svg)](https://github.com/ggerganov/llama.cpp)
[![RAG](https://img.shields.io/badge/RAG-ONNX%20Runtime%20MiniLM--L6--v2-purple.svg)](https://onnxruntime.ai)
[![Database](https://img.shields.io/badge/Database-Room%20SQLite-red.svg)]()
[![License](https://img.shields.io/badge/License-MIT-green.svg)]()

Daily-Learn is an autonomous, on-device, AI-powered learning and exam preparation system developed for Android smartphones. It allows students to import messy university course syllabi (PDF, image, or text), extracts genuine academic topics while stripping administrative noise, schedules a spaced study and revision roadmap, and provides a real-time, interactive **AI Study Tutor that runs completely offline**.

```
+-----------------------------------------------------------------------------------+
|                              DAILY-LEARN CORE ARCHITECTURE                        |
+-----------------------------------------------------------------------------------+
|  Raw Syllabus (PDF/OCR) ---> Regex Filter ---> Room SQLite DB ---> Pure Java Math |
|                                                    |                     |        |
|                                                    v                     v        |
|  Student Question ---> Semantic Vector Search ---> System Prompt    Study Plan    |
|                               (model.onnx)               |                        |
|                                                          v                        |
|  Real-Time Chat UI <--- Native JNI Token Stream <--- llama.cpp (4 CPU Threads)    |
+-----------------------------------------------------------------------------------+
```

---

## ?? Key Features

1. **100% Offline AI Operation**: Operates with zero internet connectivity. The app manifest completely omits `android.permission.INTERNET`.
2. **Noise-Hardened Syllabus Parsing**: Deterministically identifies course codes (`20MSS33`) and titles while rejecting Course Outcomes (`CO1`?`CO6`, `Col`, `C02`), credit formulas (`L T P C 3 0 0 3`), evaluation schemes, and reference bibliographies.
3. **Deterministic Study Plan Generator**: Calculates available days, automatically reserves a mandatory 18% revision window, and schedules topics weighted by difficulty without wasting LLM inference tokens.
4. **On-Device Semantic RAG (Retrieval-Augmented Generation)**: Indexes syllabus topics into an embedded SQLite vector store using an on-device ONNX transformer (`model.onnx`, 384 dimensions) to ground answers and eliminate AI hallucinations.
5. **Native CPU LLM Inference**: Runs GGUF small language models via compiled C++ `llama.cpp` using the Java Native Interface (JNI). Optimized for 4 CPU threads on mobile big.LITTLE architectures.
6. **Progressive Token Streaming**: Streams generated answers token-by-token into the chat UI in real time, featuring in-place viewholder updates and persistent session history caching.

---

## ?? Technology Stack

- **Platform**: Android SDK (API 34, Min SDK 26)
- **Languages**: Java (JDK 17), C++ (C++17), CMake, SQL, Gradle Kotlin DSL
- **Native Inference**: `llama.cpp` + GGML via JNI (`native-lib.cpp`)
- **Semantic Embeddings**: Microsoft ONNX Runtime (`model.onnx`, MiniLM-L6-v2) + `vocab.txt`
- **OCR Engine**: Google ML Kit on-device Text Recognition
- **Database**: Android Room ORM + SQLite
- **Architecture**: Clean Architecture + MVVM + Android Jetpack (ViewModel, LiveData, ViewBinding)

---

## ?? Performance & Hardware Verification

All benchmarks verified on the physical test device: **OPPO CPH2119 (MediaTek Helio P95 big.LITTLE, 8GB RAM, Android 12)**:
- **Default Inference Threading**: `n_threads = 4` (CPU-only).
- **Generation Speed**: ~17.79 tokens/second (excluding TTFT) on 4 CPU threads.
- **Time-To-First-Token (TTFT)**: ~1,867 ms.
- **Why 4 Threads Beat 8 Threads**: Avoids thread migration and lock synchronization bottlenecks on heterogeneous ARM Cortex-A75 (big) and Cortex-A55 (LITTLE) clusters.

---

## ?? Testing Suite Status

- **JVM Unit Tests**: **28 tests passing (0 failures, 0 errors, 0 skipped)**.
- **Instrumented Android Tests**: Fully verified on-device via `EndToEndFlowTest`, `RagInstrumentedTest`, and `LlamaCppPoCTest`.

---

## ?? Detailed Documentation Index

Comprehensive engineering specifications are available in the [`docs/`](docs/) directory:
- [01. Project Overview](docs/01_project_overview.md)
- [02. System Architecture](docs/02_system_architecture.md)
- [03. Complete Data Flow](docs/03_complete_data_flow.md)
- [04. Syllabus Parsing & Noise Cleansing](docs/04_syllabus_parsing.md)
- [05. Database Architecture](docs/05_database_architecture.md)
- [06. RAG Architecture](docs/06_rag_architecture.md)
- [07. Local LLM & JNI](docs/07_local_llm_and_jni.md)
- [08. AI Tutor Logic](docs/08_ai_tutor_logic.md)
- [09. Study Plan Logic (Does LLM Run Here?)](docs/09_study_plan_logic.md)
- [10. Streaming & State Management](docs/10_streaming_and_state_management.md)
- [11. Testing & Validation](docs/11_testing_and_validation.md)
- [12. Technology Stack](docs/12_technology_stack.md)
- [13. Programming Languages](docs/13_programming_languages.md)
- [14. Performance & Benchmarking](docs/14_performance_and_benchmarking.md)
- [15. Professor Demo Flow & Script](docs/15_end_to_end_demo_flow.md)
- [16. Limitations & Future Scope](docs/16_limitations_and_future_scope.md)
- [17. 50+ Viva Voce Questions & Answers](docs/17_viva_questions_and_answers.md)
- [18. Complete Technical Flow](docs/18_complete_technical_flow.md)
- [19. Feature LLM Usage Matrix](docs/19_feature_llm_usage_matrix.md)
- [20. Mermaid Diagrams Collection](docs/20_mermaid_diagrams.md)

---

## ?? License
Released under the MIT License.
