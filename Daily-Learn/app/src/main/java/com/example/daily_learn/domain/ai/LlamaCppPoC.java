package com.example.daily_learn.domain.ai;

import android.util.Log;

public class LlamaCppPoC {

    static {
        System.loadLibrary("ggml-base");
        System.loadLibrary("ggml-cpu");
        try {
            System.loadLibrary("ggml-vulkan");
        } catch (UnsatisfiedLinkError e) {
            Log.i("LlamaCppPoC", "ggml-vulkan shared library not loaded (CPU-only build)");
        }
        System.loadLibrary("ggml");
        System.loadLibrary("llama");
        System.loadLibrary("native-lib");
    }

    private static final String TAG = "LlamaCppPoC";
    private long nativeContext = 0;

    // JNI Methods
    private native long initNative(String modelPath, int gpuLayers);
    private native String generateNative(long contextPtr, String prompt, int maxTokens);
    private native void generateStreamingNative(long contextPtr, String prompt, int maxTokens, StreamCallback callback);
    private native void releaseNative(long contextPtr);
    // PoC-only: tune CPU thread count for benchmarking (defaults to 4 otherwise).
    private native void setNumThreadsNative(int nThreads);

    public interface StreamCallback {
        void onToken(String token);
        void onComplete(String fullResponse);
        void onError(String error);
    }

    public boolean init(String modelPath) {
        return init(modelPath, 0); // Default to CPU (0 GPU layers)
    }

    public boolean init(String modelPath, int gpuLayers) {
        nativeContext = initNative(modelPath, gpuLayers);
        if (nativeContext != 0) {
            Log.i(TAG, "Native model loaded successfully with " + gpuLayers + " GPU layers. Context Pointer: " + nativeContext);
            return true;
        } else {
            Log.e(TAG, "Failed to load native model with " + gpuLayers + " GPU layers from: " + modelPath);
            return false;
        }
    }

    public String generate(String prompt, int maxTokens) {
        if (nativeContext == 0) {
            return "Error: Model not loaded.";
        }
        return generateNative(nativeContext, prompt, maxTokens);
    }

    public void generateStreaming(String prompt, int maxTokens, StreamCallback callback) {
        if (nativeContext == 0) {
            if (callback != null) callback.onError("Error: Model not loaded.");
            return;
        }
        generateStreamingNative(nativeContext, prompt, maxTokens, callback);
    }

    public void release() {
        if (nativeContext != 0) {
            releaseNative(nativeContext);
            nativeContext = 0;
            Log.i(TAG, "Native context released.");
        }
    }

    // PoC-only: allow a benchmark to set CPU threads (PocketPal parity uses the
    // auto-detected logical-core count). Must be called before init() to apply.
    public void setNumThreads(int nThreads) {
        setNumThreadsNative(nThreads);
    }
}
