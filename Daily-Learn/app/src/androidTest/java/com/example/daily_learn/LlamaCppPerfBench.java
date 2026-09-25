package com.example.daily_learn;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.InstrumentationRegistry;

import com.example.daily_learn.domain.ai.LlamaCppPoC;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Result-capture file: every out() line is also appended to the app's external
 * cache dir (read back via `adb shell cat` since Oppo suppresses app logs).
 */

/**
 * Phase 5.3 PocketPal CPU-parity benchmark for the PoC JNI harness.
 *
 * Reproduces PocketPal/llama.rn evidence on-device:
 *   backend = CPU (llama.rn Android GPU = OpenCL/Adreno-only; MT6779 -> CPU)
 *   n_gpu_layers = 0, n_threads = 8 (auto on 8-core), n_batch = 512,
 *   n_ctx = 2048, cache_type_k/v = f16, use_mmap = true, ARM NEON+dotprod.
 *
 * All metrics go to System.out (captured by gradle test XML) AND logcat, so no
 * logcat dependency is required on the Oppo. Model = cached SmolLM2-360M
 * (NOT the 500M PocketPal baseline => configuration-parity experiment only).
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LlamaCppPerfBench {

    private static final String TAG = "PerfBench";
    private static final String MODEL_PATH = "/data/local/tmp/benchmark_model.gguf";
    private static final int WARMUP_TOKENS = 4;
    private static final int MEASURE_TOKENS = 24;

    private File outFile;

    private void initOutFile() {
        if (outFile != null) return;
        Context ctx = InstrumentationRegistry.getTargetContext();
        File dir = ctx.getExternalCacheDir();
        if (dir == null) dir = ctx.getFilesDir();
        outFile = new File(dir, "perf_bench_output.txt");
    }

    private void resetOutFile() {
        try {
            outFile = null;
            initOutFile();
            outFile.delete();
        } catch (Exception e) {
            Log.e(TAG, "reset failed " + e);
        }
    }

    private void out(String m) {
        Log.i(TAG, m);
        System.out.println("[PerfBench] " + m);
        try {
            initOutFile();
            FileWriter fw = new FileWriter(outFile, true);
            fw.append(m).append("\n");
            fw.flush();
            fw.close();
        } catch (Exception e) {
            Log.e(TAG, "file write failed: " + e);
        }
    }
    // SmolLM2 ChatML template (matches Phase 5.2 PoC).
    private static final String PROMPT =
            "<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n" +
            "<|im_start|>user\nWhat is inheritance in object-oriented programming?<|im_end|>\n" +
            "<|im_start|>assistant\n";

    private String readVmRss() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("VmRSS:") || line.startsWith("VmHWM:") || line.startsWith("VmPeak:")) {
                    return line.trim();
                }
            }
        } catch (IOException e) {
            return "VmRSS: n/a (" + e.getMessage() + ")";
        }
        return "VmRSS: n/a";
    }

    private void dumpConfig(String mode, int gpuLayers, int threads) {
        out("CONFIG mode=" + mode +
                " backend=CPU" + (gpuLayers > 0 ? "(!GPU requested but OpenCL=Adreno-only on MT6779 => falls back to CPU in llama.rn; our Vulkan path uses n_gpu_layers>0)" : "(n_gpu_layers=0)") +
                " n_gpu_layers=" + gpuLayers +
                " n_threads=" + threads +
                " n_threads_batch=" + threads +
                " n_batch=512 n_ubatch=512 n_ctx=2048" +
                " cache_type_k=f16 cache_type_v=f16 flash_attn=default(use_mmap=true armv8.2-a+dotprod)");
        out("CONFIG model=" + MODEL_PATH);
    }

    private void runMode(String mode, int gpuLayers, int threads) throws Exception {
        dumpConfig(mode, gpuLayers, threads);
        LlamaCppPoC poc = new LlamaCppPoC();
        poc.setNumThreads(threads);

        // --- Load ---
        long t0 = System.currentTimeMillis();
        boolean ok = poc.init(MODEL_PATH, gpuLayers);
        long loadMs = System.currentTimeMillis() - t0;
        out("RESULT mode=" + mode + " loaded=" + ok + " load_ms=" + loadMs +
                " ram_after_load=" + readVmRss());
        if (!ok) {
            out("ABORT mode=" + mode + " model_load_failed");
            return;
        }

        // --- Warm-up (NOT measured): a few tokens to warm caches/code paths ---
        out("WARMUP start (tokens=" + WARMUP_TOKENS + ")");
        CountDownLatch wLatch = new CountDownLatch(1);
        AtomicInteger wTok = new AtomicInteger(0);
        poc.generateStreaming(PROMPT, WARMUP_TOKENS, new LlamaCppPoC.StreamCallback() {
            @Override public void onToken(String t) { wTok.incrementAndGet(); }
            @Override public void onComplete(String r) { wLatch.countDown(); }
            @Override public void onError(String e) { out("WARMUP error=" + e); wLatch.countDown(); }
        });
        boolean wOk = wLatch.await(90, TimeUnit.SECONDS);
        out("WARMUP done ok=" + wOk + " warmup_tokens=" + wTok.get());

        // --- Measured generation (streaming for TTFT + token count) ---
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger tokenCount = new AtomicInteger(0);
        final AtomicLong firstTokMs = new AtomicLong(-1);
        final AtomicReference<String> tail = new AtomicReference<>("");
        long genStart = System.currentTimeMillis();
        poc.generateStreaming(PROMPT, MEASURE_TOKENS, new LlamaCppPoC.StreamCallback() {
            @Override public void onToken(String t) {
                if (tokenCount.getAndIncrement() == 0) firstTokMs.set(System.currentTimeMillis() - genStart);
                if (tokenCount.get() >= MEASURE_TOKENS - 2) tail.set(t);
            }
            @Override public void onComplete(String r) {
                String s = r == null ? "" : r;
                tail.set(s.length() > 80 ? s.substring(s.length() - 80) : s);
                latch.countDown();
            }
            @Override public void onError(String e) { out("MEASURE error=" + e); latch.countDown(); }
        });
        boolean done = latch.await(180, TimeUnit.SECONDS);
        long genMs = System.currentTimeMillis() - genStart;
        long ttft = firstTokMs.get();
        long genExclTtft = (genMs - (ttft > 0 ? ttft : 0));
        if (genExclTtft < 0) genExclTtft = 0;
        int nTok = tokenCount.get();
        double tps = genMs > 0 ? (nTok * 1000.0) / genMs : 0.0;
        double tpsExclTtft = genExclTtft > 0 ? (nTok * 1000.0) / genExclTtft : 0.0;
        out("RESULT mode=" + mode + " completed=" + done +
                " tokens=" + nTok +
                " load_ms=" + loadMs +
                " ttft_ms=" + ttft +
                " gen_total_ms=" + genMs +
                " gen_excl_ttft_ms=" + genExclTtft +
                " tps=" + String.format("%.2f", tps) +
                " tps_excl_ttft=" + String.format("%.2f", tpsExclTtft) +
                " ram=" + readVmRss() +
                " tail=\"" + tail.get() + "\"");
        poc.release();
    }

    // PocketPal parity: 8 threads, CPU (n_gpu_layers=0).
    @Test
    public void benchCPU8_PocketPalParity() throws Exception {
        resetOutFile();
        out("### START PocketPal-parity CPU run (n_threads=8, n_gpu_layers=0) ###");
        runMode("CPU_8threads", 0, 8);
        out("### END PocketPal-parity CPU run ###");
    }

    // Isolate thread-count effect: same CPU path, 4 threads (older PoC default).
    @Test
    public void benchCPU4_ThreadEffect() throws Exception {
        resetOutFile();
        out("### START thread-count comparison CPU run (n_threads=4, n_gpu_layers=0) ###");
        runMode("CPU_4threads", 0, 4);
        out("### END thread-count comparison CPU run ###");
    }
}
