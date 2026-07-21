package com.cotea.service.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.cotea.service.learning.entity.UserHintLogEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserHintLogAnalyticsTest {

    @Mock
    private UserHintLogRepository userHintLogRepository;

    private UserHintLogAnalytics analytics;

    @BeforeEach
    void setUp() {
        analytics = new UserHintLogAnalytics(userHintLogRepository, new ObjectMapper());
    }

    @Test
    void 최근_로그에서_태그별_빈도를_집계한다() {
        when(userHintLogRepository.findByUserIdAndCreatedAtAfter(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        logWithTags("[\"bfs\",\"graph_traversal\"]"),
                        logWithTags("[\"bfs\"]")));

        var counts = analytics.countTagsForUser(1L, 7);

        assertThat(counts).containsEntry("bfs", 2L);
        assertThat(counts).containsEntry("graph_traversal", 1L);
    }

    @Test
    void 태그_JSON이_깨지면_무시한다() {
        when(userHintLogRepository.findByUserIdAndCreatedAtAfter(eq(2L), any(LocalDateTime.class)))
                .thenReturn(List.of(logWithTags("not-json")));

        assertThat(analytics.countTagsForUser(2L, 7)).isEmpty();
    }

    @Test
    void days_상한은_30일이다() {
        assertThat(analytics.normalizeDays(100)).isEqualTo(30);
        assertThat(analytics.normalizeDays(0)).isEqualTo(UserHintLogAnalytics.DEFAULT_PROFILE_DAYS);
    }

    @Test
    void 로그에서_problemId_집합을_모은다() {
        UserHintLogEntity a = logWithTags("[\"bfs\"]");
        ReflectionTestUtils.setField(a, "problemId", 100);
        UserHintLogEntity b = logWithTags("[\"dfs\"]");
        ReflectionTestUtils.setField(b, "problemId", 100);
        UserHintLogEntity c = logWithTags("[\"dp\"]");
        ReflectionTestUtils.setField(c, "problemId", 200);

        assertThat(analytics.collectProblemIds(List.of(a, b, c)))
                .containsExactlyInAnyOrder(100, 200);
    }

    private static UserHintLogEntity logWithTags(String tagsJson) {
        UserHintLogEntity entity = new UserHintLogEntity();
        ReflectionTestUtils.setField(entity, "problemTags", tagsJson);
        return entity;
    }
}
