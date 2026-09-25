# 03. Complete Data Flow: Daily-Learn

This document provides end-to-end data flow specifications for all primary operations in Daily-Learn, illustrating exact data transformations, protocol boundaries, and storage lifecycles.

---

## Flow A: Syllabus Import & Document Reading

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant UI as SyllabusUploadFragment
    participant VM as SyllabusUploadViewModel
    participant SA as SyllabusAnalyzer
    participant OCR as Google ML Kit OCR

    Student->>UI: Selects PDF or Image Document
    UI->>VM: setFile(uri, fileName)
    Student->>UI: Clicks "Analyze Syllabus"
    UI->>VM: analyze()
    VM->>SA: analyzeUri(uri, targetDays, callback)
    
    alt Document is PDF
        SA->>SA: PdfRenderer renders page at 2x scale
        SA->>OCR: InputImage.fromBitmap(bitmap)
        OCR-->>SA: Extracted Page Text
    else Document is Image
        SA->>OCR: InputImage.fromFilePath(uri)
        OCR-->>SA: Extracted Raw Text
    end
    
    SA->>SA: Concatenates text into fullExtractedText
    SA->>SA: processRawText(fullExtractedText, targetDays)
    SA-->>VM: SyllabusAnalysisResult
    VM-->>UI: Displays Structured Subject & Days
```

**Data Transferred**:
- Input: Android content `Uri` (`content://media/...`).
- Intermediate: Scaled in-memory Android `Bitmap` (ARGB_8888) rendered at 2x resolution.
- Output: `SyllabusAnalysisResult` containing Course Name, Course Code, and `List<DailyTopicGroup>`.

---

## Flow B: Deterministic Syllabus Cleansing & Parsing

```mermaid
flowchart TD
    A[Raw OCR Syllabus Text] --> B[extractCourseInfo: Extract Code e.g. 20MSS33 & Title]
    B --> C[Noise Rejection: Filter Course Outcomes CO1-CO6, LTPC, Bloom Taxonomy]
    C --> D[extractCurriculumSections: Isolate UNIT / MODULE Blocks]
    D --> E{Distinct Units Found?}
    E -- Yes --> F[tokenizeSubtopics: Split by commas, semicolons, bullets]
    E -- No --> G[extractFallbackSections: Line-by-line topic extraction]
    G --> F
    F --> H[isValidTopic Filter: Reject continuation fragments and isolated keywords]
    H --> I[distributeIntoDays: Partition topics into targetDays buckets]
    I --> J[Structured Syllabus Tree]
```

**Key Data Transitions**:
1. `rawText`: Unstructured string containing header boilerplate, faculty names, and OCR artifacts.
2. `sections`: List of `RawSection` holding unit headers (e.g. *"UNIT I: LINEAR DATA STRUCTURES"*) and clean curricular text.
3. `allTopics`: Flat list of validated `ExtractedTopicItem` objects.
4. `dailyGroups`: List of `DailyTopicGroup` mapped across `Day 1` through `Day N`.

---

## Flow C: Relational Database Persistence Flow

```mermaid
sequenceDiagram
    autonumber
    participant VM as SyllabusAnalysisViewModel
    participant DB as AppDatabase
    participant SD as SubjectDao
    participant SPD as StudyPlanDao

    VM->>DB: studentDao().getStudentSync()
    Note over VM,DB: Obtains active studentId (e.g. 1L)
    VM->>SD: insertSubject(Subject)
    SD-->>VM: returns subjectId
    VM->>SPD: getLatestPlanSync(studentId) or insertStudyPlan()
    SPD-->>VM: returns planId
    
    loop For each Unique Unit
        VM->>SD: insertUnit(Unit with subjectId)
        SD-->>VM: returns unitId
        loop For each Topic in Unit
            VM->>SD: insertTopic(Topic with unitId)
            SD-->>VM: returns topicId
            VM->>SPD: insertStudySession(StudySession with planId, topicId, scheduledDate)
        end
    end
```

