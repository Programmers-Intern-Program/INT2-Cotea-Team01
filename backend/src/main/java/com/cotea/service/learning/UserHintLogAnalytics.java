package com.cotea.service.learning;

import com.cotea.service.learning.entity.UserHintLogEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * user_hint_log 집계 공통 로직. 주간 리포트와 추천 개인화가 같은 기준을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class UserHintLogAnalytics {

    public static final int DEFAULT_PROFILE_DAYS = 7;
    private static final int MAX_PROFILE_DAYS = 30;

    private final UserHintLogRepository userHintLogRepository;
    private final ObjectMapper objectMapper;

    public List<UserHintLogEntity> findRecentLogs(long userId, int days) {
        int periodDays = normalizeDays(days);
        LocalDateTime since = LocalDateTime.now().minusDays(periodDays);
        return userHintLogRepository.findByUserIdAndCreatedAtAfter(userId, since);
    }

    public Map<String, Long> countTagsForUser(long userId, int days) {
        return countTags(findRecentLogs(userId, days));
    }

    /** 최근 힌트를 요청한 문제 ID 집합 (추천 제외용). */
    public Set<Integer> collectProblemIds(List<UserHintLogEntity> logs) {
        Set<Integer> ids = new HashSet<>();
        for (UserHintLogEntity log : logs) {
            if (log.getProblemId() != null) {
                ids.add(log.getProblemId());
            }
        }
        return ids;
    }

    public Map<String, Long> countTags(List<UserHintLogEntity> logs) {
        Map<String, Long> counts = new HashMap<>();
        for (UserHintLogEntity log : logs) {
            for (String tag : parseTags(log.getProblemTags())) {
                counts.merge(tag, 1L, Long::sum);
            }
        }
        return counts;
    }

    public Map<String, Long> countWeaknessTypes(List<UserHintLogEntity> logs) {
        Map<String, Long> counts = new HashMap<>();
        for (UserHintLogEntity log : logs) {
            if (log.getWeaknessType() != null) {
                counts.merge(log.getWeaknessType().name(), 1L, Long::sum);
            }
        }
        return counts;
    }

    public Map<String, Long> countIntents(List<UserHintLogEntity> logs) {
        Map<String, Long> counts = new HashMap<>();
        for (UserHintLogEntity log : logs) {
            if (log.getDetectedIntent() != null) {
                counts.merge(log.getDetectedIntent().name(), 1L, Long::sum);
            }
        }
        return counts;
    }

    int normalizeDays(int days) {
        if (days <= 0) {
            return DEFAULT_PROFILE_DAYS;
        }
        return Math.min(days, MAX_PROFILE_DAYS);
    }

    private List<String> parseTags(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawTags, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
