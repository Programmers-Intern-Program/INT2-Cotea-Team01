package com.cotea.service.problem.generation;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 프로그래머스 문제 상세 페이지 raw HTML을 가져온다. 로그인 여부와 무관하게 problemId/title/level과
 * 문제 본문이 서버사이드 렌더링돼 있음을 실제 HTML로 확인했다(docs/problem-data-generation-prompt.md §1)
 * — 인증이나 JS 실행 없이 단순 GET으로 충분하다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProblemPageFetcher {

    private final WebClient programmersWebClient;

    public String fetchHtml(int problemId) {
        try {
            String html = programmersWebClient.get()
                    .uri("/learn/courses/30/lessons/{problemId}", problemId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(20));

            if (html == null || html.isBlank()) {
                throw new ProblemHtmlParseException("problemId=" + problemId + " 페이지 응답이 비어있습니다.");
            }
            return html;
        } catch (WebClientResponseException e) {
            log.warn("문제 페이지 조회 실패 problemId={} status={}", problemId, e.getStatusCode());
            throw new ProblemHtmlParseException(
                    "problemId=" + problemId + " 페이지를 가져오지 못했습니다 (status=" + e.getStatusCode() + ")");
        } catch (WebClientRequestException e) {
            log.warn("문제 페이지 조회 요청 실패 problemId={}: {}", problemId, e.getMessage());
            throw new ProblemHtmlParseException("problemId=" + problemId + " 페이지 요청에 실패했습니다: " + e.getMessage());
        }
    }
}
