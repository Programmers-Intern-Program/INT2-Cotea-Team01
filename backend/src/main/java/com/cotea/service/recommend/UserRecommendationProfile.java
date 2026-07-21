package com.cotea.service.recommend;

import java.util.Map;
import java.util.Set;

/**
 * 추천 랭킹에 쓰는 사용자별 읽기 모델.
 *
 * <p>HintLogUserWeaknessProvider가 weakTagCounts를 채운다.
 * solvedProblemIds는 풀이 이력 API가 준비되면 후속 연동한다.
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
