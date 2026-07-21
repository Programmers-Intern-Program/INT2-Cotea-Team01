package com.cotea.service.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.cotea.service.learning.UserHintLogAnalytics;
import com.cotea.service.learning.entity.UserHintLogEntity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HintLogUserWeaknessProviderTest {

    @Mock
    private UserHintLogAnalytics hintLogAnalytics;

    @InjectMocks
    private HintLogUserWeaknessProvider provider;

    @Test
    void 로그가_없으면_빈_프로필을_반환한다() {
        when(hintLogAnalytics.findRecentLogs(eq(1L), anyInt())).thenReturn(List.of());
        when(hintLogAnalytics.countTags(List.of())).thenReturn(Map.of());
        when(hintLogAnalytics.collectProblemIds(List.of())).thenReturn(Set.of());

        assertThat(provider.getProfile(1L)).isEqualTo(UserRecommendationProfile.empty());
    }

    @Test
    void 태그_집계와_힌트_문제_ID를_프로필로_반환한다() {
        List<UserHintLogEntity> logs = List.of(log(100, "[\"bfs\"]"), log(200, "[\"bfs\"]"));
        when(hintLogAnalytics.findRecentLogs(eq(1L), anyInt())).thenReturn(logs);
        when(hintLogAnalytics.countTags(logs)).thenReturn(Map.of("bfs", 2L));
        when(hintLogAnalytics.collectProblemIds(logs)).thenReturn(Set.of(100, 200));

        UserRecommendationProfile profile = provider.getProfile(1L);

        assertThat(profile.weakTagCounts()).containsEntry("bfs", 2L);
        assertThat(profile.solvedProblemIds()).containsExactlyInAnyOrder(100, 200);
        assertThat(profile.hasPersonalization()).isTrue();
    }

    private static UserHintLogEntity log(int problemId, String tagsJson) {
        UserHintLogEntity entity = new UserHintLogEntity();
        ReflectionTestUtils.setField(entity, "problemId", problemId);
        ReflectionTestUtils.setField(entity, "problemTags", tagsJson);
        return entity;
    }
}