**Foreign Key Integrity**:
- `Subject` references `Student(id)` (`ON DELETE CASCADE`).
- `Unit` references `Subject(id)` (`ON DELETE CASCADE`).
- `Topic` references `Unit(id)` (`ON DELETE CASCADE`).
- `StudySession` references `StudyPlan(id)` and `Topic(id)`.

---

## Flow D: Study Plan Generation Flow (Pure Java Scheduler)

```mermaid
flowchart TD
    A[Exam Date & Daily Minutes Budget] --> B[StudyPlanGenerator.generate]
    B --> C[Calculate availableDays = examDate - today]
    C --> D["Calculate revisionDays = max(3, floor(availableDays * 0.18))"]
    D --> E[Calculate learningDays = availableDays - revisionDays]
    E --> F[sortByWeight: Sort topics by difficulty * estimatedMinutes]
    F --> G[interleaveBySubject: Round-robin interleave across subjects]
    G --> H[Loop topics & assign to learningDays respecting dailyMinutes budget]
    H --> I[Assign remaining available days as REVISION sessions]
    I --> J[List of StudySession entities ready for Room insertion]
```

---

## Flow E: Semantic RAG Indexing Flow

```mermaid
sequenceDiagram
    autonumber
    participant UI as LearningSessionFragment
    participant RS as RagService
    participant RC as RagChunker
    participant ES as EmbeddingService
    participant ONNX as model.onnx Runtime
    participant LVS as LocalVectorStore
    participant DB as TopicEmbeddingDao

    UI->>UI: ensureRagIndexed() (Lazy background check)
    UI->>RS: isIndexed()
    alt Index does not exist
        UI->>DB: subjectDao.getAllSubjectsWithUnitsAndTopicsSync()
        DB-->>UI: SubjectWithUnitsAndTopics
        UI->>RS: indexSyllabus(subjectData)
        RS->>RC: createChunks(subjectData)
        RC-->>RS: List of RagChunk (Topic definitions + Unit context)
        RS->>ES: embedTexts(chunkTexts)
        ES->>ONNX: Tokenize with vocab.txt & run inference
        ONNX-->>ES: 384-dimensional float arrays
        RS->>LVS: floatArrayToByteArray(embeddings)
        RS->>DB: insertAll(TopicEmbedding records)
        DB-->>RS: Confirmed saved
    end
```

---

## Flow F & G: Student Question & Local LLM Generation Flow

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant UI as Chat Bottom Sheet
    participant Frag as LearningSessionFragment
    participant Engine as AiTutorEngine
    participant RAG as RagService
    participant Local as LocalLLMEngine
    participant JNI as native-lib.cpp
    participant LLAMA as llama.cpp CPU

    Student->>UI: Types question and clicks Send
    UI->>Frag: Adds User Message to ChatSessionManager & ChatAdapter
    Frag->>Engine: generateResponseStreaming(topic, subject, query, tokenBudget, callback)
    
    Engine->>RAG: search(subject + topic + query, topK=3)
    RAG-->>Engine: List of SearchHit (Top 3 syllabus chunks)
    
    Engine->>Engine: Synthesizes System Prompt + Context + User Query
    Engine->>Local: generateStreaming(prompt, maxTokens, callback)
    Local->>JNI: generateStreamingNative(contextPtr, prompt, maxTokens, callback)
    
    JNI->>LLAMA: llama_memory_seq_rm() (Reset KV Cache)
    JNI->>LLAMA: llama_sampler_reset() (Reset Sampler)
    JNI->>LLAMA: llama_tokenize() prompt
    JNI->>LLAMA: llama_decode() prompt batch (TTFT phase)
    
    loop For each generated token
        LLAMA->>JNI: llama_sampler_sample()
        JNI->>JNI: llama_token_to_piece()
        JNI->>JNI: Check stop tokens & repetition guardrails
        JNI->>Frag: onToken(tokenPiece) via JNI callback
        Frag->>UI: runOnUiThread -> ChatAdapter.appendStreamingToken()
        UI-->>Student: Token appears smoothly on screen
    end
    
    JNI->>Frag: onComplete(fullResponse)
    Frag->>Frag: Preserves complete answer in ChatSessionManager
```
