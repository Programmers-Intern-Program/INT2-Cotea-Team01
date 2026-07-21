package com.cotea.service.recommend;

import java.util.Map;
import java.util.Set;

/**
 * 추천 랭킹에 쓰는 사용자별 읽기 모델.
 *
 * <p>{@code weakTagCounts}: 최근 힌트 로그의 태그 빈도.
 * {@code solvedProblemIds}: 추천에서 제외할 문제 — MVP는 힌트 요청한 problem_id,
 * 풀이 이력 API가 생기면 그 소스로 교체한다.
 */
public record UserRecommendationProfile(
        Set<Integer> solvedProblemIds,
        Map<String, Long> weakTagCounts
) {

    public static UserRecommendationProfile empty() {
        return new UserRecommendationProfile(Set.of(), Map.of());
    }

    public boolean hasPersonalization() {
        return !solvedProblemIds.isEmpty() || !weakTagCounts.isEmpty();
    }
}
