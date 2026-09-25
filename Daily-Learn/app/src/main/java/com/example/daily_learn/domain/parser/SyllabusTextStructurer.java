package com.example.daily_learn.domain.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java heuristic parser: converts raw syllabus text into a structured
 * list of ParsedSubject objects (Subject → Unit → Topic).
 *
 * Hardened against:
 *  - OCR-corrupted course-outcome codes: CO1/C01/Col/C0I/COS/C02/COZ etc.
 *  - Course-code lines: 20MSS33, 20mss33 – Title, 20MSS33 – Title Part 1 etc.
 *  - Credit/LTPC metadata: LTPC, LTPc, L T P C, 3 0 0 3, 3003 etc.
 *  - Administrative headers: Contact Hours, Contact Hours Definition
 *  - Prerequisite variants: Pre-Requisites, Pre-Reouisites, Prerequisites etc.
 *  - Fragment continuation lines from PDF OCR
 *  - ALL-CAPS noise headings being promoted as subject names
 *  - Context-aware CO outcome prose (inside a COURSE OUTCOME section)
 *
 * Heuristics for legitimate content:
 *  - Lines matching "UNIT [IVX\d]+" → new Unit
 *  - ALL-CAPS lines (> 3 chars, noise-free) → Subject name
 *  - Numbered items "1." "1)" "•" "–" → Topics
 *  - Falls back to treating non-empty lines as topics when structured context exists
 */
public class SyllabusTextStructurer {

    // ─── Unit header ──────────────────────────────────────────────────────────
    private static final Pattern UNIT_PATTERN =
        Pattern.compile("(?i)^\\s*(?:UNIT|MODULE|CHAPTER)\\s+([IVXLC\\d]+)[:\\-\\s–]*(.*)",
                Pattern.CASE_INSENSITIVE);

    // ─── Numbered / bulleted item ─────────────────────────────────────────────
    private static final Pattern NUMBERED_ITEM =
        Pattern.compile("^\\s*[\\d]+[.)\\s]+(.+)");
    private static final Pattern BULLET_ITEM =
        Pattern.compile("^\\s*[•\\-–*]\\s+(.+)");

    // ─── ALL-CAPS subject heading ─────────────────────────────────────────────
    // Requires ≥ 4 letters, at least 85 % uppercase, 4–70 chars.
    private static final Pattern ALL_CAPS =
        Pattern.compile("^[A-Z][A-Z0-9 &:,\\-]{3,69}$");

    // ─── Structural course-code pattern ───────────────────────────────────────
    // Matches tokens such as 20MSS33, 25SS212, CS8391, 20mss33 (normalised to
    // uppercase before matching). Must be at start of line, optionally followed
    // by separator and title.
    private static final Pattern COURSE_CODE_LINE =
        Pattern.compile("^[A-Z0-9]{2,4}[A-Z]{2,5}[0-9]{2,4}(\\s.*)?$");

    // ─── Structural CO-code pattern (OCR-hardened) ────────────────────────────
    // After normalising 0→O, 1→I, 5→S, Z→O (common OCR swaps), this pattern
    // detects CO1, CO2, C01, Col, COS, COZ, C0I, C04: etc.
    // Applied on the normalised uppercase version of the line.
    private static final Pattern CO_CODE_PATTERN =
        Pattern.compile("^CO[0-9IOSZA]+(\\s*[:.,;\\-–—]|\\s|$).*");

    // ─── Known single-word or very-short legitimate topic fragments to KEEP ───
    // These would otherwise be caught by the strict short-fragment filter.
    private static final Set<String> ALLOWED_SHORT_TOPICS = new HashSet<>(Arrays.asList(
            "arrays", "array", "stack", "stacks", "queue", "queues", "trees", "tree",
            "graphs", "graph", "heaps", "heap", "lists", "list", "vectors", "matrices",
            "polymorphism", "inheritance", "encapsulation", "abstraction",
            "recursion", "backtracking", "memoization", "hashing", "sorting",
            "searching", "threading", "concurrency", "synchronization",
            "serialization", "generics", "reflection", "annotation"
    ));

    // ─── Fragment continuation indicators ─────────────────────────────────────
    // A topic line that STARTS with these words (lowercase) is almost certainly
    // a wrapped continuation line from the previous OCR/PDF paragraph.
    private static final Set<String> CONTINUATION_STARTERS = new HashSet<>(Arrays.asList(
            "off", "and", "or", "but", "for", "the", "of", "to", "in",
            "with", "by", "on", "at", "from", "into", "about", "an", "a",
            "as", "its", "their", "this", "that", "which", "where",
            "when", "while", "after", "before", "since", "than", "unless",
            "way", "such", "using", "based", "via"
    ));

