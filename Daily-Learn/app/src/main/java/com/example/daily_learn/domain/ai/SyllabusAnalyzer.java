package com.example.daily_learn.domain.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intelligent AI Syllabus Analyzer.
 * Parses documents (PDF, Image, Text), extracts true learning content,
 * filters out administrative/faculty/reference noise, and groups concepts into daily topics.
 */
public class SyllabusAnalyzer {

    public interface AnalysisCallback {
        void onProgress(String statusMessage, int progressPercent);
        void onSuccess(SyllabusAnalysisResult result);
        void onFailure(Exception e);
    }

    private final Context context;
    private TextRecognizer recognizer;

    public SyllabusAnalyzer(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    private TextRecognizer getRecognizer() {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
        return recognizer;
    }

    /**
     * Analyze syllabus from a document URI (PDF or Image)
     */
    public void analyzeUri(Uri uri, int targetDays, AnalysisCallback callback) {
        if (context == null || uri == null) {
            callback.onFailure(new IllegalArgumentException("Invalid URI or Context"));
            return;
        }

        callback.onProgress("Inspecting document...", 10);
        String mimeType = context.getContentResolver().getType(uri);

        if (mimeType != null && mimeType.contains("pdf")) {
            analyzePdfUri(uri, targetDays, callback);
        } else {
            // Assume image (JPG, PNG, etc.)
            analyzeImageUri(uri, targetDays, callback);
        }
    }

    /**
     * Analyze syllabus from raw text
     */
    public void analyzeText(String rawText, int targetDays, AnalysisCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("Filtering learning content...", 40);
                SyllabusAnalysisResult result = processRawText(rawText, targetDays);
                callback.onProgress("Done!", 100);
                callback.onSuccess(result);
            } catch (Exception e) {
                callback.onFailure(e);
            }
        }).start();
    }

    private void analyzeImageUri(Uri uri, int targetDays, AnalysisCallback callback) {
        try {
            callback.onProgress("Running OCR on image...", 25);
            InputImage image = InputImage.fromFilePath(context, uri);
            getRecognizer().process(image)
                    .addOnSuccessListener(visionText -> {
                        callback.onProgress("Structuring topics...", 70);
                        String text = visionText.getText();
                        SyllabusAnalysisResult result = processRawText(text, targetDays);
                        callback.onSuccess(result);
                    })
                    .addOnFailureListener(callback::onFailure);
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    private void analyzePdfUri(Uri uri, int targetDays, AnalysisCallback callback) {
        new Thread(() -> {
            File tempPdf = null;
            ParcelFileDescriptor pfd = null;
            PdfRenderer renderer = null;
            try {
                callback.onProgress("Loading PDF pages...", 20);
                tempPdf = File.createTempFile("syllabus_doc", ".pdf", context.getCacheDir());
                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(tempPdf)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }

                pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY);
                renderer = new PdfRenderer(pfd);
                int pageCount = renderer.getPageCount();

                StringBuilder fullExtractedText = new StringBuilder();

                for (int i = 0; i < pageCount; i++) {
                    final int pageIdx = i + 1;
                    callback.onProgress("Scanning page " + pageIdx + " of " + pageCount + "...", 25 + (int) ((i * 50f) / pageCount));

                    PdfRenderer.Page page = renderer.openPage(i);
                    // Render page at 2x scale for crisp OCR accuracy
                    Bitmap bitmap = Bitmap.createBitmap(page.getWidth() * 2, page.getHeight() * 2, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(0xFFFFFFFF);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close();

                    InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
                    try {
                        com.google.android.gms.tasks.Task<com.google.mlkit.vision.text.Text> task = getRecognizer().process(inputImage);
                        com.google.mlkit.vision.text.Text textResult = com.google.android.gms.tasks.Tasks.await(task);
                        if (textResult != null && textResult.getText() != null) {
                            fullExtractedText.append("\n--- PAGE ").append(pageIdx).append(" ---\n");
                            fullExtractedText.append(textResult.getText()).append("\n");
                        }
                    } catch (Exception ignored) {}
                    bitmap.recycle();
                }

                callback.onProgress("Synthesizing learning topics...", 85);
                SyllabusAnalysisResult result = processRawText(fullExtractedText.toString(), targetDays);
                callback.onProgress("Complete!", 100);
                callback.onSuccess(result);

            } catch (Exception e) {
                callback.onFailure(e);
            } finally {
                if (renderer != null) try { renderer.close(); } catch (Exception ignored) {}
                if (pfd != null) try { pfd.close(); } catch (Exception ignored) {}
                if (tempPdf != null && tempPdf.exists()) tempPdf.delete();
            }
        }).start();
    }

    /**
     * Pure Java parsing logic for extracting, filtering, and daily-grouping syllabus content.
     */
    public SyllabusAnalysisResult processRawText(String rawText, int targetDays) {
        if (targetDays <= 0) targetDays = 20;
        if (rawText == null) rawText = "";

        // 1. Extract Course Title & Code
        String[] courseInfo = extractCourseInfo(rawText);
        String courseCode = courseInfo[0];
        String courseName = courseInfo[1];

        // 2. Filter out non-learning content & isolate curriculum sections
        List<RawSection> sections = extractCurriculumSections(rawText);

        // 3. If no distinct sections found, extract topics line by line (fallback)
        if (sections.isEmpty()) {
            sections = extractFallbackSections(rawText);
        }

        // 4. Tokenize & clean atomic topics from sections
        List<ExtractedTopicItem> allTopics = new ArrayList<>();
        for (RawSection s : sections) {
            List<String> subtopics = tokenizeSubtopics(s.content);
            for (String sub : subtopics) {
                if (isValidTopic(sub)) {
                    allTopics.add(new ExtractedTopicItem(s.title, s.lectureHours, sub));
                }
            }
        }

        // If very few topics found, try splitting raw non-empty lines
        if (allTopics.isEmpty()) {
            String[] lines = rawText.split("\n");
            for (String l : lines) {
                String clean = cleanTopicString(l);
                if (isValidTopic(clean) && !isNoiseLine(clean)) {
                    allTopics.add(new ExtractedTopicItem(courseName, 1, clean));
                }
            }
        }

        // 5. Partition atomic topics into targetDays groups
        List<DailyTopicGroup> dailyGroups = distributeIntoDays(allTopics, targetDays);

        SyllabusAnalysisResult result = new SyllabusAnalysisResult(courseName, courseCode, targetDays, dailyGroups);
        result.rawText = rawText;
        return result;
    }

    private String[] extractCourseInfo(String text) {
        String code = "";
        String name = "Syllabus Learning Plan";

        // Pattern 1: e.g. "25SS212 / OBJECT ORIENTED PROGRAMMING", "CS8391 / DATA STRUCTURES",
        // or "20MSS33 ADVANCED DATA STRUCTURES". Code MUST contain at least one digit.
        Pattern p1 = Pattern.compile("(?m)^\\s*(?=[A-Za-z0-9]*\\d)([A-Za-z0-9]{5,10})\\s*(?:[/–-]\\s*)?([A-Za-z][^\r\n/–-]{2,60})");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            code = m1.group(1).trim().toUpperCase(Locale.ROOT);
            name = toTitleCase(m1.group(2).trim());
            return new String[]{code, name};
        }

        // Pattern 2: Course Title / Subject: ...
        Pattern p2 = Pattern.compile("(?:Course|Subject)\\s*(?:Name|Title|Description)?\\s*[:–-]\\s*([^\r\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            name = toTitleCase(m2.group(1).trim());
        }

        // Look for common course codes e.g. CS8391, 25SS212
        Pattern pCode = Pattern.compile("\\b([0-9]{2}[A-Z]{2,4}[0-9]{2,4}|[A-Z]{2,4}[0-9]{3,5})\\b");
        Matcher mCode = pCode.matcher(text);
        if (mCode.find()) {
            code = mCode.group(1).trim().toUpperCase(Locale.ROOT);
        }

        return new String[]{code, name};
    }

    private static class RawSection {
        String title;
        int lectureHours;
        String content;

        RawSection(String title, int lectureHours, String content) {
            this.title = title;
            this.lectureHours = lectureHours > 0 ? lectureHours : 3;
            this.content = content;
        }
    }

    private static class ExtractedTopicItem {
        String sectionName;
        int lectureHours;
        String topicName;

        ExtractedTopicItem(String sectionName, int lectureHours, String topicName) {
            this.sectionName = sectionName;
            this.lectureHours = lectureHours;
            this.topicName = topicName;
        }
    }

    /**
     * Extracts curriculum sections while stripping non-learning boilerplate.
     */
    private List<RawSection> extractCurriculumSections(String text) {
        List<RawSection> sections = new ArrayList<>();

        // Preprocess embedded headers: "TREES : Definition..." -> "TREES\nDefinition..."
        Pattern embeddedHeader = Pattern.compile("(?m)^([A-Z\\s]{4,40})\\s*[:\\-]\\s*(.+)$");
        Matcher em = embeddedHeader.matcher(text);
        text = em.replaceAll("$1\n$2");

        // Prune known non-learning tail sections (Reference books, Evaluation scheme, etc.)
        String learningBody = text;
        String[] endCutoffs = {
                "(?i)(?:iv|v|vi)?\\)?\\s*(?:text|reference|recommended|suggested|web|online)\\s*(?:b[o0]{2}ks?|references?|resources?|links?|materials?)",
                "(?i)(?:v\\)\\s*)?evaluation\\s*scheme",
                "(?i)grading\\s*criteria",
                "(?i)course\\s*assessment",
                "(?i)question\\s*paper\\s*pattern"
        };
        for (String cutoff : endCutoffs) {
            Pattern p = Pattern.compile(cutoff);
            Matcher m = p.matcher(learningBody);
            if (m.find()) {
                learningBody = learningBody.substring(0, m.start());
            }
        }

        // If there is a dedicated "Topic/Unit/Chapter Layout" header, focus on it
        Pattern layoutPattern = Pattern.compile("(?:Topic/Unit/Chapter Layout|Unit\\s*I|Module\\s*1|Chapter\\s*1)", Pattern.CASE_INSENSITIVE);
        Matcher mLayout = layoutPattern.matcher(learningBody);
        if (mLayout.find()) {
            learningBody = learningBody.substring(mLayout.start());
        }

        String[] rawLines = learningBody.split("\n");
        String currentTitle = null;
        int currentHours = 0;
        StringBuilder currentContent = new StringBuilder();
        boolean inOutcomeBlock = false;
        boolean inPrereqBlock = false;

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.contains("COURSE OUTCOME") || upper.contains("COURSE OUTCOMES") || upper.contains("COURSE OBJECTIVE") || upper.contains("COURSE OBJECTIVES")) {
                inOutcomeBlock = true;
                inPrereqBlock = false;
                continue;
            }
            if (upper.contains("PRE-REQUISITE") || upper.contains("PREREQUISITE") || upper.startsWith("PRE REQUISITE")) {
                inPrereqBlock = true;
                inOutcomeBlock = false;
                continue;
            }

            if (isNoiseLine(trimmed)) continue;

            // Check if line contains lecture hours at the end (e.g. "... 6" or "... 8")
            int hours = extractTrailingHours(trimmed);
            if (hours > 0) {
                currentHours = hours;
                trimmed = removeTrailingHours(trimmed);
            }

            // Check if line is a major unit/section title
            if (isSectionHeader(trimmed)) {
                inOutcomeBlock = false;
                inPrereqBlock = false;
                if (currentTitle != null && currentContent.length() > 0) {
                    sections.add(new RawSection(currentTitle, currentHours, currentContent.toString()));
                    currentContent.setLength(0);
                    currentHours = 0;
                }
                currentTitle = toTitleCase(cleanSectionTitle(trimmed));
                if (hours > 0) currentHours = hours;
                continue;
            }

            // Skip lines that belong to outcomes or prerequisites
            if (inOutcomeBlock) {
                // Bullets, lowercase continuation lines (e.g. "exception handling..."), or non-headers stay in outcome block
                if (Character.isLowerCase(trimmed.charAt(0)) || trimmed.matches("^[•*\\-–—>].*") || !isSectionHeader(trimmed)) {
                    continue;
                }
            }
            if (inPrereqBlock) {
                if (!isSectionHeader(trimmed)) {
                    continue;
                }
            }

            // Discard preamble lines before the first actual curriculum section header
            if (currentTitle == null) {
                continue;
            }

            currentContent.append(" ").append(trimmed);
        }

        if (currentTitle != null && currentContent.length() > 0) {
            sections.add(new RawSection(currentTitle, currentHours, currentContent.toString()));
        }

        return sections;
    }

    private List<RawSection> extractFallbackSections(String text) {
        List<RawSection> sections = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty() && !isNoiseLine(t)) {
                sb.append(t).append(" - ");
            }
        }
        if (sb.length() > 0) {
            sections.add(new RawSection("Curriculum Topics", 20, sb.toString()));
        }
        return sections;
    }

    private boolean isSectionHeader(String line) {
        if (line.length() < 3 || line.length() > 70) return false;
        // Never allow bullets or list items to be section headers
        if (line.matches("^[•*\\-–—>]\\s*.*") || line.matches("^\\d+[.)]\\s*.*")) return false;
        // Never allow comma-separated phrases (like "HTML5, CSS and BOOTSTRAP") to be section headers
        if (line.contains(",") || line.contains(";")) return false;

        String upper = line.toUpperCase(Locale.ROOT);

        if (upper.startsWith("UNIT ") || upper.startsWith("MODULE ") || upper.startsWith("CHAPTER ")) {
            return true;
        }

        // Reject lines containing outcome / instructional prose words
        if (upper.contains("USING") || upper.contains("UNDERSTAND") || upper.contains("CREATE")
                || upper.contains("DESIGN") || upper.contains("EMPLOY") || upper.contains("ESTABLISH")
                || upper.contains("STUDENT") || upper.contains("PURPOSE") || upper.contains("CONSENT")) {
            return false;
        }

        // All-uppercase headings like "PRINCIPLES OF OBJECT ORIENTED PROGRAMMING", "FUNCTIONS IN C++", "STREAMS AND FILES", "INHERITANCE AND POLYMORPHISM", "TEMPLATES AND EXCEPTIONS"
        int upperCount = 0;
        int letterCount = 0;
        for (char c : line.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) upperCount++;
            }
        }

        boolean isAllCaps = letterCount >= 4 && ((float) upperCount / letterCount) >= 0.85f;
        if (isAllCaps) {
            // Filter out noise titles like "TOPIC LECTURE HOURS", "COURSE OBJECTIVE"
            if (upper.contains("TOPIC") && upper.contains("LECTURE")) return false;
            if (upper.contains("COURSE OBJECTIVE") || upper.contains("CLASS SCHEDULE")) return false;
            if (upper.contains("NAME OF THE FACULTY") || upper.contains("REFERENCE") || upper.contains("TEXT BOOK") || upper.contains("TEXTBOOK") || upper.contains("B0OK") || upper.contains("BOOK")) return false;
            if (upper.contains("EVALUATION SCHEME") || upper.contains("ASSESSMENT")) return false;
            if (upper.contains("COURSE OUTCOME") || upper.contains("COURSE OUTCOMES")) return false;
            if (upper.contains("PREREQUISITE") || upper.contains("PRE-REQUISITE")) return false;
            if (upper.startsWith("QUESTION PAPER")) return false;
            if (upper.startsWith("AT THE END OF THIS COURSE")) return false;
            return true;
        }

        return false;
    }

    private boolean isNoiseLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT).trim();
        String compact = lower.replaceAll("\\s+", "").replace('0', 'o');

        if (compact.contains("nameofthefaculty") ||
                lower.contains("e-mail:") ||
                lower.contains("linkedin:") ||
                compact.contains("classschedule") ||
                compact.contains("hoursformeeting") ||
                compact.contains("courseobjective") ||
                compact.contains("topiclecturehours") ||
                (compact.contains("topic") && compact.contains("lecture") && compact.contains("hours")) ||
                compact.contains("referencebooks") ||
                compact.contains("textbooks") ||
                compact.contains("evaluationscheme") ||
                compact.contains("sessionalexam") ||
                lower.contains("internal marks") ||
                lower.contains("external exam") ||
                compact.contains("courseassessment") ||
                compact.contains("assessment:theory") ||
                compact.contains("assessmenttheory") ||
                compact.contains("gradingcriteria") ||
                lower.matches("^---\\s*page\\s*\\d+\\s*---$") ||
                lower.matches("^page\\s*\\d+$") ||
                lower.startsWith("timings:") ||
                lower.startsWith("timings :")) {
            return true;
        }

        // Structural CO-code detection (OCR-hardened: C01, Col, COL, COS, COS, COZ)
        String normUpper = normaliseCo(line.toUpperCase(Locale.ROOT).trim());
        if (normUpper.matches("^CO[0-9IOSZA]+(\\s*[:.,;\\-\u2013\u2014]|\\s|$).*")) return true;

        // Structural course-code detection (e.g. 20MSS33, 25SS212, CS8391)
        String upperTrimmed = line.toUpperCase(Locale.ROOT).trim();
        if (upperTrimmed.matches("^[A-Z0-9]{2,4}[A-Z]{2,5}[0-9]{2,4}(\\s.*)?$")) return true;

        // Contact Hours
        if (compact.startsWith("contacthours") || lower.equals("contact hours")
                || lower.startsWith("contact hours")) return true;

        return isOutcomeOrMetadataLine(lower) || isExamLensLine(lower);
    }

    /**
     * Normalise obvious OCR swaps in the leading CO prefix of a line.
     * Applied only to uppercase input, only the first 3 characters.
     */
    private String normaliseCo(String upper) {
        if (upper.length() < 2) return upper;
        char c0 = upper.charAt(0);
        char c1 = upper.charAt(1);
        char c2 = upper.length() > 2 ? upper.charAt(2) : 0;
        if (c0 != 'C') return upper;
        char normC1 = (c1 == '0' || c1 == 'o' || c1 == 'O') ? 'O' : c1;
        char normC2 = c2;
        if (c2 == 'I' || c2 == 'l' || c2 == 'L') normC2 = '1';
        else if (c2 == 'S') normC2 = '5';
        else if (c2 == 'Z') normC2 = '2';
        return "" + c0 + normC1 + normC2 + upper.substring(3);
    }

    /**
     * Returns true for lines that are administrative/outcome/metadata noise rather than
     * actual syllabus content: course codes, credits (L T P C), prerequisites, course
     * outcomes (CO1/CO2...), "at the end of this course", Bloom's taxonomy levels, etc.
     * These must never become study-plan topics.
     */
    private boolean isOutcomeOrMetadataLine(String lower) {
        String compact = lower.replaceAll("\\s+", "");

        // Course Outcome / CO codes (handled structurally in isNoiseLine via normaliseCo)
        if (compact.startsWith("courseoutcome") ||
                compact.startsWith("courseoutcomes") ||
                lower.contains("at the end of this course") ||
                lower.contains("students will be able") ||
                lower.contains("student will be able") ||
                lower.contains("after completing this course")) {
            return true;
        }
        // Prerequisites (all spelling variants, normalised)
        if (compact.startsWith("prerequisite") ||
                compact.startsWith("prerequisites") ||
                compact.startsWith("pre-requisite") ||
                compact.startsWith("pre-requisites") ||
                compact.startsWith("prereouisite") ||
                lower.contains("consent of the instructor") ||
                lower.contains("department approval")) {
            return true;
        }
        // Bulleted / statement-style course outcomes (e.g. "• Understand the purpose of Web Servers...")
        if (lower.matches("^[•*\\-–—>]?\\s*(understand|create|design|employ|establish|develop|analyze|analyse|apply|evaluate|assess|demonstrate|compare|differentiate|illustrate)\\s+.*")) {
            if (lower.contains("student") || lower.contains("able to") || lower.contains("skills to") || lower.contains("purpose of") || lower.contains("using") || lower.length() > 35) {
                return true;
            }
        }
        // Outcome prose phrases (common across syllabus outcome bullet continuations)
        if (lower.contains("skills to") ||
            lower.contains("to build") ||
            lower.contains("purpose of") ||
            lower.contains("for a given") ||
            lower.contains("for performance analysis") ||
            lower.contains("solve a given") ||
            lower.contains("trade-off on") ||
            lower.contains("design and validate") ||
            lower.contains("create responsive") ||
            lower.contains("interactive, data") ||
            lower.contains("data-driven sites") ||
            lower.contains("driven sites") ||
            lower.contains("exception handling, validate data")) {
            return true;
        }
        // LTPC / credit structure (normalised, covers LTPc 3003, L T P C, 3003, etc.)
        if (compact.startsWith("ltpc") || compact.startsWith("ltpcs") ||
                compact.matches("^l?t?p?c?ltpc.*") ||
                lower.matches("^\\s*l\\s+t\\s+p\\s+c\\s*.*") ||
                compact.matches("^[0-9][0-9][0-9][0-9]$")) {
            return true;
        }
        // Metadata: semester / year, course type, class, etc.
        if (compact.contains("coursetype") || compact.contains("professionalcore") ||
                compact.contains("openelective") || compact.contains("professionalelective")) {
            return true;
        }
        if (lower.matches("^\\s*semester\\s*.*") || lower.matches("^\\s*year\\s*[0-9].*")) {
            return true;
        }
        // Pure metadata scaffold like "Course Code : CS8391"
        if (compact.startsWith("coursecode") || compact.startsWith("coursename") ||
                compact.startsWith("coursetitle") ||
                compact.startsWith("subjectcode") || compact.startsWith("subjectname")) {
            return true;
        }
        return false;
    }

    /**
     * Exam-lens text: question-paper patterns, marks distribution, Bloom's level / CO
     * mapping tables, marks/total/time exam metadata. Must not become topics.
     */
    private boolean isExamLensLine(String lower) {
        if (lower.contains("question paper pattern") ||
                lower.contains("bloom") ||
                lower.contains("bloom's") ||
                lower.contains("k-level") ||
                lower.contains("k level")) {
            return true;
        }
        if (lower.matches("^\\s*marks\\s*[:._-]?.*") ||
                lower.matches("^\\s*total\\s*(marks)?\\s*[:._-]?.*") ||
                lower.matches("^\\s*time\\s*[:._-]?.*") ||
                lower.equals("marks") || lower.equals("total")) {
            return true;
        }
        return false;
    }

    private int extractTrailingHours(String line) {
        Pattern p = Pattern.compile("(?:\\s+(\\d+)\\s*(?:hours?|hrs?)?|\\b(\\d+)\\b)\\s*$", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(line);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            String num = g1 != null ? g1 : g2;
            try {
                int h = Integer.parseInt(num);
                if (h > 0 && h <= 30) return h;
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private String removeTrailingHours(String line) {
        return line.replaceAll("(?i)\\s*\\b\\d+\\s*(?:hours?|hrs?)?\\s*$", "").trim();
    }

    private String cleanSectionTitle(String title) {
        title = title.replaceAll("(?i)^(?:UNIT|MODULE|CHAPTER)\\s*[0-9IVXLCDM]+\\s*[:–-]?", "").trim();
        title = title.replaceAll("(?i)\\s*-\\s*(?:[0-9]+\\s*(?:hours?|hrs?))?$", "").trim();
        return title;
    }

    /**
     * Splits paragraph/table string into atomic subtopics.
     */
    private List<String> tokenizeSubtopics(String content) {
        List<String> results = new ArrayList<>();
        if (content == null || content.isEmpty()) return results;

        // Split by dashes ( - , – , — ), commas with concept boundaries, or numbered bullets
        String normalized = content
                .replaceAll("[\n\r]+", " | ")
                .replaceAll("\\s*[–—-]\\s*", " | ")
                .replaceAll("(?<=\\w)\\s*\\.\\s*(?=[A-Z])", " | ") // Sentences / periods
                .replaceAll("(?m)^\\s*\\d+[.)]\\s*", " | ") // Numbered lists e.g. 1. 2)
                .replaceAll("\\s*,\\s*(?=[A-Z])", " | "); // Commas before capitalized words

        String[] rawTokens = normalized.split(Pattern.quote("|"));
        for (String t : rawTokens) {
            String clean = cleanTopicString(t);
            if (isValidTopic(clean)) {
                results.add(clean);
            }
        }
        return results;
    }

    private String cleanTopicString(String t) {
        t = t.trim();
        // Remove parenthesized lecture hours e.g. "(6)", "(8)", "(11)", "(5)", "(10)"
        t = t.replaceAll("\\(\\s*\\d+\\s*\\)", " ");
        // Remove leading bullets, numbers, hyphens
        t = t.replaceAll("^[0-9]+[.)\\s–-]+", "");
        t = t.replaceAll("^[•*\\-–—>]+\\s*", "");
        t = t.replaceAll("\\s*:\\s*$", "");
        t = t.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("[\\s.]+$", "").trim();
        return t;
    }

    // Set of single-word topics that are legitimate despite being short
    private static final Set<String> ALLOWED_SHORT_TOPICS = new HashSet<>(Arrays.asList(
            "polymorphism", "inheritance", "encapsulation", "abstraction",
            "recursion", "backtracking", "memoization", "hashing", "sorting",
            "searching", "threading", "concurrency", "synchronization"
    ));

    // Words that indicate a line is a continuation fragment, not a standalone topic
    private static final Set<String> CONTINUATION_STARTERS = new HashSet<>(Arrays.asList(
            "off", "and", "or", "but", "for", "the", "of", "to", "in",
            "with", "by", "on", "at", "from", "into", "about", "an", "a",
            "way", "such", "using", "based", "via"
    ));

    private boolean isValidTopic(String t) {
        if (t == null || t.length() < 3 || t.length() > 120) return false;
        String lower = t.toLowerCase(Locale.ROOT).trim();
        if (isNoiseLine(t)) return false;
        if (lower.equals("topic") || lower.equals("lecture") || lower.equals("hours")
                || lower.equals("definition") || lower.equals("operations")
                || lower.equals("applications") || lower.equals("trees")
                || lower.equals("types") || lower.equals("introduction")
                || lower.equals("overview") || lower.equals("summary")) return false;
        if (lower.matches("^[0-9]+$")) return false;
        if (isOutcomeOrMetadataLine(lower) || isExamLensLine(lower)) return false;
        // Reject single-word topics not in the allowed set
        if (!lower.contains(" ")) {
            return ALLOWED_SHORT_TOPICS.contains(lower);
        }
        // Reject continuation fragments (start with a known continuation word)
        String firstWord = lower.split("\\s+")[0];
        if (CONTINUATION_STARTERS.contains(firstWord)) return false;
        // Reject very short lowercase-start tokens that are not multi-word tech terms
        // (real tokenized topics like "linked list implementation" are fine even though they start lowercase)
        if (Character.isLowerCase(t.trim().charAt(0)) && t.trim().length() < 15 && !lower.contains(" ")) return false;
        return true;
    }

    /**
     * Distribute extracted topics into exactly targetDays daily groups.
     *
     * Section (unit) labels are derived from the majority sectionName of the
     * topics assigned to each day — not simply from the first topic — to avoid
     * wrong unit headings (e.g. Trees topics labelled as "Graphs").
     */
    private List<DailyTopicGroup> distributeIntoDays(List<ExtractedTopicItem> topics, int targetDays) {
        List<DailyTopicGroup> days = new ArrayList<>();
        if (topics.isEmpty()) {
            for (int d = 1; d <= targetDays; d++) {
                days.add(new DailyTopicGroup(d, "Day " + d + " Topic", "General", 2,
                        Arrays.asList("Fundamental Concept " + d)));
            }
            return days;
        }

        int totalCount = topics.size();
        double step = (double) totalCount / targetDays;

        for (int d = 0; d < targetDays; d++) {
            int startIdx = (int) Math.round(d * step);
            int endIdx   = (int) Math.round((d + 1) * step);

            if (startIdx >= totalCount) startIdx = totalCount - 1;
            if (endIdx <= startIdx)     endIdx   = startIdx + 1;
            if (endIdx > totalCount)    endIdx   = totalCount;
            if (d == targetDays - 1)    endIdx   = totalCount;

            List<String> dayTopicList = new ArrayList<>();
            int maxHours = topics.get(startIdx).lectureHours;

            // Determine the dominant section for this day's slice
            // (the section that owns the most topics in [startIdx, endIdx))
            java.util.Map<String, Integer> sectionCounts = new java.util.LinkedHashMap<>();
            for (int i = startIdx; i < endIdx; i++) {
                ExtractedTopicItem item = topics.get(i);
                String sec = item.sectionName != null ? item.sectionName : "General";
                sectionCounts.put(sec, sectionCounts.getOrDefault(sec, 0) + 1);
                String name = item.topicName;
                if (!dayTopicList.contains(name)) dayTopicList.add(name);
            }
            // Pick the section with the highest count
            String dominantSection = "General";
            int maxCount = 0;
            for (java.util.Map.Entry<String, Integer> e : sectionCounts.entrySet()) {
                if (e.getValue() > maxCount) {
                    maxCount = e.getValue();
                    dominantSection = e.getKey();
                }
            }

            if (dayTopicList.isEmpty()) {
                dayTopicList.add(topics.get(Math.min(startIdx, totalCount - 1)).topicName);
            }

            String dayTitle = generateDayTitle(dayTopicList, dominantSection, d + 1);
            days.add(new DailyTopicGroup(d + 1, dayTitle, dominantSection, maxHours, dayTopicList));
        }

        return days;
    }

    private String generateDayTitle(List<String> dayTopics, String sectionName, int dayNumber) {
        if (dayTopics.isEmpty()) {
            return sectionName != null && !sectionName.isEmpty() ? sectionName : "Day " + dayNumber;
        }

        String first = dayTopics.get(0);
        if (first.length() <= 35) {
            return first;
        }

        if (sectionName != null && !sectionName.isEmpty() && !sectionName.equals("Curriculum Topics")) {
            return sectionName + " - Part " + ((dayNumber % 3) + 1);
        }

        return first.substring(0, Math.min(first.length(), 30)) + "...";
    }

    public static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean nextCapital = true;
        for (char c : input.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isSpaceChar(c) || c == '-' || c == '/' || c == '(' || c == ')') {
                nextCapital = true;
                sb.append(c);
            } else if (nextCapital) {
                sb.append(Character.toUpperCase(c));
                nextCapital = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
