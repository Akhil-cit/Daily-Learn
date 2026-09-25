package com.example.daily_learn.domain.rag;

import android.content.Context;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.InputStream;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

public class EmbeddingService {
    private final OrtEnvironment env;
    private OrtSession session;
    private final BertTokenizer tokenizer;

    public EmbeddingService(Context context) {
        this.tokenizer = new BertTokenizer(context, "vocab.txt");
        this.env = OrtEnvironment.getEnvironment();
        try {
            InputStream is = context.getAssets().open("model.onnx");
            byte[] modelBytes = new byte[is.available()];
            is.read(modelBytes);
            is.close();
            
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            this.session = env.createSession(modelBytes, options);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ONNX session", e);
        }
    }

    public float[] embedQuery(String text) {
        return embedTexts(new String[]{text})[0];
    }

    public float[][] embedTexts(String[] texts) {
        int batchSize = texts.length;
        int seqLen = 256;

        long[] flatInputIds = new long[batchSize * seqLen];
        long[] flatAttentionMask = new long[batchSize * seqLen];
        long[] flatTokenTypeIds = new long[batchSize * seqLen]; // all zeros

        for (int i = 0; i < batchSize; i++) {
            long[] tokenIds = tokenizer.tokenize(texts[i]);
            long[] mask = tokenizer.getAttentionMask(texts[i]);
            System.arraycopy(tokenIds, 0, flatInputIds, i * seqLen, seqLen);
            System.arraycopy(mask, 0, flatAttentionMask, i * seqLen, seqLen);
        }

        try {
            OnnxTensor inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(flatInputIds), new long[]{batchSize, seqLen});
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(flatAttentionMask), new long[]{batchSize, seqLen});
            OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(flatTokenTypeIds), new long[]{batchSize, seqLen});

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);

            OrtSession.Result result = session.run(inputs);
            
            // last_hidden_state is the first output, shape [batch, seqLen, 384]
            float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();
            
            float[][] embeddings = new float[batchSize][384];
            
            for (int b = 0; b < batchSize; b++) {
                float[] sumVec = new float[384];
                float maskSum = 0;
                
                for (int s = 0; s < seqLen; s++) {
                    float maskVal = flatAttentionMask[b * seqLen + s];
                    if (maskVal > 0) {
                        for (int d = 0; d < 384; d++) {
                            sumVec[d] += lastHiddenState[b][s][d];
                        }
                        maskSum += 1.0f;
                    }
                }
                
                float norm = 0;
                for (int d = 0; d < 384; d++) {
                    float mean = maskSum > 0 ? sumVec[d] / maskSum : 0;
                    embeddings[b][d] = mean;
                    norm += mean * mean;
                }
                
                // L2 Normalize
                float l2 = (float) Math.sqrt(norm);
                if (l2 > 0) {
                    for (int d = 0; d < 384; d++) {
                        embeddings[b][d] /= l2;
                    }
                }
            }

            result.close();
            inputIdsTensor.close();
            attentionMaskTensor.close();
            tokenTypeIdsTensor.close();

            return embeddings;

        } catch (Exception e) {
            throw new RuntimeException("Error running inference", e);
        }
    }
}
