package com.example.daily_learn.data.repository;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.daily_learn.domain.ai.SyllabusAnalysisResult;
import com.example.daily_learn.domain.ai.SyllabusAnalyzer;

/**
 * Repository responsible for orchestrating syllabus analysis requests.
 */
public class SyllabusAnalysisRepository {

    private final SyllabusAnalyzer analyzer;

    public SyllabusAnalysisRepository(@NonNull Context context) {
        this.analyzer = new SyllabusAnalyzer(context);
    }

    public void analyzeDocument(@NonNull Uri uri, int targetDays, @NonNull SyllabusAnalyzer.AnalysisCallback callback) {
        analyzer.analyzeUri(uri, targetDays, callback);
    }

    public void analyzeText(@NonNull String rawText, int targetDays, @NonNull SyllabusAnalyzer.AnalysisCallback callback) {
        analyzer.analyzeText(rawText, targetDays, callback);
    }
}
