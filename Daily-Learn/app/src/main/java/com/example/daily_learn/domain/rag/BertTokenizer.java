package com.example.daily_learn.domain.rag;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BertTokenizer {
    private final Map<String, Integer> vocab = new HashMap<>();
    private final int MAX_SEQ_LEN = 256;
    private final String UNK_TOKEN = "[UNK]";
    private final String CLS_TOKEN = "[CLS]";
    private final String SEP_TOKEN = "[SEP]";

    public BertTokenizer(Context context, String vocabFilename) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(vocabFilename)))) {
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null) {
                vocab.put(line.trim(), index++);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error loading vocab", e);
        }
    }

    public long[] tokenize(String text) {
        text = text.toLowerCase();
        List<String> tokens = basicTokenize(text);
        List<Integer> tokenIds = new ArrayList<>();
        
        tokenIds.add(vocab.get(CLS_TOKEN));
        
        for (String token : tokens) {
            List<Integer> subTokens = wordPieceTokenize(token);
            tokenIds.addAll(subTokens);
        }
        
        tokenIds.add(vocab.get(SEP_TOKEN));
        
        // Pad or truncate
        long[] result = new long[MAX_SEQ_LEN];
        for (int i = 0; i < MAX_SEQ_LEN; i++) {
            if (i < tokenIds.size()) {
                result[i] = tokenIds.get(i);
            } else {
                result[i] = 0; // Pad token is 0
            }
        }
        return result;
    }
    
    public long[] getAttentionMask(String text) {
        text = text.toLowerCase();
        List<String> tokens = basicTokenize(text);
        int validLen = 2; // CLS + SEP
        for (String token : tokens) {
            validLen += wordPieceTokenize(token).size();
        }
        
        long[] mask = new long[MAX_SEQ_LEN];
        for (int i = 0; i < MAX_SEQ_LEN; i++) {
            mask[i] = (i < validLen) ? 1 : 0;
        }
        return mask;
    }

    private List<String> basicTokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
            } else if (isPunctuation(c)) {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
                tokens.add(String.valueOf(c));
            } else {
                currentToken.append(c);
            }
        }
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        return tokens;
    }

    private boolean isPunctuation(char c) {
        int type = Character.getType(c);
        return type == Character.CONNECTOR_PUNCTUATION ||
               type == Character.DASH_PUNCTUATION ||
               type == Character.START_PUNCTUATION ||
               type == Character.END_PUNCTUATION ||
               type == Character.INITIAL_QUOTE_PUNCTUATION ||
               type == Character.FINAL_QUOTE_PUNCTUATION ||
               type == Character.OTHER_PUNCTUATION ||
               c == '~' || c == '^' || c == '`';
    }

    private List<Integer> wordPieceTokenize(String token) {
        List<Integer> subTokens = new ArrayList<>();
        int start = 0;
        while (start < token.length()) {
            int end = token.length();
            String subStr = null;
            while (start < end) {
                subStr = token.substring(start, end);
                if (start > 0) {
                    subStr = "##" + subStr;
                }
                if (vocab.containsKey(subStr)) {
                    break;
                }
                end--;
            }
            if (start == end) {
                // UNK
                subTokens.add(vocab.get(UNK_TOKEN));
                return subTokens;
            } else {
                subTokens.add(vocab.get(subStr));
                start = end;
            }
        }
        return subTokens;
    }
}
