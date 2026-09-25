package com.example.daily_learn.domain.parser;

import java.io.InputStream;

/**
 * Wraps PDF text extraction. In Phase 2, this would use PdfBox-Android.
 * Returns raw text from the PDF for the SyllabusTextStructurer to parse.
 *
 * NOTE: PdfBox-Android (com.tom_roush:pdfbox-android) can be added to
 * build.gradle when ready. This stub returns empty text so the app routes
 * to the manual editor, which is the specified fallback for parse failures.
 */
public class SyllabusPdfParser {

    /**
     * Extract raw text from a PDF InputStream.
     *
     * @param inputStream InputStream of the PDF file
     * @return            Extracted text, or empty string if extraction fails
     */
    public String extractText(InputStream inputStream) {
        if (inputStream == null) return "";

        // Stub: In a full Phase 2 implementation, initialize PDFBox here:
        // PDFBoxResourceLoader.init(context);
        // PDDocument document = PDDocument.load(inputStream);
        // PDFTextStripper stripper = new PDFTextStripper();
        // return stripper.getText(document);

        // Return empty string → triggers manual editor fallback in SyllabusUploadFragment
        return "";
    }
}
