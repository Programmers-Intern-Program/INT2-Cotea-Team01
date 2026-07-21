package com.cotea.service.learning;

import com.cotea.controller.dto.LearningReportResponse;
import com.cotea.controller.dto.ReportCountItem;
import com.cotea.service.auth.JwtTokenProvider;
import com.cotea.service.learning.entity.UserHintLogEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LearningReportService {

    private static final int DEFAULT_LIMIT = 3;

    private final JwtTokenProvider jwtTokenProvider;
    private final UserHintLogRepository userHintLogRepository;
    private final ObjectMapper objectMapper;

    public LearningReportResponse weekly(String authorization, int days) {
        int periodDays = normalizeDays(days);
        Long userId = jwtTokenProvider.parseUserId(authorization);
        LocalDateTime since = LocalDateTime.now().minusDays(periodDays);
        List<UserHintLogEntity> logs = userHintLogRepository.findByUserIdAndCreatedAtAfter(userId, since);

        List<ReportCountItem> topWeaknessTypes = topItems(countWeaknessTypes(logs), DEFAULT_LIMIT, this::weaknessMessage);
        List<ReportCountItem> topIntents = topItems(countIntents(logs), DEFAULT_LIMIT, this::intentMessage);
        List<ReportCountItem> topTags = topItems(countTags(logs), DEFAULT_LIMIT, tag -> "최근 자주 질문한 문제 유형입니다.");

        return LearningReportResponse.builder()
                .periodDays(periodDays)
                .totalHintCount(logs.size())
                .topWeaknessTypes(topWeaknessTypes)
                .topIntents(topIntents)
                .topTags(topTags)
                .summary(summary(logs.size(), topWeaknessTypes, topIntents, topTags, periodDays))
                .build();
    }

    private int normalizeDays(int days) {
        if (days <= 0) {
            return 7;
        }
        return Math.min(days, 30);
    }

    private Map<String, Long> countWeaknessTypes(List<UserHintLogEntity> logs) {
        Map<String, Long> counts = new HashMap<>();
        for (UserHintLogEntity log : logs) {
            if (log.getWeaknessType() != null) {
                counts.merge(log.getWeaknessType().name(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private Map<String, Long> countIntents(List<UserHintLogEntity> logs) {
        Map<String, Long> counts = new HashMap<>();
        for (UserHintLogEntity log : logs) {
            if (log.getDetectedIntent() != null) {
                counts.merge(log.getDetectedIntent().name(), 1L, Long::sum);
            }
        }
        return counts;
    }

    private Map<String, Long> countTags(List<UserHintLogEntity> logs) {
        Map<String, Long> counts = new HashMap<>();
        for (UserHintLogEntity log : logs) {
            for (String tag : parseTags(log.getProblemTags())) {
                counts.merge(tag, 1L, Long::sum);
            }
        }
        return counts;
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

    private List<ReportCountItem> topItems(
            Map<String, Long> counts,
            int limit,
            java.util.function.Function<String, String> messageFactory
    ) {
        List<ReportCountItem> items = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .forEach(entry -> items.add(ReportCountItem.builder()
                        .name(entry.getKey())
                        .count(entry.getValue())
                        .message(messageFactory.apply(entry.getKey()))
                        .build()));
        return items;
    }

    private String summary(
            int totalHintCount,
            List<ReportCountItem> topWeaknessTypes,
            List<ReportCountItem> topIntents,
            List<ReportCountItem> topTags,
            int periodDays
    ) {
        if (totalHintCount == 0) {
            return "최근 " + periodDays + "일 동안 저장된 힌트 요청이 없습니다.";
        }

        String weakness = topWeaknessTypes.isEmpty() ? "특정 약점 유형" : topWeaknessTypes.get(0).getName();
        String intent = topIntents.isEmpty() ? "세부 질문 유형" : topIntents.get(0).getName();
        String tag = topTags.isEmpty() ? "특정 알고리즘" : topTags.get(0).getName();

        return "최근 " + periodDays + "일 동안 " + weakness
                + " 유형의 질문이 가장 많았습니다. 특히 " + tag
                + " 문제에서 " + intent + " 관련 막힘이 반복되는지 점검해보면 좋습니다.";
    }

    private String weaknessMessage(String weaknessType) {
        return switch (weaknessType) {
            case "CONCEPT" -> "알고리즘/자료구조 개념 자체를 확인하는 질문이 많습니다.";
            case "APPROACH" -> "문제를 어떤 방향으로 풀지 잡는 단계에서 자주 막힙니다.";
            case "IMPLEMENTATION" -> "구현 순서나 자료구조 적용 단계에서 자주 막힙니다.";
            case "DEBUGGING" -> "오답 원인이나 반례를 찾는 과정에서 자주 막힙니다.";
            case "COMPLEXITY" -> "시간복잡도와 효율성 관련 질문이 반복되었습니다.";
            case "SYNTAX" -> "Java 문법 또는 API 사용 관련 질문이 반복되었습니다.";
            default -> "기타 유형의 질문입니다.";
        };
    }

    private String intentMessage(String intent) {
        return switch (intent) {
            case "VISITED_HANDLING" -> "방문 처리 기준을 정하는 연습이 필요할 수 있습니다.";
            case "TIME_COMPLEXITY" -> "풀이 전 입력 크기와 예상 시간복잡도를 먼저 확인해보면 좋습니다.";
            case "BOUNDARY_CONDITION" -> "경계 조건과 인덱스 범위를 점검하는 습관이 도움이 됩니다.";
            case "IMPLEMENTATION_ORDER" -> "구현 전에 처리 순서를 짧게 정리해보면 좋습니다.";
            case "APPROACH_UNKNOWN" -> "문제 조건을 풀이 관점으로 바꾸는 연습이 필요할 수 있습니다.";
            case "ALGORITHM_CONCEPT" -> "관련 알고리즘의 기본 개념을 먼저 복습하면 좋습니다.";
            default -> "반복적으로 등장한 세부 질문 유형입니다.";
        };
    }
}
