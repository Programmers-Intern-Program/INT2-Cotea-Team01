package com.cotea.service.problem.generation;

import java.util.List;

/**
 * 프로그래머스 문제 상세 페이지 raw HTML에서 파싱한 결과.
 * problemId/title/level/url은 LLM이 추측하지 않고 그대로 쓰는 고정값이다
 * (docs/problem-data-generation-prompt.md §1 참고 — data-lesson-id/data-lesson-title/
 * data-challenge-level 속성에서 직접 파싱되며, 로그인 여부와 무관하게 raw HTML에 존재함을 확인함).
 */
public record ParsedProblem(
        int problemId,
        String title,
        String level,
        String url,
        String bodyText,
        String bodyHtml,
        List<String> imageUrls
) {
}
