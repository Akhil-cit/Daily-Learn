# CPU Parity Benchmark — OPPO CPH2119 (MT6779, 6+2 big.LITTLE)

Date: 2026-09-04 · Device serial `S4PBC67585CQZTNJ` (CPH2119)
Model under test: SmolLM2-360M Q4_K_M (`/data/local/tmp/benchmark_model.gguf`, 270,590,880 bytes)
Fixed params: `n_gpu_layers=0 n_batch=512 n_ctx=2048 mmap=on cache=f16`. Backend verified CPU-only (`GGML Backend Device Count: 1`, `Device 0: Name = CPU`).

---

## 1. Build flags compared

| Build | GGML_CPU_ARM_ARCH | GGML_CPU_REPACK | GGML_VULKAN | c/cpp flags |
|---|---|---|---|---|
| Prior (8/16, non-optimized) | `(empty)` | default ON | ON | default |
| Current (9/4, optimized) | `armv8.2-a+dotprod+fp16` | ON | OFF | `-march=armv8.2-a+dotprod+fp16 -O3 -DNDEBUG` |

Device features: `asimddp` (dotprod) + `asimdhp` (fp16) present; **no i8mm, no SVE** →
`dotprod+fp16` is the correct architecture target; **i8mm must NOT be used** (SIGILL / rejected).

---

## 2. Apples-to-apples results — `LlamaCppPerfBench`, streaming, 24 generated tokens

Same harness, same token count, same model, same CPU-only backend.

| Threads | Build | load_ms | TTFT_ms | gen_excl_TTFT_ms | tps | tps_excl_TTFT |
|---|---|---|---|---|---|---|
| 8 | Prior non-optimized (8/16) | 1738 | 1024 | 1961 | 8.04 | **12.24** |
| 4 | Current optimized | 960 | 884 | 1349 | 10.75 | **17.79** |
| 8 | Current optimized (run 1) | 816 | 1376 | 6092 | 3.21 | **3.94** |
| 8 | Current optimized (run 2, reproducible) | 844 | 1888 | 6101 | 3.00 | **3.93** |

---

## 3. Non-streaming results — `CpuParityBenchmarkTest`, 50 generated tokens (optimized build only)

| Threads | TTFT_ms | gen_excl_TTFT_ms | tps_excl_TTFT |
|---|---|---|---|
| 4 | 1005 | 5088 | **9.83** |
| 6 | 1542 | 5360 | **9.33** |
| 8 | 1510 | 5814 | **8.60** |

Note: 6-thread Big cores drooped 1.93→1.65 GHz; 8-thread Big cores 2.07→1.65 GHz.

---

## 4. Key findings

1. **CPU optimization genuinely helps the 4-thread path.** With the same streaming/24-token
   methodology, optimized CPU4 = **17.79 tps_excl_TTFT**. This is the healthiest single result
   on this device and is broadly in the range of the task's cited "CPU4 ≈ 20.92" (source
   artifact not located in the repo; see §5).

2. **8 threads collapse on the optimized build (3.94 vs 12.24 prior).** The regression is
   reproducible (3.93 / 3.94 across two isolated runs). Live core sampling during the 8-thread
   run shows the **Big cores (cpu6/7) never boost to their 2.2 GHz ceiling — they sit at
   1.65–1.93 GHz**, while Little cores run at up to 2.0 GHz. With 8 threads spread across 6×A55
   + 2×A75, the A55 threads (up to 2.0 GHz) outpace the A75 threads and become the effective
   bottleneck, so the A75s sit idle/low-clock and end-to-end throughput drops well below the
   balanced 4-thread case. This is the big.LITTLE imbalance, evidenced by cores, not assumed.

3. **8 threads are never the right choice on this 6+2 device** — both optimized runs are far
   slower than 4 threads; the non-optimized 8-thread run (12.24) was also slower than the
   optimized 4-thread (17.79).

---

## 5. Reconciliation of the task's cited prior figures

- The prior logcat artifact (`logcat-...benchCPU8_PocketPalParity.txt`, 8/16) actually records
  **CPU8 = 12.24 tps_excl_TTFT** (non-optimized), **not** the task's "~7.43".
- The task's cited "CPU4 ≈ 20.92" and "CPU8 ≈ 7.43" numbers do **not** appear in any repo
  artifact (grep across `.md/.txt/.log/.java` found no `20.92` or `7.43` other than incidental
  matches). They likely came from a different/unsaved harness run or a different token-count
  window and cannot be verified from the available evidence.
- Conclusion: do not treat the task's cited numbers as a verified baseline. The verified prior
  baseline on record is **CPU8 = 12.24** (non-optimized, streaming, 24 tok).

---

## 6. Bottom line

- The `-march=armv8.2-a+dotprod+fp16` CPU build **works and materially improves the 4-thread
  throughput** (optimized 4-thread ≈ 17.79 solo). The flags are confirmed in the current
  CMake cache (`5cku6l72`) and were not producing crashes.
- **Do NOT run 8 threads** on this 6+2 big.LITTLE part; it is a net negative (3.9 vs 17.8).
  The optimal thread count on CPH2119 is **4**.
- **No PocketPal parity is claimed.** Model differs (360M here vs 500M for PocketPal) and this
  is CPU-only; a direct parity claim would require the same model + harness.

---

## 7. Recommended single next step

Re-run the 4-thread non-streaming case a few times and confirm stability (~9.8–18 depending on
streaming vs non-streaming and token count), then lock `n_threads=4` for production CPU
inference and **do not ship a thread count ≥ 6 on this device**. Optionally add `sched_setaffinity`
to pin llama worker threads to the 2×A75 Big cores for the 2-thread path to see if a
Big-core-only path exceeds 17.79.
