package com.cotea.service.learning;

import com.cotea.controller.dto.LearningReportResponse;
import com.cotea.controller.dto.RecommendedProblem;
import com.cotea.controller.dto.ReportCountItem;
import com.cotea.service.auth.JwtTokenProvider;
import com.cotea.service.learning.entity.UserHintLogEntity;
import com.cotea.service.recommend.RecommendationService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LearningReportService {

    private static final int DEFAULT_LIMIT = 3;

    private final JwtTokenProvider jwtTokenProvider;
    private final UserHintLogAnalytics hintLogAnalytics;
    private final RecommendationService recommendationService;

    public LearningReportResponse weekly(String authorization, int days) {
        int periodDays = hintLogAnalytics.normalizeDays(days);
        Long userId = jwtTokenProvider.parseUserId(authorization);
        List<UserHintLogEntity> logs = hintLogAnalytics.findRecentLogs(userId, periodDays);

        List<ReportCountItem> topWeaknessTypes = topItems(
                hintLogAnalytics.countWeaknessTypes(logs),
                DEFAULT_LIMIT,
                this::weaknessLabel,
                this::weaknessMessage
        );
        List<ReportCountItem> topIntents = topItems(
                hintLogAnalytics.countIntents(logs),
                DEFAULT_LIMIT,
                this::intentLabel,
                this::intentMessage
        );
        List<ReportCountItem> topTags = topItems(
                hintLogAnalytics.countTags(logs),
                DEFAULT_LIMIT,
                this::tagLabel,
                tag -> "최근 자주 질문한 알고리즘 유형입니다."
        );

        int solvedProblemCount = solvedProblemCount(logs);
        int solvedCount = (int) logs.stream().filter(log -> Boolean.TRUE.equals(log.getSolved())).count();
        int unresolvedHintCount = logs.size() - solvedCount;
        List<String> insights = insights(logs, topWeaknessTypes, topIntents, topTags, solvedProblemCount);
        List<RecommendedProblem> reviewRecommendations = reviewRecommendations(logs, topTags);
        List<RecommendedProblem> diversityRecommendations = diversityRecommendations(logs);
        List<RecommendedProblem> recommendations = combinedRecommendations(
                reviewRecommendations,
                diversityRecommendations,
                DEFAULT_LIMIT
        );

        return LearningReportResponse.builder()
                .periodDays(periodDays)
                .totalHintCount(logs.size())
                .solvedProblemCount(solvedProblemCount)
                .solvedCount(solvedCount)
                .unresolvedHintCount(unresolvedHintCount)
                .topWeaknessTypes(topWeaknessTypes)
                .topIntents(topIntents)
                .topTags(topTags)
                .insights(insights)
                .recommendations(recommendations)
                .reviewRecommendations(reviewRecommendations)
                .diversityRecommendations(diversityRecommendations)
                .summary(summary(logs.size(), topWeaknessTypes, topIntents, topTags, recommendations, periodDays))
                .build();
    }

    private int solvedProblemCount(List<UserHintLogEntity> logs) {
        return (int) logs.stream()
                .filter(log -> Boolean.TRUE.equals(log.getSolved()))
                .map(UserHintLogEntity::getProblemId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    private List<ReportCountItem> topItems(
            Map<String, Long> counts,
            int limit,
            java.util.function.Function<String, String> labelFactory,
            java.util.function.Function<String, String> messageFactory
    ) {
        List<ReportCountItem> items = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .forEach(entry -> items.add(ReportCountItem.builder()
                        .code(entry.getKey())
                        .name(labelFactory.apply(entry.getKey()))
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
            List<RecommendedProblem> recommendations,
            int periodDays
    ) {
        if (totalHintCount == 0) {
            return "최근 " + periodDays + "일 동안 저장된 힌트 요청이 없습니다.";
        }

        String weakness = topWeaknessTypes.isEmpty() ? "특정 약점 유형" : topWeaknessTypes.get(0).getName();
        String intent = topIntents.isEmpty() ? "세부 질문 유형" : topIntents.get(0).getName();
        String tag = topTags.isEmpty() ? "특정 알고리즘" : topTags.get(0).getName();

        String recommendationPhrase = recommendations.isEmpty()
                ? ""
                : " 반복된 약점과 아직 적게 풀어본 유형을 기준으로 추천 문제도 함께 준비했습니다.";

        return "최근 " + periodDays + "일 동안 " + weakness
                + " 유형의 질문이 가장 많았습니다. 특히 " + tag
                + " 문제에서 " + intent + " 관련 막힘이 반복되는지 점검해보면 좋습니다."
                + recommendationPhrase;
    }

    private List<String> insights(
            List<UserHintLogEntity> logs,
            List<ReportCountItem> topWeaknessTypes,
            List<ReportCountItem> topIntents,
            List<ReportCountItem> topTags,
            int solvedProblemCount
    ) {
        if (logs.isEmpty()) {
            return List.of("아직 리포트를 만들 만큼의 힌트 기록이 없습니다.");
        }

        List<String> insights = new ArrayList<>();
        if (!topWeaknessTypes.isEmpty()) {
            ReportCountItem top = topWeaknessTypes.get(0);
            insights.add(top.getName() + " 유형 질문이 " + top.getCount() + "회로 가장 많았습니다. " + top.getMessage());
        }
        if (!topIntents.isEmpty()) {
            ReportCountItem top = topIntents.get(0);
            insights.add("세부적으로는 " + top.getName() + " 관련 질문이 반복되었습니다. " + top.getMessage());
        }
        if (!topTags.isEmpty()) {
            ReportCountItem top = topTags.get(0);
            insights.add(top.getName() + " 태그 문제에서 힌트 요청이 가장 많았습니다.");
        }
        if (solvedProblemCount > 0) {
            insights.add("최근 기록 기준으로 해결한 문제는 " + solvedProblemCount + "개입니다.");
        }
        insights.add("AI에게 질문한 횟수는 총 " + logs.size() + "회입니다.");
        return insights;
    }

    private List<RecommendedProblem> reviewRecommendations(
            List<UserHintLogEntity> logs,
            List<ReportCountItem> topTags
    ) {
        if (logs.isEmpty()) {
            return List.of();
        }

        List<Integer> excludedProblemIds = recentProblemIds(logs);
        List<String> tagOverride = topTags.isEmpty() ? List.of() : List.of(topTags.get(0).getCode());
        if (!tagOverride.isEmpty()) {
            List<RecommendedProblem> tagBasedRecommendations = recommendationService
                    .recommendByTags(tagOverride, excludedProblemIds, 2)
                    .getRecommendations();
            if (!tagBasedRecommendations.isEmpty()) {
                return tagBasedRecommendations;
            }
        }

        Integer sourceProblemId = logs.get(logs.size() - 1).getProblemId();
        if (sourceProblemId == null) {
            return List.of();
        }

        try {
            return recommendationService.recommend(sourceProblemId, tagOverride, 2).getRecommendations();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<RecommendedProblem> diversityRecommendations(List<UserHintLogEntity> logs) {
        if (logs.isEmpty()) {
            return List.of();
        }
        return recommendationService
                .recommendUnderPracticed(practicedTagCounts(logs), recentProblemIds(logs), DEFAULT_LIMIT)
                .getRecommendations();
    }

    private List<RecommendedProblem> combinedRecommendations(
            List<RecommendedProblem> reviewRecommendations,
            List<RecommendedProblem> diversityRecommendations,
            int limit
    ) {
        List<RecommendedProblem> result = new ArrayList<>();
        result.addAll(reviewRecommendations);
        result.addAll(diversityRecommendations);
        return deduplicateRecommendations(result, limit);
    }

    private Map<String, Long> practicedTagCounts(List<UserHintLogEntity> logs) {
        List<UserHintLogEntity> solvedLogs = logs.stream()
                .filter(log -> Boolean.TRUE.equals(log.getSolved()))
                .collect(Collectors.toList());
        return hintLogAnalytics.countTags(solvedLogs.isEmpty() ? logs : solvedLogs);
    }

    private List<RecommendedProblem> deduplicateRecommendations(List<RecommendedProblem> recommendations, int limit) {
        List<RecommendedProblem> unique = new ArrayList<>();
        for (RecommendedProblem recommendation : recommendations) {
            if (recommendation.getProblemId() == null) {
                continue;
            }
            boolean exists = unique.stream()
                    .anyMatch(item -> recommendation.getProblemId().equals(item.getProblemId()));
            if (!exists) {
                unique.add(recommendation);
            }
            if (unique.size() >= limit) {
                break;
            }
        }
        return unique;
    }

    private List<Integer> recentProblemIds(List<UserHintLogEntity> logs) {
        return logs.stream()
                .map(UserHintLogEntity::getProblemId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
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

    private String weaknessLabel(String weaknessType) {
        return switch (weaknessType) {
            case "CONCEPT" -> "개념 이해";
            case "APPROACH" -> "접근 방향 잡기";
            case "IMPLEMENTATION" -> "구현으로 옮기기";
            case "DEBUGGING" -> "오답 원인 찾기";
            case "COMPLEXITY" -> "시간복잡도 판단";
            case "SYNTAX" -> "Java 문법/API";
            default -> "기타 질문";
        };
    }

    private String intentMessage(String intent) {
        return switch (intent) {
            case "VISITED_HANDLING" -> "방문 처리 기준을 정하는 연습이 필요할 수 있습니다.";
            case "TIME_COMPLEXITY" -> "풀이 전 입력 크기와 예상 시간복잡도를 먼저 확인해보면 좋습니다.";
            case "BOUNDARY_CONDITION" -> "경계 조건과 인덱스 범위를 점검하는 습관이 도움이 됩니다.";
            case "IMPLEMENTATION_ORDER" -> "구현 전에 처리 순서를 짧게 정리해보면 좋습니다.";
            case "DATA_STRUCTURE" -> "문제 조건에 맞는 자료구조를 먼저 고르는 연습이 도움이 됩니다.";
            case "STATE_DEFINITION" -> "반복문/탐색/DP에서 관리할 상태를 먼저 정의해보면 좋습니다.";
            case "APPROACH_UNKNOWN" -> "문제 조건을 풀이 관점으로 바꾸는 연습이 필요할 수 있습니다.";
            case "ALGORITHM_CONCEPT" -> "관련 알고리즘의 기본 개념을 먼저 복습하면 좋습니다.";
            case "DEBUG_REASON" -> "틀린 이유를 찾을 때 조건, 반례, 상태 변화를 순서대로 확인해보면 좋습니다.";
            case "RUNTIME_REASON" -> "예외가 날 수 있는 인덱스, null, 빈 입력 조건을 먼저 확인해보면 좋습니다.";
            case "SYNTAX" -> "Java 문법과 자주 쓰는 컬렉션 API 사용법을 함께 정리해두면 좋습니다.";
            case "OFF_TOPIC" -> "문제 풀이와 직접 관련되지 않은 질문도 일부 포함되어 있습니다.";
            default -> "반복적으로 등장한 세부 질문 유형입니다.";
        };
    }

    private String intentLabel(String intent) {
        return switch (intent) {
            case "APPROACH_UNKNOWN" -> "처음 접근 방법";
            case "ALGORITHM_CONCEPT" -> "알고리즘 개념";
            case "DATA_STRUCTURE" -> "자료구조 선택";
            case "IMPLEMENTATION_ORDER" -> "구현 순서";
            case "VISITED_HANDLING" -> "방문 처리";
            case "BOUNDARY_CONDITION" -> "경계 조건/반례";
            case "STATE_DEFINITION" -> "상태 정의";
            case "TIME_COMPLEXITY" -> "시간복잡도";
            case "DEBUG_REASON" -> "오답 원인";
            case "RUNTIME_REASON" -> "런타임 에러 원인";
            case "SYNTAX" -> "Java 문법/API";
            case "OFF_TOPIC" -> "문제 외 질문";
            default -> "기타 막힘";
        };
    }

    private String tagLabel(String tag) {
        String normalized = tag == null ? "" : tag.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "dfs" -> "DFS";
            case "bfs" -> "BFS";
            case "dp" -> "동적 계획법";
            case "greedy" -> "그리디";
            case "implementation" -> "구현";
            case "brute_force", "bruteforcing" -> "완전탐색";
            case "binary_search" -> "이분 탐색";
            case "two_pointer" -> "투 포인터";
            case "sliding_window" -> "슬라이딩 윈도우";
            case "graph", "graph_traversal" -> "그래프";
            case "tree", "trees" -> "트리";
            case "string" -> "문자열";
            case "sort", "sorting" -> "정렬";
            case "hash", "hash_map", "hash_set" -> "해시";
            case "stack" -> "스택";
            case "queue", "queue_deque" -> "큐";
            case "priority_queue", "heap" -> "우선순위 큐/힙";
            case "prefix_sum" -> "누적합";
            case "simulation" -> "시뮬레이션";
            case "backtracking" -> "백트래킹";
            case "array" -> "배열";
            case "math" -> "수학";
            default -> tag == null || tag.isBlank() ? "기타 알고리즘" : tag;
        };
    }
}
