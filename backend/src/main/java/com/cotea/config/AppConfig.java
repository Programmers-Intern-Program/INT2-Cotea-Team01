package com.cotea.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(CoteaProperties.class)
public class AppConfig {

    @Bean
    WebClient geminiWebClient(CoteaProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getGemini().getBaseUrl())
                .build();
    }
}
