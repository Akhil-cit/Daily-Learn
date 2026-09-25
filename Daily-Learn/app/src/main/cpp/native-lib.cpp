#include <jni.h>
#include <string>
#include <vector>
#include <chrono>
#include <android/log.h>
#include "llama.h"
#include "ggml-backend.h"

#define TAG "LlamaCppPoC_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static int g_n_threads_override = -1;

struct LlamaContext {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    llama_sampler *smpl = nullptr;
};

#ifdef GGML_USE_VULKAN
#include "ggml-vulkan.h"
#endif

// Detects degenerate repetition loops (e.g. model repeating the exact same sentence or line)
static bool has_degenerate_repetition(const std::string & text, size_t * loop_len = nullptr) {
    if (text.length() < 30) return false;

    // Check 1: Consecutive repeating substring of length L (from 16 up to 200 chars)
    size_t len = text.length();
    for (size_t L = 16; L <= 200 && (2 * L) <= len; ++L) {
        if (text.compare(len - L, L, text, len - 2 * L, L) == 0) {
            if (loop_len) *loop_len = L;
            return true;
        }
    }

    // Check 2: Repeated completed line detection (delimited by '\n')
    size_t last_nl = text.find_last_of('\n');
    if (last_nl != std::string::npos && last_nl > 0) {
        size_t prev_nl = text.find_last_of('\n', last_nl - 1);
        size_t start = (prev_nl == std::string::npos) ? 0 : prev_nl + 1;
        std::string last_line = text.substr(start, last_nl - start);
        while (!last_line.empty() && (unsigned char)last_line.front() <= 32) last_line.erase(last_line.begin());
        while (!last_line.empty() && (unsigned char)last_line.back() <= 32) last_line.pop_back();

        if (last_line.length() >= 15 && start > 0) {
            std::string history = text.substr(0, start);
            if (history.find(last_line) != std::string::npos) {
                if (loop_len) *loop_len = text.length() - start;
                return true;
            }
        }
    }

    // Check 3: Repeated completed sentence detection (delimited by . ! ?)
    size_t last_period = text.find_last_of(".!?");
    if (last_period != std::string::npos && last_period >= 20) {
        size_t prev_period = text.find_last_of(".!?", last_period - 1);
        size_t s_start = (prev_period == std::string::npos) ? 0 : prev_period + 1;
        std::string last_sentence = text.substr(s_start, last_period - s_start);
        while (!last_sentence.empty() && (unsigned char)last_sentence.front() <= 32) last_sentence.erase(last_sentence.begin());
        while (!last_sentence.empty() && (unsigned char)last_sentence.back() <= 32) last_sentence.pop_back();

        if (last_sentence.length() >= 20 && s_start > 0) {
            std::string s_history = text.substr(0, s_start);
            if (s_history.find(last_sentence) != std::string::npos) {
                if (loop_len) *loop_len = text.length() - s_start;
                return true;
            }
        }
    }

    return false;
}

