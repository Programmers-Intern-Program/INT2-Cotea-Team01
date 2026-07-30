package com.cotea.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(CoteaProperties.class)
public class AppConfig {

    /** 문제 데이터 생성(HTML 조회~LLM~DB 저장)을 요청 스레드에서 떼어내 실행하는 전용 풀. */
    @Bean(destroyMethod = "shutdown")
    ExecutorService problemGenerationExecutor() {
        return Executors.newFixedThreadPool(4);
    }

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

    @Bean
    WebClient kakaoAuthWebClient(CoteaProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getKakao().getAuthBaseUrl())
                .build();
    }

    @Bean
    WebClient kakaoApiWebClient(CoteaProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.getKakao().getApiBaseUrl())
                .build();
    }

    /** 문제 데이터가 없을 때 프로그래머스 문제 상세 페이지 HTML을 가져오는 용도. */
    @Bean
    WebClient programmersWebClient() {
        return WebClient.builder()
                .baseUrl("https://school.programmers.co.kr")
                .build();
    }
}
