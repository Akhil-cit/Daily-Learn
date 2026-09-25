package com.example.daily_learn.domain.ai;

import android.util.Log;

/**
 * A thin abstraction over the LlamaCppPoC JNI layer.
 * Handles model loading, unloading, and basic generation.
 */
public class LocalLLMEngine {
    private static final String TAG = "LocalLLMEngine";
    private final LlamaCppPoC llamaCppPoC;
    private boolean isLoaded = false;

    public LocalLLMEngine() {
        this.llamaCppPoC = new LlamaCppPoC();
    }

    /**
     * Loads the model at the specified path.
     * @param modelPath The absolute path to the .gguf model file.
     * @param gpuLayers Number of layers to offload to GPU.
     * @return true if loaded successfully, false otherwise.
     */
    public boolean loadModel(String modelPath, int gpuLayers) {
        if (isLoaded) {
            Log.w(TAG, "Model is already loaded. Unloading previous model first.");
            unloadModel();
        }
        
        Log.i(TAG, "Loading model via LlamaCppPoC: " + modelPath);
        isLoaded = llamaCppPoC.init(modelPath, gpuLayers);
        return isLoaded;
    }

    /**
     * Generates a response for the given prompt.
     * @param prompt The input prompt.
     * @return The generated text from the model.
     */
    public String generate(String prompt, int maxTokens) {
        if (!isLoaded) {
            return "Error: Engine not initialized.";
        }
        return llamaCppPoC.generate(prompt, maxTokens);
    }

    public void generateStreaming(String prompt, int maxTokens, LlamaCppPoC.StreamCallback callback) {
        if (!isLoaded) {
            if (callback != null) callback.onError("Error: Engine not initialized.");
            return;
        }
        llamaCppPoC.generateStreaming(prompt, maxTokens, callback);
    }

    /**
     * Releases the native model context.
     */
    public void unloadModel() {
        if (isLoaded) {
            llamaCppPoC.release();
            isLoaded = false;
            Log.i(TAG, "Model unloaded via LlamaCppPoC.");
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}