// Cleans trailing stop tokens from response string
static void clean_trailing_stop_tokens(std::string & response) {
    const std::vector<std::string> stop_tokens = {
        "<|im_end|>", "<|im_start|>", "<|endoftext|>", "<|eot_id|>", "</s>"
    };
    for (const auto & st : stop_tokens) {
        size_t pos = response.find(st);
        if (pos != std::string::npos) {
            response.erase(pos);
        }
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_daily_1learn_domain_ai_LlamaCppPoC_initNative(JNIEnv *env, jobject thiz, jstring modelPath, jint gpuLayers) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing llama.cpp. Model: %s, Requested GPU Layers: %d", path, gpuLayers);

#ifdef GGML_USE_VULKAN
    ggml_backend_register(ggml_backend_vk_reg());
#endif

#ifdef GGML_USE_VULKAN
    LOGI("llama.cpp Vulkan backend compiled: YES");
#else
    LOGI("llama.cpp Vulkan backend compiled: NO (CPU-only fallback)");
#endif

    llama_backend_init();

    size_t dev_count = ggml_backend_dev_count();
    LOGI("GGML Backend Device Count: %zu", dev_count);
    for (size_t i = 0; i < dev_count; ++i) {
        ggml_backend_dev_t dev = ggml_backend_dev_get(i);
        const char *name = ggml_backend_dev_name(dev);
        const char *desc = ggml_backend_dev_description(dev);
        LOGI("Device %zu: Name = %s, Description = %s", i, name ? name : "null", desc ? desc : "null");
    }

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = gpuLayers;
    llama_model *model = llama_model_load_from_file(path, mparams);

    if (model == nullptr) {
        LOGE("Failed to load model from %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 2048;
    cparams.n_batch = 512;
    cparams.n_ubatch = 512;
    int n_threads = (g_n_threads_override > 0) ? g_n_threads_override : 4;
    cparams.n_threads = n_threads;
    cparams.n_threads_batch = n_threads;
    LOGI("Config: n_gpu_layers=%d n_threads=%d n_threads_batch=%d n_batch=%d n_ctx=%d cache=f16",
         gpuLayers, cparams.n_threads, cparams.n_threads_batch, cparams.n_batch, cparams.n_ctx);
    llama_context *ctx = llama_init_from_model(model, cparams);
    
    if (ctx == nullptr) {
        LOGE("Failed to create context");
        llama_model_free(model);
        env->ReleaseStringUTFChars(modelPath, path);
        return 0;
    }

    // Initialize sampler chain with repetition penalty, top-k, top-p, and temperature
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);

    int32_t n_vocab = llama_vocab_n_tokens(llama_model_get_vocab(model));
    // Repetition penalty on last 64 tokens: penalty_repeat = 1.18f, freq = 0.05f, present = 0.05f
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(n_vocab, 64, 1.18f, 0.05f, 0.05f));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.90f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.70f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    LlamaContext *wrapper = new LlamaContext();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->smpl = smpl;

    env->ReleaseStringUTFChars(modelPath, path);
    return reinterpret_cast<jlong>(wrapper);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_daily_1learn_domain_ai_LlamaCppPoC_setNumThreadsNative(JNIEnv *env,
                                                                         jobject thiz,
                                                                         jint nThreads) {
    g_n_threads_override = nThreads;
    LOGI("Thread override set to %d", nThreads);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_daily_1learn_domain_ai_LlamaCppPoC_generateNative(JNIEnv *env, jobject thiz, jlong contextPtr, jstring prompt, jint maxTokens) {
    if (contextPtr == 0) return env->NewStringUTF("Error: Invalid context pointer");

    LlamaContext *wrapper = reinterpret_cast<LlamaContext *>(contextPtr);
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    
    LOGI("Generating for prompt: %s", prompt_str);

    std::string prompt_str_c(prompt_str);
    const struct llama_vocab * vocab = llama_model_get_vocab(wrapper->model);
    int n_prompt_tokens = -llama_tokenize(vocab, prompt_str_c.c_str(), prompt_str_c.length(), NULL, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    if (llama_tokenize(vocab, prompt_str_c.c_str(), prompt_str_c.length(), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Tokenization failed");
    }

    llama_memory_t mem = llama_get_memory(wrapper->ctx);
    if (mem != nullptr) {
        llama_memory_seq_rm(mem, -1, -1, -1);
    }
    llama_sampler_reset(wrapper->smpl);

    LOGI("Prompt tokens: %d (n_ctx=2048)", n_prompt_tokens);
    if (n_prompt_tokens > 2000) {
        LOGE("Prompt too long (%d tokens), skipping generation", n_prompt_tokens);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Prompt exceeds context window");
    }

    llama_batch batch = llama_batch_init(prompt_tokens.size() + 1, 0, 1);
    
    for (size_t i = 0; i < prompt_tokens.size(); i++) {
        batch.token[i] = prompt_tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = false;
    }
    batch.logits[prompt_tokens.size() - 1] = true;
    batch.n_tokens = prompt_tokens.size();

    auto start_time = std::chrono::high_resolution_clock::now();
    int decode_res = llama_decode(wrapper->ctx, batch);
    auto end_time = std::chrono::high_resolution_clock::now();
    auto ttft_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGI("TTFT: %lld ms", (long long)ttft_ms);

    if (decode_res != 0) {
        LOGE("llama_decode failed with error code: %d (prompt_tokens=%d)", decode_res, (int)prompt_tokens.size());
        env->ReleaseStringUTFChars(prompt, prompt_str);
        llama_batch_free(batch);
        return env->NewStringUTF("Error: llama_decode failed");
    }

    std::string response = "";
    int n_cur = batch.n_tokens;
    int n_decode = 0;
    
    auto gen_start_time = std::chrono::high_resolution_clock::now();
    while (n_cur <= 2048 && n_decode < maxTokens) {
        llama_token new_token_id = llama_sampler_sample(wrapper->smpl, wrapper->ctx, -1);
        llama_sampler_accept(wrapper->smpl, new_token_id);
        
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }
        
        char buf[128];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n < 0) {
            break;
        }
        
        std::string piece(buf, n);

        // Check for stop tokens
        if (piece.find("<|im_end|>") != std::string::npos ||
            piece.find("<|im_start|>") != std::string::npos ||
            piece.find("<|endoftext|>") != std::string::npos ||
            piece.find("<|eot_id|>") != std::string::npos) {
            LOGI("Stop sequence reached in generateNative.");
            break;
        }

        response += piece;

        // Check for degenerate repetition loops
        size_t loop_len = 0;
        if (has_degenerate_repetition(response, &loop_len)) {
            LOGI("Degenerate repetition loop detected in generateNative! Terminating. loop_len=%zu, n_decode=%d", loop_len, n_decode);
            if (loop_len > 0 && loop_len <= response.length()) {
                response.erase(response.length() - loop_len);
            }
            break;
        }
        
        // Setup batch for next token
        batch.token[0] = new_token_id;
        batch.pos[0] = n_cur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        batch.n_tokens = 1;
        
        if (llama_decode(wrapper->ctx, batch)) {
            break;
        }
        
        n_cur += 1;
        n_decode += 1;
    }
    auto gen_end_time = std::chrono::high_resolution_clock::now();
    auto gen_ms = std::chrono::duration_cast<std::chrono::milliseconds>(gen_end_time - gen_start_time).count();
    double tps_excl_ttft = gen_ms > 0 ? (n_decode * 1000.0) / gen_ms : 0.0;
    LOGI("BENCHMARK_METRICS: TTFT=%lld ms, GEN_EXCL_TTFT=%lld ms, TOKENS=%d, TPS_EXCL_TTFT=%.2f",
         (long long)ttft_ms, (long long)gen_ms, n_decode, tps_excl_ttft);

    clean_trailing_stop_tokens(response);
    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_daily_1learn_domain_ai_LlamaCppPoC_generateStreamingNative(JNIEnv *env, jobject thiz, jlong contextPtr, jstring prompt, jint maxTokens, jobject callback) {
    if (contextPtr == 0) {
        if (callback != nullptr) {
            jclass cbClass = env->GetObjectClass(callback);
            jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");
            jstring err = env->NewStringUTF("Error: Invalid context pointer");
            env->CallVoidMethod(callback, onError, err);
            env->DeleteLocalRef(err);
        }
        return;
    }

    LlamaContext *wrapper = reinterpret_cast<LlamaContext *>(contextPtr);
    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    
    LOGI("Streaming generation for prompt: %s", prompt_str);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onErrorMethod = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");

    std::string prompt_str_c(prompt_str);
    const struct llama_vocab * vocab = llama_model_get_vocab(wrapper->model);
    int n_prompt_tokens = -llama_tokenize(vocab, prompt_str_c.c_str(), prompt_str_c.length(), NULL, 0, true, true);
    
    if (n_prompt_tokens <= 0) {
        LOGE("Failed to estimate tokens");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        jstring err = env->NewStringUTF("Error: Failed to estimate tokens");
        env->CallVoidMethod(callback, onErrorMethod, err);
        env->DeleteLocalRef(err);
        return;
    }

    std::vector<llama_token> prompt_tokens(n_prompt_tokens);
    if (llama_tokenize(vocab, prompt_str_c.c_str(), prompt_str_c.length(), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        LOGE("Failed to tokenize prompt");
        env->ReleaseStringUTFChars(prompt, prompt_str);
        jstring err = env->NewStringUTF("Error: Failed to tokenize");
        env->CallVoidMethod(callback, onErrorMethod, err);
        env->DeleteLocalRef(err);
        return;
    }

    llama_memory_t mem = llama_get_memory(wrapper->ctx);
    if (mem != nullptr) {
        llama_memory_seq_rm(mem, -1, -1, -1);
    }
    llama_sampler_reset(wrapper->smpl);

    llama_batch batch = llama_batch_init(prompt_tokens.size() + 1, 0, 1);
    
    for (size_t i = 0; i < prompt_tokens.size(); i++) {
        batch.token[i] = prompt_tokens[i];
        batch.pos[i] = i;
        batch.n_seq_id[i] = 1;
        batch.seq_id[i][0] = 0;
        batch.logits[i] = false;
    }
    batch.logits[prompt_tokens.size() - 1] = true;
    batch.n_tokens = prompt_tokens.size();

    auto start_time = std::chrono::high_resolution_clock::now();
    int decode_res = llama_decode(wrapper->ctx, batch);
    auto end_time = std::chrono::high_resolution_clock::now();
    auto ttft_ms = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGI("Streaming TTFT: %lld ms", (long long)ttft_ms);

    if (decode_res != 0) {
        LOGE("llama_decode failed with error code: %d (prompt_tokens=%d)", decode_res, (int)prompt_tokens.size());
        env->ReleaseStringUTFChars(prompt, prompt_str);
        llama_batch_free(batch);
        jstring err = env->NewStringUTF("Error: llama_decode failed");
        env->CallVoidMethod(callback, onErrorMethod, err);
        env->DeleteLocalRef(err);
        return;
    }

    std::string response = "";
    int n_cur = batch.n_tokens;
    int n_decode = 0;
    
    while (n_cur <= 2048 && n_decode < maxTokens) {
        llama_token new_token_id = llama_sampler_sample(wrapper->smpl, wrapper->ctx, -1);
        llama_sampler_accept(wrapper->smpl, new_token_id);
        
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }
        
        char buf[128];
        int n = llama_token_to_piece(vocab, new_token_id, buf, sizeof(buf), 0, true);
        if (n < 0) {
            break;
        }
        
        std::string piece(buf, n);

        // Check for stop tokens
        if (piece.find("<|im_end|>") != std::string::npos ||
            piece.find("<|im_start|>") != std::string::npos ||
            piece.find("<|endoftext|>") != std::string::npos ||
            piece.find("<|eot_id|>") != std::string::npos) {
            LOGI("Streaming reached stop sequence.");
            break;
        }

        response += piece;

        // Check for degenerate repetition loops
        size_t loop_len = 0;
        if (has_degenerate_repetition(response, &loop_len)) {
            LOGI("Degenerate repetition loop detected during streaming! Terminating cleanly. loop_len=%zu, n_decode=%d", loop_len, n_decode);
            if (loop_len > 0 && loop_len <= response.length()) {
                response.erase(response.length() - loop_len);
            }
            break;
        }
        
        jstring jPiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jPiece);
        env->DeleteLocalRef(jPiece);
        
        // Setup batch for next token
        batch.token[0] = new_token_id;
        batch.pos[0] = n_cur;
        batch.n_seq_id[0] = 1;
        batch.seq_id[0][0] = 0;
        batch.logits[0] = true;
        batch.n_tokens = 1;
        
        if (llama_decode(wrapper->ctx, batch)) {
            break;
        }
        
        n_cur++;
        n_decode++;
    }

    clean_trailing_stop_tokens(response);
    llama_batch_free(batch);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    
    jstring jResponse = env->NewStringUTF(response.c_str());
    env->CallVoidMethod(callback, onCompleteMethod, jResponse);
    env->DeleteLocalRef(jResponse);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_daily_1learn_domain_ai_LlamaCppPoC_releaseNative(JNIEnv *env, jobject thiz, jlong contextPtr) {
    if (contextPtr == 0) return;
    LlamaContext *wrapper = reinterpret_cast<LlamaContext *>(contextPtr);
    LOGI("Releasing llama.cpp context");
    
    if (wrapper->smpl) llama_sampler_free(wrapper->smpl);
    if (wrapper->ctx) llama_free(wrapper->ctx);
    if (wrapper->model) llama_model_free(wrapper->model);
    llama_backend_free();
    
    delete wrapper;
}
