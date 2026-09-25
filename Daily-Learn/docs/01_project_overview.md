# 01. Project Overview: Daily-Learn

## 1. Executive Summary

**Daily-Learn** is an autonomous, on-device, AI-powered learning and exam preparation application developed for Android. It transforms complex, noisy, unstructured university course syllabi (imported via PDF documents, camera captures, or pasted text) into a structured daily study plan, and provides students with an interactive, offline AI Study Tutor.

Unlike standard educational applications that act as thin clients relying on cloud APIs (such as OpenAI ChatGPT, Google Gemini, or Anthropic Claude), Daily-Learn operates **completely offline on the student's mobile device**. It delivers an end-to-end pedagogical workflow with zero internet access, zero server hosting costs, and absolute data privacy.

```
+-----------------------------------------------------------------------------------+
|                              DAILY-LEARN CORE WORKFLOW                            |
+-----------------------------------------------------------------------------------+
|  Raw Syllabus (PDF / Image / Text)                                                |
|         |                                                                         |
|         v                                                                         |
|  Deterministic Regex & Noise Rejection (Removes COs, Marks, LTPC, References)     |
|         |                                                                         |
|         v                                                                         |
|  Room SQLite Database (Relational Hierarchy: Student -> Subject -> Unit -> Topic)|
|         |                                                                         |
|         +---------------------------------------+                                 |
|         |                                       |                                 |
|         v                                       v                                 |
|  Deterministic Study Scheduler         Local RAG Indexing (ONNX Runtime)          |
|  (Pure Java Spaced Learning)           (Topic Chunks -> model.onnx -> Vector DB)  |
|                                                 |                                 |
|                                                 v                                 |
|  Student Doubt in Chat UI  --------->  Cosine Semantic Search                     |
|                                                 |                                 |
|                                                 v                                 |
|  Grounded Answer (Streamed) <------- llama.cpp C++ Native Engine (4 CPU Threads)  |
+-----------------------------------------------------------------------------------+
```

---

## 2. Target Audience

Daily-Learn is engineered specifically for:
- **Collegiate & University Students**: Enrolled in syllabus-driven academic programs (Engineering, Sciences, Arts, and Management) governed by formal curriculum guidelines.
- **Exam Candidates**: Students facing semester examinations, continuous internal assessments (CIA), or competitive tests with fixed study calendar windows.
- **Offline & Bandwidth-Constrained Learners**: Students studying in remote locations, university hostels with restricted Wi-Fi, or examination halls where cloud connectivity is unavailable or erratic.
- **Privacy-Conscious Institutions**: Organizations requiring strict data compliance where student performance records and proprietary study notes must never be transmitted off-device.

---

## 3. Problem Statement & Core Value Proposition

### Problem 1: Unstructured and Corrupted Syllabus Documents
Academic syllabi published by universities are notoriously messy. When ingested from PDF or OCR scans, they are filled with bureaucratic boilerplate:
- Institutional course codes (`20MSS33`, `CS8391`, `25SS212`)
- Credit distribution formulas (`L T P C 3 0 0 3`, `Contact Hours`)
- Pedagogical metadata (`Course Outcomes`, `CO1`, `CO2`, `Bloom's Taxonomy`)
- Internal assessment formulas, marks distributions, and question paper blueprints
- Lengthy bibliographies and reference book citations (`Reference Books`, `Text Books`)
- OCR scanning artifacts and line-wrap continuation fragments

If a student attempts to schedule their revision, or if a generic AI ingests this text, this administrative metadata is mistakenly treated as academic topics, cluttering study plans and producing hallucinated study questions.

### Problem 2: LLM Hallucination and Academic Scope Creep
General-purpose conversational AI models are prone to hallucination. When a student asks a cloud LLM about *"Inorder Traversal"*, the model often introduces irrelevant tangents, such as compiler AST implementations, threaded binary trees, or language-specific memory management nuances that are outside the scope of their immediate course syllabus.

### Problem 3: Cloud Dependency, Latency, and Privacy Risks
Most modern AI learning tools require constant high-speed internet, charge recurring API usage subscriptions, and introduce high network latency. Students studying on the go cannot rely on uninterrupted connectivity.

### Daily-Learn's Solution
Daily-Learn resolves these challenges through a unified on-device architecture:
1. **Algorithmic Syllabus Cleansing**: Pure-Java deterministic heuristics strip away administrative boilerplate, isolating genuine academic units and topics.
2. **Relational Knowledge Graph**: Course data is persisted in a normalized SQLite/Room database with strict referential integrity.
3. **Deterministic Study Plan Scheduler**: Generates spaced learning and revision windows without consuming LLM inference tokens.
4. **Local Retrieval-Augmented Generation (RAG)**: Syllabus content is embedded via a local ONNX transformer (`model.onnx`, 90MB) and indexed into a local vector store.
5. **Grounded Native LLM Inference**: Offline prompt synthesis injects exact syllabus context into an embedded C++ `llama.cpp` inference engine running on 4 CPU threads, streaming grounded answers token-by-token.
6. **Zero Internet Requirement**: The application manifest (`AndroidManifest.xml`) omits `android.permission.INTERNET`, providing hardware-level privacy and offline reliability.
