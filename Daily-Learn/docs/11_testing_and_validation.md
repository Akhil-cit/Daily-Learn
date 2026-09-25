# 11. Testing & Validation Architecture

## 1. Testing Strategy Overview

Daily-Learn incorporates a rigorous, multi-tiered testing suite spanning local JVM unit tests, regression harnesses, Android instrumented tests, and hardware benchmark suites:

```
+-----------------------------------------------------------------------------------+
|                           DAILY-LEARN TEST SUITE TAXONOMY                         |
+-----------------------------------------------------------------------------------+
|  1. Local JVM Unit Tests (28 Tests Passed - 0 Failures)                           |
|     - LocalExplanationSourceTest (4 tests)                                        |
|     - SchedulerAndParserTest (5 tests)                                            |
|     - SyllabusAnalyzerTest (3 tests)                                              |
|     - SyllabusNoiseExclusionTest (1 test)                                         |
|     - SyllabusRealSyllabusRegressionTest (12 tests)                               |
|     - SyllabusTextStructurerNoiseTest (1 test)                                    |
|     - RagUnitTest (2 tests)                                                       |
|                                                                                   |
|  2. On-Device Instrumented Tests (app/src/androidTest/)                            |
|     - EndToEndFlowTest: Full pipeline validation                                  |
|     - RagInstrumentedTest: Room DB & ONNX retrieval                               |
|     - Phase3RealDbRagTest: Multi-subject RAG integration                          |
|     - LlamaCppPoCTest: Native JNI inference & streaming                           |
|                                                                                   |
|  3. Hardware Benchmark Harnesses                                                  |
|     - LlamaCppPerfBench: CPU 4 vs 8 thread throughput                             |
|     - CpuParityBenchmarkTest: Core utilization metrics                            |
+-----------------------------------------------------------------------------------+
```

---

## 2. Exhaustive Unit Test Suite (28 Passed)

All 28 unit tests execute on the JVM via Gradle `./gradlew testDebugUnitTest`:

| Test Suite Class | Tests | Execution Time | Scope & Verification |
|---|---|---|---|
| `LocalExplanationSourceTest` | 4 | 0.005s | Verifies fallback explanation source behaviors when offline AI is uninitialized. |
| `SchedulerAndParserTest` | 5 | 0.013s | Verifies `StudyPlanGenerator` available days, revision fraction, and budget math. |
| `SyllabusAnalyzerTest` | 3 | 0.063s | Verifies `processRawText` course code extraction and daily topic distribution. |
| `SyllabusNoiseExclusionTest` | 1 | 0.003s | Validates filtering of LTPC, marks, and evaluation sections. |
| `SyllabusRealSyllabusRegressionTest` | 12 | 0.022s | Rigorous regression testing on real OCR syllabi (`20MSS33` and variations). |
| `SyllabusTextStructurerNoiseTest` | 1 | 0.000s | Validates rejection of OCR-corrupted Course Outcome markers (`CO1`, `Col`, `C02`). |
| `RagUnitTest` | 2 | 1.587s | Tests ONNX `EmbeddingService` vector generation and cosine similarity calculation. |
| **Total** | **28** | **1.693s** | **100% Success Rate (0 Failures, 0 Errors, 0 Skipped)** |

---

## 3. Critical Instrumented Tests (Device-Verified)

### 1. `EndToEndFlowTest` (`androidTest/.../EndToEndFlowTest.java`)
Validates the entire production chain on physical Android hardware:
$$	ext{Raw OCR Syllabus} ightarrow 	ext{Parser} ightarrow 	ext{Room DB} ightarrow 	ext{RAG Index} ightarrow 	ext{AiTutorEngine} ightarrow 	ext{Native LLM} ightarrow 	ext{Streamed Answer}$$
- **Verification Assertions**:
  - `modelLoaded == true` (verifies GGUF model loaded into memory).
  - `ragSearchCalled == true` (verifies semantic vector search executed).
  - `llmGenerateCalled == true` (verifies native C++ JNI boundary was crossed).
  - `streamingTokenCount > 0` (verifies tokens arrived via callback).
  - `totalElapsed > 5000ms` (proves real LLM execution occurred rather than a mock).
  - Verifies zero Course Outcomes or reference lines leaked into RAG chunks.

### 2. `RagInstrumentedTest` & `Phase3RealDbRagTest`
- Ingests real syllabus trees into an in-memory Room database.
- Verifies foreign key cascading rules.
- Indexes topic chunks into `topic_embeddings` table.
- Executes semantic queries (e.g. *"binary search tree insertion"*) and asserts that the top search hit matches the target unit.

### 3. `LlamaCppPerfBench` (`androidTest/.../LlamaCppPerfBench.java`)
- Measures Time-To-First-Token (TTFT) and Tokens Per Second (TPS) across different CPU thread counts.
- Verifies clean memory purging via `llama_memory_seq_rm()` between consecutive turns.