    // ─── Outcome section verbs (only noise INSIDE a CO section) ──────────────
    private static final Set<String> OUTCOME_VERBS = new HashSet<>(Arrays.asList(
            "illustrate", "understand", "apply", "analyze", "analyse",
            "choose", "assess", "evaluate", "create", "design", "implement",
            "demonstrate", "differentiate", "compare", "construct", "develop"
    ));

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Main parse entry point.
     *
     * @param rawText Raw syllabus text (from PDF OCR, image OCR, or plain text)
     * @return List of ParsedSubject objects; never null, may be empty.
     */
    public List<ParsedSubject> parse(String rawText) {
        List<ParsedSubject> subjects = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) return subjects;

        String[] lines = rawText.split("\\r?\\n");

        ParsedSubject currentSubject = null;
        ParsedUnit    currentUnit    = null;
        boolean       tailMode       = false;
        boolean       inCoSection    = false;  // true while inside a COURSE OUTCOME block

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // ── 1. Tail mode: everything after a tail marker is dropped ─────
            if (tailMode) continue;
            if (isTailMarker(line)) {
                tailMode = true;
                continue;
            }

            // ── 2. Detect entry/exit of a COURSE OUTCOME section ───────────
            String lowerLine = line.toLowerCase(Locale.ROOT);
            if (isCourseOutcomeHeader(lowerLine)) {
                inCoSection = true;
                continue;   // The header itself is noise
            }
            // A UNIT header exits the CO section
            if (UNIT_PATTERN.matcher(line).matches()) {
                inCoSection = false;
            }

            // ── 3. Hard noise filter (applies everywhere) ───────────────────
            if (isNoiseLine(line)) continue;

            // ── 4. Unit header ─────────────────────────────────────────────
            Matcher unitMatcher = UNIT_PATTERN.matcher(line);
            if (unitMatcher.matches()) {
                inCoSection = false;   // Exiting CO section
                if (currentSubject == null) {
                    currentSubject = new ParsedSubject("General");
                    subjects.add(currentSubject);
                }
                String unitNum    = unitMatcher.group(1);
                String unitSuffix = unitMatcher.group(2).trim();
                String unitTitle  = "Unit " + unitNum;
                if (!unitSuffix.isEmpty()) unitTitle += " \u2013 " + toTitleCase(unitSuffix);
                currentUnit = new ParsedUnit(unitTitle);
                currentSubject.units.add(currentUnit);
                continue;
            }

            // ── 5. ALL-CAPS subject heading (noise-guarded) ─────────────────
            if (isAllCapsSubjectLine(line)) {
                currentSubject = new ParsedSubject(toTitleCase(line));
                subjects.add(currentSubject);
                currentUnit    = null;
                inCoSection    = false;
                continue;
            }

            // ── 6. Topic candidate ─────────────────────────────────────────
            String topicName = null;

            Matcher numMatcher = NUMBERED_ITEM.matcher(line);
            if (numMatcher.matches()) {
                topicName = numMatcher.group(1).trim();
            } else {
                Matcher bulletMatcher = BULLET_ITEM.matcher(line);
                if (bulletMatcher.matches()) {
                    topicName = bulletMatcher.group(1).trim();
                } else if (isAcceptablePlainLine(line)) {
                    topicName = line;
                }
            }

            // Guard: apply all noise filters to the extracted topic name
            if (topicName != null && (isNoiseLine(topicName)
                    || isCoOutcomeLine(topicName, inCoSection)
                    || isContinuationFragment(topicName))) {
                topicName = null;
            }

