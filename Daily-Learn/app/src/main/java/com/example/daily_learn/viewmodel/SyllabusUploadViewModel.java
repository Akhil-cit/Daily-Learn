package com.example.daily_learn.viewmodel;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.daily_learn.domain.parser.SyllabusImageParser;
import com.example.daily_learn.domain.parser.SyllabusPdfParser;
import com.example.daily_learn.domain.parser.SyllabusTextStructurer;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;

public class SyllabusUploadViewModel extends AndroidViewModel {

    private final SyllabusPdfParser pdfParser;
    private final SyllabusTextStructurer textStructurer;
    private final SyllabusImageParser imageParser;

    public final MutableLiveData<List<SyllabusTextStructurer.ParsedSubject>> parsedSubjects =
        new MutableLiveData<>();
    public final MutableLiveData<String> rawTextResult = new MutableLiveData<>();
    public final MutableLiveData<Boolean> parseSuccess = new MutableLiveData<>(false);
    public final MutableLiveData<String> parseErrorMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> loading      = new MutableLiveData<>(false);

    public SyllabusUploadViewModel(@NonNull Application app) {
        super(app);
        pdfParser       = new SyllabusPdfParser();
        textStructurer  = new SyllabusTextStructurer();
        imageParser     = new SyllabusImageParser(app);
    }

    public void parseImage(Uri imageUri) {
        loading.setValue(true);
        imageParser.parseFromUri(imageUri, new SyllabusImageParser.Callback() {
            @Override
            public void onSuccess(List<SyllabusTextStructurer.ParsedSubject> subjects, String rawExtractedText) {
                loading.postValue(false);
                rawTextResult.postValue(rawExtractedText);
                if (subjects == null || subjects.isEmpty()) {
                    parseErrorMessage.postValue("Could not structure subjects from image. Try pasting or manual entry.");
                } else {
                    parsedSubjects.postValue(subjects);
                    parseSuccess.postValue(true);
                }
            }

            @Override
            public void onFailure(Exception e) {
                loading.postValue(false);
                parseErrorMessage.postValue(e.getMessage() != null ? e.getMessage() : "Failed to extract text from image");
            }
        });
    }

    public void parseImageBitmap(Bitmap bitmap) {
        loading.setValue(true);
        imageParser.parseFromBitmap(bitmap, new SyllabusImageParser.Callback() {
            @Override
            public void onSuccess(List<SyllabusTextStructurer.ParsedSubject> subjects, String rawExtractedText) {
                loading.postValue(false);
                rawTextResult.postValue(rawExtractedText);
                if (subjects == null || subjects.isEmpty()) {
                    parseErrorMessage.postValue("Could not structure subjects from image. Try pasting or manual entry.");
                } else {
                    parsedSubjects.postValue(subjects);
                    parseSuccess.postValue(true);
                }
            }

            @Override
            public void onFailure(Exception e) {
                loading.postValue(false);
                parseErrorMessage.postValue(e.getMessage() != null ? e.getMessage() : "Failed to extract text from image");
            }
        });
    }

    public void parsePdf(InputStream inputStream) {
        loading.setValue(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            String rawText = pdfParser.extractText(inputStream);
            rawTextResult.postValue(rawText);
            List<SyllabusTextStructurer.ParsedSubject> parsed = textStructurer.parse(rawText);
            if (parsed.isEmpty()) {
                parsed = textStructurer.parseFallback("Uploaded Syllabus", rawText);
            }

            if (parsed.isEmpty()) {
                parseErrorMessage.postValue("No subjects found in the PDF. Please check the file or paste text.");
            } else {
                parsedSubjects.postValue(parsed);
                parseSuccess.postValue(true);
            }
            loading.postValue(false);
        });
    }

    public void parseText(String rawText) {
        loading.setValue(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            rawTextResult.postValue(rawText);
            List<SyllabusTextStructurer.ParsedSubject> parsed = textStructurer.parse(rawText);
            if (parsed.isEmpty()) {
                parsed = textStructurer.parseFallback("Custom Syllabus", rawText);
            }

            if (parsed.isEmpty()) {
                parseErrorMessage.postValue("Unable to detect topics. Please format each topic on a new line.");
            } else {
                parsedSubjects.postValue(parsed);
                parseSuccess.postValue(true);
            }
            loading.postValue(false);
        });
    }
}
