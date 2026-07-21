package com.cotea.service.recommend;

import com.cotea.service.learning.UserHintLogAnalytics;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * user_hint_log 기반 추천 개인화 프로필 제공.
 *
 * <p>최근 7일 힌트 로그의 problem_tags 빈도를 weakTagCounts로 반환한다.
 * solvedProblemIds는 풀이 이력 API 연동 전까지 비운다.
 * (힌트만 요청한 문제는 연습 후보로 남을 수 있어야 하므로 제외하지 않는다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HintLogUserWeaknessProvider implements UserWeaknessProvider {

    private final UserHintLogAnalytics hintLogAnalytics;

    @Override
    public UserRecommendationProfile getProfile(long userId) {
        Map<String, Long> weakTagCounts = hintLogAnalytics.countTagsForUser(
                userId, UserHintLogAnalytics.DEFAULT_PROFILE_DAYS);
        if (weakTagCounts.isEmpty()) {
            return UserRecommendationProfile.empty();
        }
        log.info("[RECOMMEND_PROFILE] userId={} weakTagCount={}", userId, weakTagCounts.size());
        return new UserRecommendationProfile(Set.of(), weakTagCounts);
    }
}
