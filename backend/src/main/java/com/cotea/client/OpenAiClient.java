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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiClient implements LlmClient {

    private static final String DEFAULT_AI_ERROR_MESSAGE = "AI 응답 생성 중 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";

    private final WebClient openAiWebClient;
    private final CoteaProperties properties;
    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        String apiKey = properties.getOpenAi().getApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String generate(String systemPrompt, List<ConversationMessage> history, String userMessage) {
        if (!isConfigured()) {
            throw new CoteaException("AI_SERVICE_ERROR", "OPENAI_API_KEY가 설정되지 않았습니다.", 500);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getOpenAi().getModel());
        body.put("max_tokens", properties.getOpenAi().getMaxTokens());

        ArrayNode messages = body.putArray("messages");
        messages.add(messageNode("system", systemPrompt));
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
            JsonNode response = openAiWebClient.post()
                    .uri("/v1/chat/completions")
                    .header("Authorization", "Bearer " + properties.getOpenAi().getApiKey())
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(45));

            if (response == null) {
                throw new CoteaException("AI_SERVICE_ERROR", "OpenAI 응답이 비어 있습니다.", 500);
            }

            JsonNode choices = response.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new CoteaException("AI_SERVICE_ERROR", "OpenAI choices가 없습니다.", 500);
            }
            return choices.get(0).path("message").path("content").asText("").trim();
        } catch (WebClientResponseException e) {
            throw toCoteaException(e);
        } catch (WebClientRequestException e) {
            log.warn("OpenAI API request failed: {}", e.getMessage());
            throw new CoteaException("AI_SERVICE_ERROR", DEFAULT_AI_ERROR_MESSAGE, 502);
        } catch (IllegalStateException e) {
            log.warn("OpenAI API request timed out or was interrupted: {}", e.getMessage());
            throw new CoteaException("AI_SERVICE_ERROR", "AI 응답 생성 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.", 504);
        }
    }

    private CoteaException toCoteaException(WebClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        log.warn("OpenAI API returned status {}: {}", status.value(), e.getResponseBodyAsString());

        if (status.value() == 401 || status.value() == 403) {
            return new CoteaException("AI_AUTH_ERROR", "OpenAI 인증 설정에 문제가 있습니다.", 502);
        }
        if (status.value() == 429) {
            return new CoteaException("AI_RATE_LIMITED", "OpenAI 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.", 429);
        }
        if (status.is5xxServerError()) {
            return new CoteaException("AI_SERVICE_UNAVAILABLE", "OpenAI 서비스가 일시적으로 불안정합니다.", 502);
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
