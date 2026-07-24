package com.cotea.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cotea")
public class CoteaProperties {

    private final Claude claude = new Claude();
    private final OpenAi openAi = new OpenAi();
    private final ProblemMeta problemMeta = new ProblemMeta();
    private final Rag rag = new Rag();
    private final OffTopic offTopic = new OffTopic();
    private final Kakao kakao = new Kakao();
    private final Jwt jwt = new Jwt();

    @Getter
    @Setter
    public static class Claude {
        private String apiKey;
        private String model = "claude-sonnet-4-6";
        private int maxTokens = 1024;
        /**
         * 문제 데이터 생성(JSON 스키마 전체 출력)은 힌트 응답보다 훨씬 길어서 별도 한도가 필요하다.
         * claude-sonnet-4-6의 실제 상한은 128k지만, 이 스키마가 그 정도로 길어질 일은 없으므로
         * 여유 있게 16000으로 잡는다(그래도 잘리면 이 값을 더 올리면 된다).
         */
        private int problemGenerationMaxTokens = 16000;
        private String baseUrl = "https://api.anthropic.com";
    }

    @Getter
    @Setter
    public static class OpenAi {
        private String apiKey;
        private String model = "gpt-4o-mini";
        private int maxTokens = 512;
        private String baseUrl = "https://api.openai.com";
    }

    @Getter
    @Setter
    public static class ProblemMeta {
        private String directory = "../rag/problems";
    }

    @Getter
    @Setter
    public static class Rag {
        private boolean enabled;
        private String directory = "../rag";
    }

    @Getter
    @Setter
    public static class OffTopic {
        /** FREE_TEXT 질문을 저렴한 OpenAI(실패 시 Claude)로 돌릴지 */
        private boolean enabled = true;
        /**
         * 규칙으로 판별되지 않은 FREE_TEXT를 OpenAI로 RELATED/OFF_TOPIC 라우팅할지.
         * false면 ambiguous는 RELATED로 처리한다.
         */
        private boolean llmRouteEnabled = true;
    }

    @Getter
    @Setter
    public static class Kakao {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String authBaseUrl = "https://kauth.kakao.com";
        private String apiBaseUrl = "https://kapi.kakao.com";
    }

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenTtlSeconds = 60 * 60 * 24 * 7;
        private String issuer = "cotea";
    }
}
