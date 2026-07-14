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

    @Getter
    @Setter
    public static class Claude {
        private String apiKey;
        private String model = "claude-sonnet-4-6";
        private int maxTokens = 1024;
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
    }

    @Getter
    @Setter
    public static class OffTopic {
        /** FREE_TEXT 질문을 저렴한 OpenAI(실패 시 Claude)로 돌릴지 */
        private boolean enabled = true;
    }
}
