# 20. Complete System Mermaid Diagrams Collection

This document compiles all architectural, behavioral, and relational Mermaid diagrams for Daily-Learn in standard markdown syntax.

---

### Diagram 1: High-Level Clean Architecture
```mermaid
flowchart TD
    subgraph Presentation["Presentation Layer (UI)"]
        A[MainActivity]
        B[SyllabusUploadFragment]
        C[SyllabusAnalysisFragment]
        D[LearningSessionFragment]
        E[Chat Dialog Sheet]
    end

    subgraph ViewModel["ViewModel Layer"]
        F[SyllabusUploadViewModel]
        G[SyllabusAnalysisViewModel]
        H[LearningViewModel]
    end

    subgraph Domain["Domain Layer"]
        I[SyllabusAnalyzer / Structurer]
        J[StudyPlanGenerator]
        K[AiTutorEngine]
        L[RagService]
        M[LocalLLMEngine]
    end

    subgraph Data["Data Layer (Room DB)"]
        N[(AppDatabase)]
        O[Student / Subject / Unit / Topic]
        P[TopicEmbedding Table]
    end

    subgraph Native["Native Layer (C++)"]
        Q[native-lib.cpp JNI]
        R[llama.cpp Engine]
        S[4-Thread CPU Pool]
    end

    Presentation --> ViewModel
    ViewModel --> Domain
    Domain --> Data
    Domain --> Native
```

---

### Diagram 2: Complete Relational Database ER Diagram
```mermaid
erDiagram
    STUDENT ||--o{ SUBJECT : enrolls
    SUBJECT ||--o{ UNIT : contains
    UNIT ||--o{ TOPIC : defines
    TOPIC ||--o| TOPIC_EMBEDDING : indexed_by
    STUDENT ||--o{ STUDY_PLAN : owns
    STUDY_PLAN ||--o{ STUDY_SESSION : schedules
    TOPIC ||--o{ STUDY_SESSION : assigned_to

    STUDENT {
        int id PK
        string name
        int semester
        string branch
    }
    SUBJECT {
        int id PK
        int student_id FK
        string name
        long target_exam_date
    }
    UNIT {
        int id PK
        int subject_id FK
        string name
        int unit_order
    }
    TOPIC {
        int id PK
        int unit_id FK
        string name
        int difficulty
        int estimated_minutes
    }
    TOPIC_EMBEDDING {
        int id PK
        int topic_id FK
        string chunk_id
        string text
        blob vector
    }
    STUDY_SESSION {
        int id PK
        int plan_id FK
        int topic_id FK
        long scheduled_date
        boolean is_completed
    }
```

---

### Diagram 3: Local RAG Pipeline Flow
```mermaid
flowchart LR
    A[Syllabus Topic] --> B[RagChunker]
    B --> C[EmbeddingService ONNX]
    C --> D[(TopicEmbedding Table)]
    E[Student Query] --> F[EmbeddingService ONNX]
    F --> G[Cosine Search]
    D --> G
    G --> H[Top-3 Chunks]
    H --> I[AiTutorEngine Prompt]
    I --> J[llama.cpp Native Engine]
```

---

### Diagram 4: Native JNI Streaming Sequence
```mermaid
sequenceDiagram
    participant UI as ChatAdapter (RecyclerView)
    participant Frag as LearningSessionFragment
    participant JNI as native-lib.cpp
    participant Cpp as llama.cpp CPU

    Frag->>JNI: generateStreamingNative(prompt, maxTokens, callback)
    JNI->>Cpp: llama_memory_seq_rm() & llama_sampler_reset()
    JNI->>Cpp: Decode prompt batch (TTFT ~1.8s)
    loop For Each Token
        Cpp->>JNI: Sample token
        JNI->>Frag: callback.onToken(tokenPiece)
        Frag->>UI: appendStreamingToken(in-place update)
    end
    JNI->>Frag: callback.onComplete(fullText)
```

---

### Diagram 5: End-to-End User Experience Journey
```mermaid
stateDiagram-v2
    [*] --> UploadSyllabus : Select PDF or Text
    UploadSyllabus --> AnalyzingSyllabus : ML Kit OCR & Regex Cleanse
    AnalyzingSyllabus --> ReviewPlan : Verify Extracted Units & Days
    ReviewPlan --> ActiveStudyPlan : Save to Room SQLite DB
    ActiveStudyPlan --> StudySession : Open Day's Prescribed Topic
    StudySession --> AskAiTutor : Open Chat Bottom Sheet
    AskAiTutor --> StreamingResponse : RAG Search + llama.cpp 4-Thread CPU
    StreamingResponse --> StudySession : Close Chat (History Preserved)
    StudySession --> [*] : Mark Topic Completed
```
