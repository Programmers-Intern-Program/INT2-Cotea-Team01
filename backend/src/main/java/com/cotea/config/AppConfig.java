package com.cotea.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(CoteaProperties.class)
public class AppConfig {

    @Bean
    WebClient claudeWebClient(CoteaProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getClaude().getBaseUrl())
                .build();
    }

    @Bean
    WebClient openAiWebClient(CoteaProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getOpenAi().getBaseUrl())
                .build();
    }
}
