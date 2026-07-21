package com.cotea.service.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cotea.service.learning.UserHintLogAnalytics;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HintLogUserWeaknessProviderTest {

    @Mock
    private UserHintLogAnalytics hintLogAnalytics;

    @InjectMocks
    private HintLogUserWeaknessProvider provider;

    @Test
    void 로그가_없으면_빈_프로필을_반환한다() {
        when(hintLogAnalytics.countTagsForUser(1L, UserHintLogAnalytics.DEFAULT_PROFILE_DAYS))
                .thenReturn(Map.of());

        assertThat(provider.getProfile(1L)).isEqualTo(UserRecommendationProfile.empty());
    }

    @Test
    void 태그_집계만_채우고_문제_제외_목록은_비운다() {
        when(hintLogAnalytics.countTagsForUser(1L, UserHintLogAnalytics.DEFAULT_PROFILE_DAYS))
                .thenReturn(Map.of("bfs", 3L, "dp", 1L));

        UserRecommendationProfile profile = provider.getProfile(1L);

        assertThat(profile.weakTagCounts()).containsEntry("bfs", 3L);
        assertThat(profile.solvedProblemIds()).isEmpty();
        assertThat(profile.hasPersonalization()).isTrue();
    }
}
