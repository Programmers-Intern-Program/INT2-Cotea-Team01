package com.cotea.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cotea")
public class CoteaProperties {

    private final Gemini gemini = new Gemini();
    private final ProblemMeta problemMeta = new ProblemMeta();
    private final Rag rag = new Rag();

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey;
        private String model = "gemini-2.5-flash";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
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
