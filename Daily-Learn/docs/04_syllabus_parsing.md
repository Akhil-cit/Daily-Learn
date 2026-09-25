# 04. Syllabus Parsing & Noise Cleansing

## 1. The Challenges of Academic Syllabus Parsing

University syllabi are notoriously irregular documents. When ingested via OCR or digital PDF extractors, several categories of noise corrupt the textual stream:

1. **Course Identification Codes**: Lines such as `20MSS33`, `25SS212`, `CS8391`, or `20mss33 - Advanced Data Structures` represent course catalog identifiers rather than study topics.
2. **Credit Formulas & Timetable Metadata**: Text such as `L T P C 3 0 0 3`, `LTPc`, `Contact Hours: 45`, `Total Hours: 60`, or `Lecture: 3 Tutorial: 0 Practical: 0` are administrative instructions for faculty.
3. **Pedagogical Boilerplate & Course Outcomes (COs)**: Sections defining `Course Outcomes`, `CO1: Apply data structures...`, `CO2`, `Bloom's Taxonomy (K1, K2, K3)`, and program educational objectives.
4. **Examination & Evaluation Schemes**: Instructions regarding mid-term assessments, internal tests, semester end exams, maximum marks (e.g. `100 Marks`), and question paper blueprints.
5. **Bibliographies & Reference Sections**: Trailing sections titled `References`, `Text Books`, `Online Resources`, listing author names, ISBN numbers, publisher details, and edition years.
6. **OCR Continuation Fragments**: Broken sentences resulting from column wraps or table layouts (e.g., words like `off`, `and`, `with`, or `implementation`).

---

## 2. Comparison: SyllabusAnalyzer vs. SyllabusTextStructurer

The codebase contains two distinct parsing engines designed for different architectural responsibilities:

| Metric / Dimension | `SyllabusAnalyzer` (`domain/ai/SyllabusAnalyzer.java`) | `SyllabusTextStructurer` (`domain/parser/SyllabusTextStructurer.java`) |
|---|---|---|
| **Primary Purpose** | User-facing interactive syllabus analysis, PDF OCR rendering, and daily study planning. | Pure-Java headless structural parser converting raw text into database-ready entities. |
| **Android Dependency** | Depends on `Context`, `PdfRenderer`, and Google ML Kit Text Recognition. | Pure Java with zero Android dependencies (100% testable via JVM JUnit). |
| **Input Modalities** | File `Uri` (PDF, JPG, PNG) or raw `String` text. | Raw `String` text. |
| **Output Data Structure** | `SyllabusAnalysisResult` containing `DailyTopicGroup` lists mapped to days. | `List<ParsedSubject>` containing nested `ParsedUnit` and `ParsedTopic` trees. |
| **Noise Filtering Strategy** | Heuristic regex filtering, curriculum section isolation, and fallback topic tokenization. | Multi-pass regex filtering, structural course-code detection, OCR-corrupted CO normalization, and tail truncation. |
| **Production Usage** | Powers `SyllabusUploadFragment` and `SyllabusAnalysisViewModel`. | Powers unit tests, automated regressions, and batch ingestion. |

---

## 3. Cleansing Algorithms & Regex Heuristics

### A. Course Outcome (CO) Detection & OCR Normalization
Syllabi frequently contain corrupted Course Outcome tags due to OCR errors. `SyllabusTextStructurer` applies a specialized normalization pattern:

```java
// Normalizes common OCR swaps before regex matching:
// 0 -> O, 1 -> I, 5 -> S, Z -> O
private static final Pattern CO_CODE_PATTERN =
    Pattern.compile("^CO[0-9IOSZA]+(\\s*[:.,;\\-?\"]|\\s|$).*");
```

**Real Regression Example**:
- **Corrupted Input**: `Col : Illustrate binary tree structures` or `C01 : Understand hashing`
- **Old Behavior**: Failed to detect `Col` because `l` was treated as a letter; text became a study topic named `"Col : Illustrate..."`.
- **New Behavior**: The character `l` is recognized as an OCR substitution for digit `1`; the entire line is rejected as a Course Outcome.
- **Robustness Safeguard**: In `SyllabusRealSyllabusRegressionTest`, legitimate academic topics starting with `"co"` (such as `"collision resolution methods"`) are protected by ensuring CO detection strictly requires an OCR digit/character immediately following `"CO"`.

### B. Course Code Detection
Course codes must be captured for subject metadata but prevented from becoming study topics:

```java
// Matches tokens like 20MSS33, 25SS212, CS8391
private static final Pattern COURSE_CODE_LINE =
    Pattern.compile("^[A-Z0-9]{2,4}[A-Z]{2,5}[0-9]{2,4}(\\s.*)?$");
```

### C. Tail Section Truncation (References & Evaluation)
Once a syllabus reaches reference books or evaluation rubrics, all subsequent text is non-curricular. `SyllabusTextStructurer` enforces tail markers:

```java
private static final String[] TAIL_MARKERS = {
    "REFERENCE BOOKS", "TEXT BOOKS", "REFERENCES", "TEXTBOOKS",
    "COURSE OUTCOMES", "EVALUATION SCHEME", "QUESTION PAPER PATTERN",
    "SUGGESTED READINGS", "WEB REFERENCES"
};
```
When any of these headings is encountered, parser state switches to tail rejection, immediately terminating topic extraction for that unit.

### D. Fragment Continuation Line Filtering
To prevent broken sentence fragments from becoming topics, lines starting with lower-case continuation words are filtered:

```java
private static final Set<String> CONTINUATION_STARTERS = new HashSet<>(Arrays.asList(
    "off", "and", "or", "but", "for", "the", "of", "to", "in",
    "with", "by", "on", "at", "from", "into", "about", "an", "a"
));
```
**Legitimate Short Topic Protection**:
To prevent legitimate single-word computer science topics from being rejected by the short-fragment filter, an explicit whitelist is maintained:
`"arrays", "stack", "queue", "trees", "graphs", "heaps", "recursion", "hashing", "sorting", "polymorphism", "inheritance", "encapsulation"`.

---

## 4. Real-World Regression Case Study: 20MSS33

An official Advanced Data Structures syllabus (`20MSS33`) exposed critical edge cases during development:

| Raw Input Line | Old Output (Buggy) | Root Cause | Fixed Logic | Correct Output |
|---|---|---|---|---|
| `20MSS33 ADVANCED DATA STRUCTURES` | Generated topic: `"20MSS33"` | Parser lacked course-code recognition. | `COURSE_CODE_LINE` pattern added. | Extracted as Course Code; excluded from topics. |
| `Col : Describe tree properties` | Generated topic: `"Col : Describe..."` | OCR swapped digit `1` with lowercase `l`. | Normalized OCR swaps (`l->I`, `0->O`). | Identified as Course Outcome; rejected. |
| `LTPc 3003` | Generated topic: `"LTPc 3003"` | Credit header treated as curriculum. | LTPC regex filter added. | Rejected as administrative metadata. |
| `collision resolution methods` | Mistakenly filtered out | Regex checked `line.startsWith("co")` too broadly. | Strict digit check enforced (`CO[0-9]`). | Preserved as valid academic topic. |
| `linked list implementation` | Mistakenly filtered out | Parser rejected all lowercase starting lines. | Continuation starter check restricted to prepositions/conjunctions. | Preserved as valid academic topic. |
