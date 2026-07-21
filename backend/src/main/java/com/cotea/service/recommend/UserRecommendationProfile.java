package com.cotea.service.recommend;

import java.util.Map;
import java.util.Set;

/**
 * 추천 랭킹에 쓰는 사용자별 읽기 모델.
 *
 * <p>{@code weakTagCounts}: 최근 힌트 로그의 태그 빈도 (가산점·reason용).
 * {@code solvedProblemIds}: 실제 푼 문제 제외용 — 풀이 이력 API 연동 전까지 비움.
 * 힌트만 요청한 문제는 여기에 넣지 않는다.
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
