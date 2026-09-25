package com.example.daily_learn;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.daily_learn.domain.ai.LlamaCppPoC;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.BufferedReader;
import java.io.FileReader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * PocketPal CPU Parity Benchmark Test Harness for SmolLM2-360M Q4_K_M.
 *
 * Tests 4, 6, and 8 CPU threads on connected OPPO CPH2119 device (Helio P95).
 * - GPU offloading = 0 (n_gpu_layers = 0)
 * - Context = 2048
 * - Batch = 512
 * - KV Cache = F16
 * - Warm-up generation (5 tokens)
 * - Measured generation (50 tokens)
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CpuParityBenchmarkTest {

    private static final String TAG = "CpuParityBench";
    private static final String MODEL_PATH = "/data/local/tmp/benchmark_model.gguf";

    private static String getCpuFreq(int coreIndex) {
        try (BufferedReader br = new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu" + coreIndex + "/cpufreq/scaling_cur_freq"))) {
            String line = br.readLine();
            if (line != null) {
                long khz = Long.parseLong(line.trim());
                return String.format("%.2f GHz", khz / 1000000.0);
            }
        } catch (Exception ignored) {}
        return "N/A";
    }

    private void runThreadBenchmark(int numThreads) {
        Log.i(TAG, "==========================================================");
        Log.i(TAG, "=== STARTING BENCHMARK: " + numThreads + " THREADS ===");
        Log.i(TAG, "==========================================================");

        // Log CPU frequencies before load
        Log.i(TAG, "CPU_FREQ_BEFORE: Little(CPU0)=" + getCpuFreq(0) + " | Big(CPU6)=" + getCpuFreq(6));

        LlamaCppPoC poc = new LlamaCppPoC();
        poc.setNumThreads(numThreads);

        long loadStart = System.currentTimeMillis();
        boolean loaded = poc.init(MODEL_PATH, 0); // 0 GPU layers (CPU-only)
        long loadTime = System.currentTimeMillis() - loadStart;

        assertTrue("Model failed to load for " + numThreads + " threads", loaded);
        Log.i(TAG, "RESULT_THREADS_" + numThreads + "_LOAD_TIME: " + loadTime + " ms");

        // Warm-up prompt (5 tokens max)
        String warmUpPrompt = "<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n<|im_start|>user\nHi<|im_end|>\n<|im_start|>assistant\n";
        long warmUpStart = System.currentTimeMillis();
        String warmUpResponse = poc.generate(warmUpPrompt, 5);
        long warmUpTime = System.currentTimeMillis() - warmUpStart;
        Log.i(TAG, "RESULT_THREADS_" + numThreads + "_WARMUP_TIME: " + warmUpTime + " ms");
        Log.i(TAG, "WARMUP_RESPONSE: " + warmUpResponse);

        // Measured prompt (50 tokens max)
        String prompt = "<|im_start|>system\n" +
                "You are an AI tutor.<|im_end|>\n" +
                "<|im_start|>user\n" +
                "Explain object-oriented programming in detail.<|im_end|>\n" +
                "<|im_start|>assistant\n";

        Log.i(TAG, "CPU_FREQ_BEFORE_GEN: Little(CPU0)=" + getCpuFreq(0) + " | Big(CPU6)=" + getCpuFreq(6));

        long genStart = System.currentTimeMillis();
        String response = poc.generate(prompt, 50);
        long totalElapsed = System.currentTimeMillis() - genStart;

        Log.i(TAG, "CPU_FREQ_AFTER_GEN: Little(CPU0)=" + getCpuFreq(0) + " | Big(CPU6)=" + getCpuFreq(6));

        assertNotNull(response);
        assertFalse(response.startsWith("Error:"));

        Log.i(TAG, "RESULT_THREADS_" + numThreads + "_TOTAL_ELAPSED: " + totalElapsed + " ms");
        Log.i(TAG, "RESPONSE_TEXT:\n" + response);

        poc.release();
        Log.i(TAG, "RESULT_THREADS_" + numThreads + "_COMPLETE");
    }

    @Test
    public void test1_Benchmark_4_Threads() {
        runThreadBenchmark(4);
    }

    @Test
    public void test2_Benchmark_6_Threads() {
        runThreadBenchmark(6);
    }

    @Test
    public void test3_Benchmark_8_Threads() {
        runThreadBenchmark(8);
    }
}
