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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class ClaudeClient implements LlmClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final String DEFAULT_AI_ERROR_MESSAGE = "AI 응답 생성 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";

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
            throw toCoteaException(e);
        } catch (WebClientRequestException e) {
            log.warn("Claude API request failed: {}", e.getMessage());
            throw new CoteaException("AI_SERVICE_ERROR", DEFAULT_AI_ERROR_MESSAGE, 502);
        } catch (IllegalStateException e) {
            log.warn("Claude API request timed out or was interrupted: {}", e.getMessage());
            throw new CoteaException("AI_SERVICE_ERROR", "AI 응답 생성 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.", 504);
        }
    }

    private CoteaException toCoteaException(WebClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        log.warn("Claude API returned status {}: {}", status.value(), e.getResponseBodyAsString());

        if (status.value() == 401 || status.value() == 403) {
            return new CoteaException("AI_AUTH_ERROR", "AI 서비스 인증 설정에 문제가 있습니다.", 502);
        }
        if (status.value() == 429) {
            return new CoteaException("AI_RATE_LIMITED", "AI 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.", 429);
        }
        if (status.is5xxServerError()) {
            return new CoteaException("AI_SERVICE_UNAVAILABLE", "AI 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", 502);
        }
        return new CoteaException("AI_SERVICE_ERROR", DEFAULT_AI_ERROR_MESSAGE, 502);
    }

    private ObjectNode messageNode(String role, String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", text);
        return node;
    }
}
