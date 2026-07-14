package com.cotea.service.hint;

import com.cotea.client.ClaudeClient;
import com.cotea.client.OpenAiClient;
import com.cotea.controller.dto.ConversationMessage;
import com.cotea.exception.CoteaException;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 전처리 범위 밖 질문: OpenAI(저렴) 우선 → 미설정/실패 시 Claude + 동일 off-topic policy.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OffTopicLlmRouter {

    private final OpenAiClient openAiClient;
    private final ClaudeClient claudeClient;

    public Result generate(String systemPrompt, List<ConversationMessage> history, String userMessage) {
        if (openAiClient.isConfigured()) {
            try {
                String text = openAiClient.generate(systemPrompt, history, userMessage);
                return new Result(text, "openai");
            } catch (CoteaException e) {
                log.warn("[OFF_TOPIC] OpenAI 실패 → Claude fallback: {}", e.getMessage());
            }
        } else {
            log.info("[OFF_TOPIC] OPENAI_API_KEY 없음 → Claude fallback");
        }
        String text = claudeClient.generate(systemPrompt, history, userMessage);
        return new Result(text, "claude_fallback");
    }

    public String preferredProviderForDryRun() {
        return openAiClient.isConfigured() ? "openai" : "claude_fallback";
    }

    @Getter
    @RequiredArgsConstructor
    public static class Result {
        private final String responseText;
        private final String llmProvider;
    }
}
