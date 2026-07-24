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
    private static final Duration DEFAULT_BLOCK_TIMEOUT = Duration.ofSeconds(60);
    /** max_tokens를 16000까지 올렸으므로, 실제로 그만큼 생성이 필요한 경우 60초로는 부족할 수 있다. */
    private static final Duration PROBLEM_GENERATION_BLOCK_TIMEOUT = Duration.ofSeconds(120);

    private final WebClient claudeWebClient;
    private final CoteaProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String generate(String systemPrompt, List<ConversationMessage> history, String userMessage) {
        ArrayNode messages = objectMapper.createArrayNode();
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

        return execute(systemPrompt, messages, properties.getClaude().getMaxTokens(), DEFAULT_BLOCK_TIMEOUT);
    }

    /**
     * 이미지를 먼저, 텍스트를 나중에 배치한다 — Anthropic 문서 권고사항
     * (https://platform.claude.com/docs/en/build-with-claude/vision): 이미지가 텍스트보다 앞에 오면
     * 결과가 더 좋다. 이미지는 source.type="url"로 전달한다 — 프로그래머스 문제 이미지는 이미
     * S3에 공개 호스팅돼 있어(예: grepp-programmers.s3....) 다운로드 후 base64로 인코딩할 필요가 없다.
     */
    @Override
    public String generateWithImages(String systemPrompt, String userMessage, List<String> imageUrls) {
        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("role", "user");
        ArrayNode content = userNode.putArray("content");

        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }
                ObjectNode imageBlock = content.addObject();
                imageBlock.put("type", "image");
                ObjectNode source = imageBlock.putObject("source");
                source.put("type", "url");
                source.put("url", imageUrl);
            }
        }
        ObjectNode textBlock = content.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", userMessage);

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(userNode);

        return execute(systemPrompt, messages, properties.getClaude().getProblemGenerationMaxTokens(),
                PROBLEM_GENERATION_BLOCK_TIMEOUT);
    }

    private String execute(String systemPrompt, ArrayNode messages, int maxTokens, Duration timeout) {
        String apiKey = properties.getClaude().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new CoteaException("AI_SERVICE_ERROR", "ANTHROPIC_API_KEY가 설정되지 않았습니다.", 500);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getClaude().getModel());
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);
        body.set("messages", messages);

        try {
            JsonNode response = claudeWebClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);

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
