package com.cotea.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cotea")
public class CoteaProperties {

    private final Claude claude = new Claude();
    private final ProblemMeta problemMeta = new ProblemMeta();
    private final Rag rag = new Rag();

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
    public static class ProblemMeta {
        private String directory = "../rag/problems";
    }

    @Getter
    @Setter
    public static class Rag {
        private boolean enabled;
    }
}
