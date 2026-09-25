package com.example.daily_learn.domain.parser;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.List;

public class SyllabusImageParser {

    public interface Callback {
        void onSuccess(List<SyllabusTextStructurer.ParsedSubject> subjects, String rawExtractedText);
        void onFailure(Exception e);
    }

    private final Context context;
    private final SyllabusTextStructurer structurer;
    private final TextRecognizer recognizer;

    public SyllabusImageParser(Context context) {
        this.context = context.getApplicationContext();
        this.structurer = new SyllabusTextStructurer();
        this.recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    /** Process syllabus image from URI (camera capture or gallery pick) */
    public void parseFromUri(Uri imageUri, Callback callback) {
        try {
            InputImage image = InputImage.fromFilePath(context, imageUri);
            processImage(image, callback);
        } catch (IOException e) {
            callback.onFailure(e);
        }
    }

    /** Process syllabus image from Bitmap */
    public void parseFromBitmap(Bitmap bitmap, Callback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        processImage(image, callback);
    }

    private void processImage(InputImage image, Callback callback) {
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String rawText = visionText.getText();
                    if (rawText == null || rawText.trim().isEmpty()) {
                        callback.onFailure(new Exception("No text detected in the image. Please ensure good lighting and clear text."));
                        return;
                    }
                    List<SyllabusTextStructurer.ParsedSubject> subjects = structurer.parse(rawText);
                    if (subjects.isEmpty()) {
                        // Fallback: If heuristic didn't find specific UNIT headers, create a single subject with lines
                        subjects = structurer.parseFallback("Scanned Syllabus", rawText);
                    }
                    callback.onSuccess(subjects, rawText);
                })
                .addOnFailureListener(callback::onFailure);
    }
}
