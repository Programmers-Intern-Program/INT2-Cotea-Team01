package com.cotea.client;

import com.cotea.config.CoteaProperties;
import com.cotea.controller.dto.ConversationMessage;
import com.cotea.exception.CoteaException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@Primary
@RequiredArgsConstructor
public class ClaudeClient implements LlmClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final WebClient claudeWebClient;
    private final CoteaProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String generate(String systemPrompt, List<ConversationMessage> history, String userMessage) {
        String apiKey = properties.getClaude().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new CoteaException("AI_SERVICE_ERROR", "ANTHROPIC_API_KEY가 설정되지 않았습니다.", 500);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getClaude().getModel());
        body.put("max_tokens", properties.getClaude().getMaxTokens());
        body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        if (history != null) {
            for (ConversationMessage message : history) {
                if (message.getText() == null || message.getText().isBlank()) {
                    continue;
                }
                String role = "assistant".equals(message.getRole()) ? "assistant" : "user";
                messages.add(messageNode(role, message.getText()));
            }
        }
        messages.add(messageNode("user", userMessage));

        try {
            JsonNode response = claudeWebClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(60));

            if (response == null) {
                throw new CoteaException("AI_SERVICE_ERROR", "Claude 응답이 비어 있습니다.", 500);
            }

            JsonNode content = response.path("content");
            if (!content.isArray() || content.isEmpty()) {
                throw new CoteaException("AI_SERVICE_ERROR", "Claude content가 없습니다.", 500);
            }

            return content.get(0).path("text").asText("").trim();
        } catch (WebClientResponseException e) {
            throw new CoteaException("AI_SERVICE_ERROR", "Claude API 호출 실패: " + e.getResponseBodyAsString(), 500);
        }
    }

    private ObjectNode messageNode(String role, String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", text);
        return node;
    }
}
