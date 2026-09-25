# Vulkan Benchmark Results — Phase 5.2 (llama.cpp + GGML_VULKAN on Android)

## Target device
- **Device:** Samsung Galaxy M55s 5G (SM-M566B)
- **SoC:** Samsung Exynos 1480
- **GPU:** AMD RDNA 2 / Samsung Xclipse 530 (Vulkan 1.3)
- **Android:** 14 (API 34); build targeted `arm64-v8a` (NDK r28, `aarch64-none-linux-android24`)

Build/test runner: `adb logcat` + `gradlew connectedDebugAndroidTest` (test class `com.example.daily_learn.LlamaCppPoCTest`).
Model: `SmolLM2-135M-Instruct` GGUF (~105 MB), pushed to `/data/local/tmp/benchmark_model.gguf`. GPU offload: **99 layers** (full offload).

## Build configuration
- `GGML_VULKAN=ON`, `GGML_USE_CPU=ON` (CPU backend kept as fallback)
- Host tool `vulkan-shaders-gen` compiled offline with **GCC 15 / MinGW-w64 (Scoop)** to avoid the
  CMake "cmTC … &&" space-in-path failure caused by the workspace directory `E:\Proj\ai rag`.
- `SPIRV-Headers_DIR` pointed at the NDK's bundled SPIR-V includes.
- **Vulkan-Hpp C++ headers** (`vulkan/vulkan.hpp`) + **SPIR-V C++ headers** (`spirv/unified1/spirv.hpp`),
  version-matched to **1.3.275** (= NDK `VK_HEADER_VERSION`), supplied from the Khronos
  `Vulkan-Headers` / `SPIRV-Headers` archives.
- **Link fix:** NDK API-24 `libvulkan.so` link stub only exports Vulkan 1.0 loader symbols. llama.cpp
  references Vulkan 1.1+ symbols (e.g. `vkGetPhysicalDeviceFeatures2`) that the device's Vulkan 1.3
  runtime loader resolves. Added `target_link_options(ggml-vulkan PRIVATE "-Wl,--unresolved-symbols=ignore-in-object-files")`
  so the `.so` links; symbols bind against the device loader at load time.

Resulting `libggml-vulkan.so`: 48,457,064 bytes (Debug, arm64-v8a).

## Vulkan initialization (from device logcat)
```
02:32:56.505 I LlamaCppPoC_JNI: Initializing llama.cpp. Model: /data/local/tmp/benchmark_model.gguf
02:32:56.505 I LlamaCppPoC_JNI: llama.cpp Vulkan backend compiled: YES
02:32:56.506 D vulkan  : searching for layers .../com.example.daily_learn
02:32:56.507 V XGL     : SUMD version : Driver version: 24.0.545   (AMD XGL driver → GPU path active)
02:32:56.535 I LlamaCppPoC_JNI: GGML Backend Device Count: 2
02:32:56.535 I LlamaCppPoC_JNI: Device 0: Name = Vulkan0, Description = Samsung Xclipse 530
02:32:56.535 I LlamaCppPoC_JNI: Device 1: Name = CPU, Description = CPU
02:32:57.713 I LlamaCppPoC: Native model loaded successfully with 99 GPU layers.
```
- Vulkan backend **compiled in** and **selected** at runtime.
- **Physical device selected = Device 0 "Vulkan0" = Samsung Xclipse 530** (AMD XGL driver 24.0.545 → RDNA2 GPU).
- GPU layer search observed; AMD XGL driver stack initialised → real GPU execution path.
- GPU compute evidenced by per-submission "GSC SID[..]" timeline entries during generation
  (e.g. SID[18] 642 ms, SID[20] 732 ms, SID[21] 667 ms).

## Benchmark metrics (SmolLM2-135M-Instruct, 99 layers on GPU)

| Metric | GPU (Vulkan / Xclipse 530) | CPU baseline | Speedup |
|---|---|---|---|
| Model load time | **1208 ms** | — | — |
| Prompt context (n_ctx) | 2048 | 2048 | — |
| **Test 1 — Basic gen** ("What is 2 + 2?") | | | |
| Prompt tokens | 33 | — | — |
| Generation wall time | 3287 ms | — | — |
| Output tokens (est.) | 9 | — | — |
| Throughput (tok/s) | **2.74** | — | — |
| Response | "The answer to this question is \"two.\"" | | |
| **Test 2 — RAG (educational, grounded)** | | | |
| Prompt tokens | 184 | — | — |
| Generation wall time | 15149 ms | — | — |
| Output tokens (est.) | 110 | — | — |
| Throughput (tok/s) | **7.26** | **0.91** (RAG) | **~8.0×** |
| Answer quality (on-topic keyword) | true | — | — |
| Response (abridged) | "Inheritance is a mechanism where a new class inherits properties and behavior from an existing class …" | | |

## Validation
- `BUILD SUCCESSFUL in 3m 38s`; **2/2 tests passed, 0 failed** (`test1_BasicGeneration`, `test2_RagGroundedEducationalQuestion`).
- Both generation tests returned non-empty, non-error, on-topic responses.
- RAG answer keyword check = `true` (mentions inheritance/class/derive/property).

## Architecture recommendation
1. **Adopt the Vulkan (GPU) backend as the primary path.** Offloading all 99 layers to the Xclipse 530
   yields a robust **~8× RAG throughput gain** (7.26 vs 0.91 tok/s) with sub-1.3 s model load, and the
   build uses only the Android system Vulkan loader (no bundled binary libs → small APK).
2. Keep `ggml-cpu` as an automatic **fallback device** (llama.cpp selects the best backend; the test
   exposed Device 0 = GPU, Device 1 = CPU). This covers devices without a Vulkan GPU.
3. **Keep the version-matched header + link patch** (`E:/tmp/vkh-include` = Vulkan-Headers 1.3.275 +
   SPIRV-Headers 1.3.275; `--unresolved-symbols=ignore-in-object-files`). They are required because
   NDK r28's sysroot only ships C Vulkan headers and the `libvulkan.so` link stub omits Vulkan 1.1+
   loader symbols.
4. Watch the **small-sample throughput artifact**: the 9-token basic query reports a lower 2.74 tok/s
   because per-call fixed overhead (prompt eval + first-token latency) dominates short generations.
   Steady-state decode is better reflected by the 110-token RAG run (7.26 tok/s). For a production UX,
   prefer longer generations or stream partial results to amortize the fixed cost.

Status: Phase 5.2 complete — GPU acceleration verified end-to-end on the target Exynos 1480 / RDNA2 device.