            if (topicName != null && !topicName.isEmpty()) {
                if (currentSubject == null) {
                    currentSubject = new ParsedSubject("Syllabus");
                    subjects.add(currentSubject);
                }
                if (currentUnit == null) {
                    currentUnit = new ParsedUnit("Unit 1");
                    currentSubject.units.add(currentUnit);
                }
                currentUnit.topics.add(topicName);
            }
        }

        return subjects;
    }

    // ─── Fallback ─────────────────────────────────────────────────────────────

    /** Fallback parsing for unstructured or OCR text */
    public List<ParsedSubject> parseFallback(String defaultSubjectName, String rawText) {
        List<ParsedSubject> subjects = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) return subjects;

        ParsedSubject subject = new ParsedSubject(defaultSubjectName);
        ParsedUnit    unit    = new ParsedUnit("Unit 1 - Topics");

        String[] lines   = rawText.split("\\r?\\n");
        boolean tailMode = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (tailMode) continue;
            if (isTailMarker(line)) { tailMode = true; continue; }
            if (line.length() > 2) {
                String cleaned = line.replaceFirst("^[0-9]+[.)\\s]+", "")
                                     .replaceFirst("^[•\\-–*]\\s+", "").trim();
                if (!cleaned.isEmpty() && !isNoiseLine(cleaned)
                        && !isContinuationFragment(cleaned)) {
                    unit.topics.add(cleaned);
                }
            }
        }
        if (!unit.topics.isEmpty()) {
            subject.units.add(unit);
            subjects.add(subject);
        }
        return subjects;
    }

    // ─── Classification helpers ───────────────────────────────────────────────

    /**
     * Returns true if the line marks the start of a trailing non-curriculum
     * section after which no real topics appear.
     */
    private boolean isTailMarker(String line) {
        String lower = line.toLowerCase(Locale.ROOT).trim();
        // Collapse spaces and normalize OCR 0 -> o for "text books" / "reference books" variants
        String compact = lower.replaceAll("\\s+", "").replace('0', 'o');
        return compact.contains("referencebooks") ||
               compact.contains("textbooks") ||
               compact.contains("webreferences") ||
               compact.contains("onlinereferences") ||
               compact.contains("onlineresources") ||
               compact.contains("evaluationscheme") ||
               compact.contains("gradingcriteria") ||
               compact.contains("courseassessment") ||
               compact.contains("questionpaperpattern") ||
               compact.contains("marksdistribution");
    }

    /**
     * Returns true if the line is a COURSE OUTCOME section header.
     * Covers: "COURSE OUTCOME", "COURSE OUTCOMES", "Course Outcomes:", etc.
     */
    private boolean isCourseOutcomeHeader(String lowerLine) {
        String compact = lowerLine.replaceAll("\\s+", "");
        return compact.startsWith("courseoutcome") ||
               compact.startsWith("courseoutcomes");
    }

    /**
     * Returns true if the line is a CO code or outcome prose that should be
     * suppressed. When inCoSection is true, outcome-verb prefixes are also noise.
     *
     * OCR variants handled:
     *   CO1, CO2, C01, Col, COS, COZ, C0I, C04:, Col :, etc.
     */
    private boolean isCoOutcomeLine(String line, boolean inCoSection) {
        // Normalise for structural OCR matching
        String norm = normaliseCo(line.toUpperCase(Locale.ROOT).trim());
        if (CO_CODE_PATTERN.matcher(norm).matches()) return true;

        if (inCoSection) {
            // Inside a COURSE OUTCOME block, lines starting with outcome verbs are noise
            String lw = line.toLowerCase(Locale.ROOT).trim();
            for (String verb : OUTCOME_VERBS) {
                if (lw.startsWith(verb)) return true;
            }
            // Also drop numbered items inside a CO section that are outcome prose
            // (e.g. "1. Understand the features of C++")
            Matcher nm = NUMBERED_ITEM.matcher(line);
            if (nm.matches()) {
                String content = nm.group(1).trim().toLowerCase(Locale.ROOT);
                for (String verb : OUTCOME_VERBS) {
                    if (content.startsWith(verb)) return true;
                }
                if (content.contains("students will be able") ||
                    content.contains("student will be able") ||
                    content.contains("at the end of this course") ||
                    content.contains("after completing")) return true;
            }
        }
        return false;
    }

    /**
     * Normalise obvious single-character OCR substitutions in a CO code token.
     * Applied ONLY to the leading 4-5 characters (the "COx" prefix).
     *  0 → O, 1/l/I → I (becomes digit again for matching), 5 → S, Z → O
     */
    private String normaliseCo(String upper) {
        if (upper.length() < 2) return upper;
        // Only normalise the first 3 characters (the C, O/0, digit cluster)
        char c0 = upper.charAt(0);
        char c1 = upper.charAt(1);
        char c2 = upper.length() > 2 ? upper.charAt(2) : 0;

        // c0 must be 'C'
        if (c0 != 'C') return upper;

        // Normalise c1: 'O', '0', 'o' all → 'O'
        char normC1 = (c1 == '0' || c1 == 'o' || c1 == 'O') ? 'O' : c1;

        // Normalise c2 to a representative digit:
        // 'I', 'l', 'L', '1' → '1'; 'S', '5' → '5'; 'Z' → '2' (best guess)
        char normC2 = c2;
        if (c2 == 'I' || c2 == 'l' || c2 == 'L') normC2 = '1';
        else if (c2 == 'S') normC2 = '5';
        else if (c2 == 'Z') normC2 = '2';

        return "" + c0 + normC1 + normC2 + upper.substring(3);
    }

    /**
     * Returns true if the line is administrative/metadata/outcome/exam noise.
     * This is the broad, always-on filter.
     */
    private boolean isNoiseLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT).trim();
        String compact = lower.replaceAll("\\s+", "").replace('0', 'o');  // for collapsed matching

        // ── Reference / evaluation / faculty / schedule blocks ─────────────
        if (compact.contains("nameofthefaculty") ||
            lower.contains("e-mail:") ||
            lower.contains("linkedin:") ||
            compact.contains("classschedule") ||
            compact.contains("hoursformeeting") ||
            compact.contains("referencebooks") ||
            compact.contains("textbooks") ||
            compact.contains("webreferences") ||
            compact.contains("onlinereferences") ||
            compact.contains("onlineresources") ||
            compact.contains("evaluationscheme") ||
            compact.contains("sessionalexam") ||
            lower.contains("internal marks") ||
            lower.contains("external exam") ||
            compact.contains("courseassessment") ||
            compact.contains("assessment:theory") ||
            compact.contains("assessmenttheory") ||
            compact.contains("gradingcriteria")) {
            return true;
        }

        // ── Course outcome / objective headers ─────────────────────────────
        if (compact.startsWith("courseoutcome") ||
            compact.startsWith("courseoutcomes") ||
            compact.startsWith("courseobjective") ||
            compact.startsWith("courseobjectives") ||
            lower.contains("at the end of this course") ||
            lower.contains("students will be able") ||
            lower.contains("student will be able") ||
            lower.contains("after completing this course")) {
            return true;
        }

        // ── CO code lines (structural, OCR-hardened) ───────────────────────
        String normUpper = normaliseCo(line.toUpperCase(Locale.ROOT).trim());
        if (CO_CODE_PATTERN.matcher(normUpper).matches()) return true;

        // ── Prerequisites (various spellings) ──────────────────────────────
        if (compact.startsWith("prerequisite") ||
            compact.startsWith("prerequisites") ||
            compact.startsWith("pre-requisite") ||
            compact.startsWith("pre-requisites") ||
            compact.startsWith("prereouisite") ||   // OCR: "Pre-Reouisites"
            lower.contains("consent of the instructor") ||
            lower.contains("department approval")) {
            return true;
        }

        // ── Credit / LTPC structures ────────────────────────────────────────
        // Normalised: collapse all spaces, uppercase
        String compactUpper = compact.toUpperCase(Locale.ROOT);
        if (isLtpcLine(compactUpper)) return true;

        // ── Contact hours / definition ─────────────────────────────────────
        if (compact.startsWith("contacthours") ||
            compact.equals("contacthours") ||
            lower.equals("contact hours") ||
            lower.startsWith("contact hours")) {
            return true;
        }

        // ── Course-code lines (structural) ─────────────────────────────────
        // e.g. "20MSS33", "20mss33 - Advanced Data Structures", "25SS212"
        String upperTrimmed = line.toUpperCase(Locale.ROOT).trim();
        if (COURSE_CODE_LINE.matcher(upperTrimmed).matches()) return true;

        // ── Credit / type / semester metadata ─────────────────────────────
        if (compact.contains("coursetype") ||
            compact.contains("professionalcore") ||
            compact.contains("openelective") ||
            compact.contains("professionalelective") ||
            lower.startsWith("semester") ||
            lower.startsWith("year ")) {
            return true;
        }

        // ── "Course Code", "Course Title", "Course Name", "Subject Code" etc.
        if (compact.startsWith("coursecode") ||
            compact.startsWith("coursetitle") ||
            compact.startsWith("coursename") ||
            compact.startsWith("subjectcode") ||
            compact.startsWith("subjectname") ||
            compact.startsWith("subjecttitle")) {
            return true;
        }

        // ── Exam-lens: marks, time, Bloom's ───────────────────────────────
        if (compact.contains("questionpaperpattern") ||
            compact.contains("bloom") ||
            compact.contains("klevel") ||
            compact.contains("k-level")) {
            return true;
        }
        if (lower.matches("^\\s*marks\\s*[:._\\-]?.*") ||
            lower.matches("^\\s*total\\s*(marks)?\\s*[:._\\-]?.*") ||
            lower.matches("^\\s*time\\s*[:._\\-]?.*") ||
            lower.equals("marks") || lower.equals("total")) {
            return true;
        }

        // ── Page markers ───────────────────────────────────────────────────
        if (lower.matches("^---\\s*page\\s*\\d+\\s*---$") ||
            lower.matches("^page\\s*\\d+$")) {
            return true;
        }

        // ── Topic/Lecture table header row ─────────────────────────────────
        if (compact.contains("topiclecturehours") ||
            (compact.contains("topic") && compact.contains("lecture") && compact.contains("hours"))) {
            return true;
        }

        return false;
    }

    /**
     * Detect LTPC / credit-line variants.
     * Input is COMPACT (spaces collapsed) and UPPERCASE.
     * Matches: LTPC, LTPc, LTPC3003, L T P C, 3003, 3 0 0 3, LTPC 3 0 0 3, etc.
     */
    private boolean isLtpcLine(String compactUpper) {
        // Pure LTPC token (with or without numbers appended)
        if (compactUpper.matches("^L?T?P?C?LTPC.*")) return true;  // catches LTPC...
        if (compactUpper.startsWith("LTPC")) return true;
        if (compactUpper.startsWith("LTPCS")) return true;  // OCR: LTPcS
        // "L T P C" spread – already collapsed: "LTPC"  handled above
        // "3 0 0 3" or "3003" as standalone credit numbers
        if (compactUpper.matches("^[0-9][0-9][0-9][0-9]$")) return true;  // e.g. 3003, 4004
        if (compactUpper.matches("^[0-9]\\s*[0-9]\\s*[0-9]\\s*[0-9]$")) return true;
        return false;
    }

    /**
     * Returns true for an ALL-CAPS line that represents a legitimate subject/
     * section heading (not noise). Applies isNoiseLine() to guard.
     */
    private boolean isAllCapsSubjectLine(String line) {
        if (line.length() < 4 || line.length() > 70) return false;

        int upperCount = 0, letterCount = 0;
        for (char c : line.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) upperCount++;
            }
        }
        boolean isAllCaps = letterCount >= 4 && ((float) upperCount / letterCount) >= 0.85f;
        if (!isAllCaps) return false;

        // Must not be any kind of noise
        if (isNoiseLine(line)) return false;

        // A course-code-only line should not be a subject
        String upperTrimmed = line.toUpperCase(Locale.ROOT).trim();
        if (COURSE_CODE_LINE.matcher(upperTrimmed).matches()) return false;

        return true;
    }

    /**
     * Returns true if a plain (non-numbered, non-bulleted) line should be
     * tentatively treated as a topic. Rejects obvious metadata/fragment lines.
     */
    private boolean isAcceptablePlainLine(String line) {
        if (line.length() <= 4) return false;  // Too short
        if (line.contains(":")) return false;  // Likely a key-value metadata row
        // Lines with only uppercase letters are likely noise headers, handled above
        return true;
    }

    /**
     * Returns true if the line looks like a OCR/PDF line-continuation fragment
     * rather than a self-contained topic.
     *
     * Heuristics:
     *  - Starts with a lowercase continuation word
     *  - Very short single word that is not in the allowed-short set
     *  - Starts with a lowercase letter (PDF continuation lines almost always do)
     */
    private boolean isContinuationFragment(String line) {
        if (line == null || line.isEmpty()) return false;
        String trimmed = line.trim();
        String lower   = trimmed.toLowerCase(Locale.ROOT);

        // Single-word lines: only keep if they are in the allowed set
        if (!lower.contains(" ")) {
            return !ALLOWED_SHORT_TOPICS.contains(lower);
        }

        // Lines starting with a lowercase continuation word
        String firstWord = lower.split("\\s+")[0];
        if (CONTINUATION_STARTERS.contains(firstWord)) {
            // Even long lines that start with continuation words are fragments.
            // (Legitimate topics generally start with a capitalised noun.)
            return true;
        }

        // Lines that start with a lowercase letter are almost always continuation
        // fragments from the previous OCR paragraph (PDF wrapping).
        // Exception: if > 60 chars it could be a long descriptive topic — keep it.
        char first = trimmed.charAt(0);
        if (Character.isLowerCase(first) && trimmed.length() < 60) {
            return true;
        }

        return false;
    }

    // ─── String utilities ─────────────────────────────────────────────────────

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return "";
        String[] words = input.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    // ─── DTO classes ──────────────────────────────────────────────────────────

    public static class ParsedSubject {
        public String name;
        public List<ParsedUnit> units = new ArrayList<>();
        public ParsedSubject(String name) { this.name = name; }
    }

    public static class ParsedUnit {
        public String name;
        public List<String> topics = new ArrayList<>();
        public ParsedUnit(String name) { this.name = name; }
    }
}
