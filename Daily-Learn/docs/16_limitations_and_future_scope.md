# 16. Limitations & Future Scope

## 1. Current Implementation Limitations

To maintain rigorous academic honesty, the following real-world constraints of the current Proof-of-Concept (POC) implementation are documented:

### A. Small Language Model (SLM) Reasoning Capacity
- **Model Size**: The on-device model is a quantized Small Language Model (~258 MB, ~0.5 Billion parameters).
- **Consequence**: While the model excels at pedagogical explanations, concept definitions, study tips, and simple code snippets, it **cannot solve Grandmaster-level competitive programming challenges** (e.g., dynamic graph offline connectivity with rollback DSU) or write multi-file software projects. When prompted beyond its parametric capacity, it terminates early with an End-of-Generation (EOG) stop token.

### B. Hardware & Thermal Constraints on Mobile CPUs
- **CPU Execution**: Inference runs exclusively on CPU cores. Sustained multi-turn conversations can cause CPU heating and thermal throttling on mid-range chipsets.
- **Context Window**: Native context memory is constrained to `n_ctx = 2048` tokens to prevent mobile out-of-memory (OOM) crashes.

### C. Dependency on OCR Input Quality
- If a student uploads a severely blurred, low-resolution photograph with missing text lines, Google ML Kit OCR may drop characters, requiring manual topic adjustment in the app's manual editor.

### D. Model Distribution in Production
- In the current benchmark prototype, the model is loaded from `/data/local/tmp/benchmark_model.gguf`. A production consumer release would require an in-app download manager or APK asset packaging.

---

## 2. Future Scope & Research Roadmap

The following enhancements represent natural engineering extensions of the Daily-Learn architecture:

1. **NPU Acceleration via NNAPI / Qualcomm QNN**:
   Offload transformer tensor operations to mobile Neural Processing Units (NPUs) to increase generation throughput from ~18 tok/s to >40 tok/s while drastically reducing battery consumption.
2. **Dynamic Model Swapping**:
   Support user selection between ultra-light models (0.5B for fast study tips) and larger models (1.5B?3B for advanced coding and mathematical proofs) based on available device RAM.
3. **Adaptive Quiz Generation via SLM**:
   Expand the on-device model to dynamically synthesize multiple-choice diagnostic quizzes tailored to the student's weakest syllabus units.
4. **Multimodal Syllabus Ingestion**:
   Directly process lecture slide diagrams and textbook schematics into the RAG vector index using local multimodal vision models.
