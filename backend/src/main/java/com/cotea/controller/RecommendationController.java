package com.cotea.controller;

import com.cotea.controller.dto.RecommendationResponse;
import com.cotea.service.recommend.RecommendationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * 현재 막힌 문제 기준으로 같은 유형의 더 쉬운/유사한 문제를 추천한다.
     *
     * @param problemId 현재 풀고 있는(막힌) 문제 ID
     * @param tags      선택 — 태그를 직접 지정해 후보 태그를 덮어쓰고 싶을 때 (예: bfs,dfs)
     * @param limit     선택 — 추천 개수 (기본 3)
     */
    @GetMapping("/recommend")
    public RecommendationResponse recommend(
            @RequestParam("problemId") int problemId,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return recommendationService.recommend(problemId, tags, limit);
    }
}
