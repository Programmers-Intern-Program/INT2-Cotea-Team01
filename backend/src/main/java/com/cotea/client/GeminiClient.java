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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final WebClient geminiWebClient;
    private final CoteaProperties properties;
    private final ObjectMapper objectMapper;

    public String generate(String systemPrompt, List<ConversationMessage> history, String userMessage) {
        String apiKey = properties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new CoteaException("AI_SERVICE_ERROR", "GEMINI_API_KEY가 설정되지 않았습니다.", 500);
        }

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");

        if (history != null) {
            for (ConversationMessage message : history) {
                if (message.getText() == null || message.getText().isBlank()) {
                    continue;
                }
                String role = "assistant".equals(message.getRole()) ? "model" : "user";
                contents.add(contentNode(role, message.getText()));
            }
        }
        contents.add(contentNode("user", userMessage));

        ObjectNode systemInstruction = objectMapper.createObjectNode();
        systemInstruction.putArray("parts").addObject().put("text", systemPrompt);
        body.set("systemInstruction", systemInstruction);

        String model = properties.getGemini().getModel();
        String uri = "/models/" + model + ":generateContent?key=" + apiKey;

        try {
            JsonNode response = geminiWebClient.post()
                    .uri(uri)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(60));

            if (response == null) {
                throw new CoteaException("AI_SERVICE_ERROR", "Gemini 응답이 비어 있습니다.", 500);
            }

            JsonNode candidates = response.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new CoteaException("AI_SERVICE_ERROR", "Gemini candidates가 없습니다.", 500);
            }

            return candidates.get(0).path("content").path("parts").get(0).path("text").asText("").trim();
        } catch (WebClientResponseException e) {
            throw new CoteaException("AI_SERVICE_ERROR", "Gemini API 호출 실패: " + e.getResponseBodyAsString(), 500);
        }
    }

    private ObjectNode contentNode(String role, String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.putArray("parts").addObject().put("text", text);
        return node;
    }
}
