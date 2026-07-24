package com.cotea.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotea.config.CoteaProperties;
import com.cotea.exception.CoteaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ClaudeClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsTextFromClaudeResponse() {
        ClaudeClient client = clientWithResponse(
                HttpStatus.OK,
                """
                {
                  "content": [
                    {
                      "type": "text",
                      "text": "힌트 응답입니다."
                    }
                  ]
                }
                """
        );

        String result = client.generate("system", null, "user");

        assertThat(result).isEqualTo("힌트 응답입니다.");
    }

    @Test
    void returnsTextFromClaudeResponseWithImages() {
        ClaudeClient client = clientWithResponse(
                HttpStatus.OK,
                """
                {
                  "content": [
                    {
                      "type": "text",
                      "text": "이미지를 반영한 응답입니다."
                    }
                  ]
                }
                """
        );

        String result = client.generateWithImages(
                "system", "user", List.of("https://grepp-programmers.s3.example.com/example.png"));

        assertThat(result).isEqualTo("이미지를 반영한 응답입니다.");
    }

    @Test
    void generateWithImagesWorksWithoutAnyImageUrls() {
        ClaudeClient client = clientWithResponse(
                HttpStatus.OK,
                """
                {
                  "content": [{ "type": "text", "text": "이미지 없이도 동작." }]
                }
                """
        );

        String result = client.generateWithImages("system", "user", List.of());

        assertThat(result).isEqualTo("이미지 없이도 동작.");
    }

    @Test
    void rejectsMissingApiKeyBeforeRequest() {
        CoteaProperties properties = propertiesWithApiKey("");
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(new AssertionError("request should not be sent")))
                .build();
        ClaudeClient client = new ClaudeClient(webClient, properties, objectMapper);

        assertError(client, "AI_SERVICE_ERROR", 500);
    }

    @Test
    void rejectsEmptyClaudeContent() {
        ClaudeClient client = clientWithResponse(HttpStatus.OK, "{\"content\": []}");

        assertError(client, "AI_SERVICE_ERROR", 500);
    }

    @Test
    void mapsUnauthorizedResponseToAuthError() {
        ClaudeClient client = clientWithResponse(HttpStatus.UNAUTHORIZED, "{\"error\":\"secret detail\"}");

        assertError(client, "AI_AUTH_ERROR", 502);
    }

    @Test
    void mapsForbiddenResponseToAuthError() {
        ClaudeClient client = clientWithResponse(HttpStatus.FORBIDDEN, "{\"error\":\"secret detail\"}");

        assertError(client, "AI_AUTH_ERROR", 502);
    }

    @Test
    void mapsRateLimitResponse() {
        ClaudeClient client = clientWithResponse(HttpStatus.TOO_MANY_REQUESTS, "{\"error\":\"rate limited\"}");

        assertError(client, "AI_RATE_LIMITED", 429);
    }

    @Test
    void mapsServerErrorResponse() {
        ClaudeClient client = clientWithResponse(HttpStatus.BAD_GATEWAY, "{\"error\":\"upstream detail\"}");

        assertError(client, "AI_SERVICE_UNAVAILABLE", 502);
    }

    @Test
    void hidesUnexpectedClientErrorBody() {
        ClaudeClient client = clientWithResponse(HttpStatus.BAD_REQUEST, "{\"error\":\"prompt body detail\"}");

        assertThatThrownBy(() -> client.generate("system", null, "user"))
                .isInstanceOf(CoteaException.class)
                .satisfies(error -> {
                    CoteaException coteaException = (CoteaException) error;
                    assertThat(coteaException.getErrorCode()).isEqualTo("AI_SERVICE_ERROR");
                    assertThat(coteaException.getStatus()).isEqualTo(502);
                    assertThat(coteaException.getMessage()).doesNotContain("prompt body detail");
                });
    }

    private ClaudeClient clientWithResponse(HttpStatus status, String body) {
        ClientResponse response = ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body(body)
                .build();
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(response))
                .build();
        return new ClaudeClient(webClient, propertiesWithApiKey("test-api-key"), objectMapper);
    }

    private CoteaProperties propertiesWithApiKey(String apiKey) {
        CoteaProperties properties = new CoteaProperties();
        properties.getClaude().setApiKey(apiKey);
        return properties;
    }

    private void assertError(ClaudeClient client, String errorCode, int status) {
        assertThatThrownBy(() -> client.generate("system", null, "user"))
                .isInstanceOf(CoteaException.class)
                .satisfies(error -> {
                    CoteaException coteaException = (CoteaException) error;
                    assertThat(coteaException.getErrorCode()).isEqualTo(errorCode);
                    assertThat(coteaException.getStatus()).isEqualTo(status);
                });
    }
}
