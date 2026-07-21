package com.cotea.service.recommend;

import com.cotea.service.learning.UserHintLogAnalytics;
import com.cotea.service.learning.entity.UserHintLogEntity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * user_hint_log 기반 추천 개인화 프로필 제공.
 *
 * <p>최근 7일 힌트 로그의 problem_tags 빈도 → weakTagCounts,
 * problem_id 집합 → solvedProblemIds(추천 제외)로 채운다.
 * 실제 풀이 이력 API가 생기면 solvedProblemIds만 교체하면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HintLogUserWeaknessProvider implements UserWeaknessProvider {

    private final UserHintLogAnalytics hintLogAnalytics;

    @Override
    public UserRecommendationProfile getProfile(long userId) {
        List<UserHintLogEntity> logs = hintLogAnalytics.findRecentLogs(
                userId, UserHintLogAnalytics.DEFAULT_PROFILE_DAYS);
        Map<String, Long> weakTagCounts = hintLogAnalytics.countTags(logs);
        Set<Integer> hintedProblemIds = hintLogAnalytics.collectProblemIds(logs);
        if (weakTagCounts.isEmpty() && hintedProblemIds.isEmpty()) {
            return UserRecommendationProfile.empty();
        }
        log.info("[RECOMMEND_PROFILE] userId={} weakTags={} hintedProblems={}",
                userId, weakTagCounts.size(), hintedProblemIds.size());
        return new UserRecommendationProfile(hintedProblemIds, weakTagCounts);
    }
}
