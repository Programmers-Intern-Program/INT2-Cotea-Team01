package com.cotea.service.recommend;

import com.cotea.controller.dto.RecommendationResponse;
import com.cotea.controller.dto.RecommendedProblem;
import com.cotea.exception.CoteaException;
import com.cotea.service.auth.JwtTokenProvider;
import com.cotea.service.problem.ProblemClassificationRepository;
import com.cotea.service.problem.ProblemMetaRepository;
import com.cotea.service.problem.entity.ProblemClassificationEntity;
import com.cotea.service.problem.entity.ProblemEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 막힌 문제를 기준으로 "같은 유형(태그)의 더 쉬운/유사한 문제"를 추천한다.
 *
 * <p>로그인 사용자는 {@link UserWeaknessProvider} 프로필(푼 문제 제외·약점 태그 가중)을 반영한다.
 * 비로그인·프로필 없음은 태그 기반 stateless와 동일하게 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 3;
    /** weakTagCounts 1회당 가산점 상한 (태그별) */
    private static final int WEAK_TAG_SCORE_CAP = 12;

    private final ProblemMetaRepository problemMetaRepository;
    private final ProblemClassificationRepository classificationRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserWeaknessProvider userWeaknessProvider;

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(int problemId, List<String> tagOverride, Integer limit) {
        return recommend(problemId, tagOverride, limit, null);
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(
            int problemId,
            List<String> tagOverride,
            Integer limit,
            String authorization
    ) {
        UserRecommendationProfile profile = jwtTokenProvider.resolveUserId(authorization)
                .map(userWeaknessProvider::getProfile)
                .orElse(UserRecommendationProfile.empty());
        ProblemEntity source = problemMetaRepository.findById(problemId)
                .orElseThrow(() -> new CoteaException(
                        "MISSING_PROBLEM_ID",
                        "문제 메타데이터를 찾을 수 없습니다: " + problemId,
                        404));

        Set<String> sourceTags = resolveSourceTags(source, tagOverride);
        if (sourceTags.isEmpty()) {
            log.info("[RECOMMEND] problemId={} 태그 없음 → 추천 없음", problemId);
            return RecommendationResponse.builder()
                    .sourceProblemId(problemId)
                    .sourceTags(List.of())
                    .recommendations(List.of())
                    .build();
        }

        Set<String> sourceTagSub = source.getClassifications().stream()
                .filter(c -> c.getSubcategory() != null)
                .map(c -> tagSubKey(c.getTag(), c.getSubcategory()))
                .collect(Collectors.toSet());
        Integer sourceLevel = parseLevel(source.getLevel());

        Map<Integer, List<ProblemClassificationEntity>> byProblem =
                classificationRepository.findByTagIn(sourceTags).stream()
                        .filter(c -> !c.getProblemId().equals(problemId))
                        .filter(c -> !profile.solvedProblemIds().contains(c.getProblemId()))
                        .collect(Collectors.groupingBy(ProblemClassificationEntity::getProblemId));

        if (byProblem.isEmpty()) {
            log.info("[RECOMMEND] problemId={} tags={} 후보 없음", problemId, sourceTags);
            return RecommendationResponse.builder()
                    .sourceProblemId(problemId)
                    .sourceTags(new ArrayList<>(sourceTags))
                    .recommendations(List.of())
                    .build();
        }

        Map<Integer, ProblemEntity> candidateProblems = problemMetaRepository
                .findAllById(byProblem.keySet()).stream()
                .collect(Collectors.toMap(ProblemEntity::getProblemId, p -> p));

        int max = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
        List<RecommendedProblem> recommendations = byProblem.entrySet().stream()
                .map(e -> toCandidate(e.getKey(), e.getValue(), candidateProblems.get(e.getKey()),
                        sourceTagSub, sourceLevel, profile))
                .filter(c -> c != null)
                .sorted(Comparator
                        .comparingInt(Candidate::score).reversed()
                        .thenComparing(c -> c.level == null ? Integer.MAX_VALUE : c.level)
                        .thenComparingInt(c -> c.problemId))
                .limit(max)
                .map(this::toDto)
                .collect(Collectors.toList());

        log.info("[RECOMMEND] problemId={} tags={} candidates={} returned={} personalized={}",
                problemId, sourceTags, byProblem.size(), recommendations.size(), profile.hasPersonalization());

        return RecommendationResponse.builder()
                .sourceProblemId(problemId)
                .sourceTags(new ArrayList<>(sourceTags))
                .recommendations(recommendations)
                .build();
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommendByTags(List<String> tags, List<Integer> excludeProblemIds, Integer limit) {
        Set<String> sourceTags = resolveTags(tags);
        if (sourceTags.isEmpty()) {
            return RecommendationResponse.builder()
                    .sourceTags(List.of())
                    .recommendations(List.of())
                    .build();
        }

        Set<Integer> excluded = excludeProblemIds == null
                ? Set.of()
                : excludeProblemIds.stream().collect(Collectors.toSet());

        Map<Integer, List<ProblemClassificationEntity>> byProblem =
                classificationRepository.findByTagIn(sourceTags).stream()
                        .filter(c -> !excluded.contains(c.getProblemId()))
                        .collect(Collectors.groupingBy(ProblemClassificationEntity::getProblemId));

        if (byProblem.isEmpty()) {
            log.info("[RECOMMEND_BY_TAGS] tags={} 후보 없음", sourceTags);
            return RecommendationResponse.builder()
                    .sourceTags(new ArrayList<>(sourceTags))
                    .recommendations(List.of())
                    .build();
        }

        Map<Integer, ProblemEntity> candidateProblems = problemMetaRepository
                .findAllById(byProblem.keySet()).stream()
                .collect(Collectors.toMap(ProblemEntity::getProblemId, p -> p));

        int max = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
        List<RecommendedProblem> recommendations = byProblem.entrySet().stream()
                .map(e -> toCandidate(e.getKey(), e.getValue(), candidateProblems.get(e.getKey()),
                        Set.of(), null, UserRecommendationProfile.empty()))
                .filter(c -> c != null)
                .sorted(Comparator
                        .comparingInt(Candidate::score).reversed()
                        .thenComparing(c -> c.level == null ? Integer.MAX_VALUE : c.level)
                        .thenComparingInt(c -> c.problemId))
                .limit(max)
                .map(this::toDto)
                .collect(Collectors.toList());

        log.info("[RECOMMEND_BY_TAGS] tags={} candidates={} returned={}",
                sourceTags, byProblem.size(), recommendations.size());

        return RecommendationResponse.builder()
                .sourceTags(new ArrayList<>(sourceTags))
                .recommendations(recommendations)
                .build();
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommendUnderPracticed(
            Map<String, Long> practicedTagCounts,
            List<Integer> excludeProblemIds,
            Integer limit
    ) {
        Set<Integer> excluded = excludeProblemIds == null
                ? Set.of()
                : excludeProblemIds.stream().collect(Collectors.toSet());
        Map<String, Long> practiceCounts = practicedTagCounts == null ? Map.of() : practicedTagCounts;

        List<ProblemClassificationEntity> availableRows = classificationRepository.findAll().stream()
                .filter(c -> c.getTag() != null && !c.getTag().isBlank())
                .filter(c -> !excluded.contains(c.getProblemId()))
                .collect(Collectors.toList());

        if (availableRows.isEmpty()) {
            log.info("[RECOMMEND_UNDER_PRACTICED] 추천 가능한 후보 없음");
            return RecommendationResponse.builder()
                    .sourceTags(List.of())
                    .recommendations(List.of())
                    .build();
        }

        Set<String> targetTags = availableRows.stream()
                .map(ProblemClassificationEntity::getTag)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .sorted(Comparator
                        .comparingLong((String tag) -> practiceCounts.getOrDefault(tag, 0L))
                        .thenComparing(tag -> tag))
                .limit(Math.max(limitOrDefault(limit) * 2L, DEFAULT_LIMIT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Integer, List<ProblemClassificationEntity>> byProblem = availableRows.stream()
                .filter(c -> targetTags.contains(c.getTag()))
                .collect(Collectors.groupingBy(ProblemClassificationEntity::getProblemId));

        Map<Integer, ProblemEntity> candidateProblems = problemMetaRepository
                .findAllById(byProblem.keySet()).stream()
                .collect(Collectors.toMap(ProblemEntity::getProblemId, p -> p));

        int max = limitOrDefault(limit);
        List<RecommendedProblem> recommendations = byProblem.entrySet().stream()
                .map(e -> toDiversityCandidate(e.getKey(), e.getValue(), candidateProblems.get(e.getKey()), practiceCounts))
                .filter(c -> c != null)
                .sorted(Comparator
                        .comparingLong((Candidate c) -> c.practicedCount)
                        .thenComparing(c -> c.level == null ? Integer.MAX_VALUE : c.level)
                        .thenComparingInt(c -> c.problemId))
                .limit(max)
                .map(this::toDiversityDto)
                .collect(Collectors.toList());

        log.info("[RECOMMEND_UNDER_PRACTICED] targetTags={} candidates={} returned={}",
                targetTags, byProblem.size(), recommendations.size());

        return RecommendationResponse.builder()
                .sourceTags(new ArrayList<>(targetTags))
                .recommendations(recommendations)
                .build();
    }

    private Set<String> resolveSourceTags(ProblemEntity source, List<String> tagOverride) {
        if (tagOverride != null && !tagOverride.isEmpty()) {
            return resolveTags(tagOverride);
        }
        return source.getClassifications().stream()
                .map(ProblemClassificationEntity::getTag)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> resolveTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int limitOrDefault(Integer limit) {
        return (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
    }

    private Candidate toCandidate(int candidateId,
                                  List<ProblemClassificationEntity> sharedRows,
                                  ProblemEntity problem,
                                  Set<String> sourceTagSub,
                                  Integer sourceLevel,
                                  UserRecommendationProfile profile) {
        if (problem == null) {
            return null;
        }
        Set<String> sharedTags = sharedRows.stream()
                .map(ProblemClassificationEntity::getTag)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String matchedSubcategory = sharedRows.stream()
                .filter(c -> c.getSubcategory() != null)
                .filter(c -> sourceTagSub.contains(tagSubKey(c.getTag(), c.getSubcategory())))
                .map(ProblemClassificationEntity::getSubcategory)
                .findFirst()
                .orElse(null);

        String primaryTag = sharedRows.stream()
                .filter(c -> matchedSubcategory != null && matchedSubcategory.equals(c.getSubcategory()))
                .map(ProblemClassificationEntity::getTag)
                .findFirst()
                .orElse(sharedTags.iterator().next());

        Integer level = parseLevel(problem.getLevel());

        int score = sharedTags.size() * 10;
        if (matchedSubcategory != null) {
            score += 15;
        }
        score += levelScore(level, sourceLevel);
        score += weaknessTagBonus(sharedTags, profile.weakTagCounts());

        Candidate candidate = new Candidate();
        candidate.problemId = candidateId;
        candidate.problem = problem;
        candidate.primaryTag = primaryTag;
        candidate.matchedSubcategory = matchedSubcategory;
        candidate.level = level;
        candidate.sourceLevel = sourceLevel;
        candidate.score = score;
        candidate.weakTagHit = profile.weakTagCounts() != null
                && profile.weakTagCounts().getOrDefault(primaryTag, 0L) > 0;
        return candidate;
    }

    private int weaknessTagBonus(Set<String> sharedTags, Map<String, Long> weakTagCounts) {
        if (weakTagCounts == null || weakTagCounts.isEmpty()) {
            return 0;
        }
        int bonus = 0;
        for (String tag : sharedTags) {
            long count = weakTagCounts.getOrDefault(tag, 0L);
            if (count > 0) {
                bonus += Math.min((int) count * 2, WEAK_TAG_SCORE_CAP);
            }
        }
        return bonus;
    }

    private Candidate toDiversityCandidate(int candidateId,
                                           List<ProblemClassificationEntity> rows,
                                           ProblemEntity problem,
                                           Map<String, Long> practicedTagCounts) {
        if (problem == null || rows.isEmpty()) {
            return null;
        }

        String primaryTag = rows.stream()
                .map(ProblemClassificationEntity::getTag)
                .filter(tag -> tag != null && !tag.isBlank())
                .min(Comparator
                        .comparingLong((String tag) -> practicedTagCounts.getOrDefault(tag, 0L))
                        .thenComparing(tag -> tag))
                .orElse(null);
        if (primaryTag == null) {
            return null;
        }

        Candidate candidate = new Candidate();
        candidate.problemId = candidateId;
        candidate.problem = problem;
        candidate.primaryTag = primaryTag;
        candidate.level = parseLevel(problem.getLevel());
        candidate.practicedCount = practicedTagCounts.getOrDefault(primaryTag, 0L);
        return candidate;
    }

    private int levelScore(Integer candidateLevel, Integer sourceLevel) {
        if (candidateLevel == null || sourceLevel == null) {
            return 2;
        }
        if (candidateLevel < sourceLevel) {
            return 8;
        }
        if (candidateLevel.equals(sourceLevel)) {
            return 4;
        }
        return 0;
    }

    private RecommendedProblem toDto(Candidate c) {
        String type = recommendationType(c);
        return RecommendedProblem.builder()
                .problemId(c.problemId)
                .title(c.problem.getTitle())
                .level(c.problem.getLevel())
                .url(problemUrl(c.problem))
                .tag(c.primaryTag)
                .subcategory(c.matchedSubcategory)
                .recommendationType(type)
                .reason(reason(type, c))
                .build();
    }

    private RecommendedProblem toDiversityDto(Candidate c) {
        return RecommendedProblem.builder()
                .problemId(c.problemId)
                .title(c.problem.getTitle())
                .level(c.problem.getLevel())
                .url(problemUrl(c.problem))
                .tag(c.primaryTag)
                .recommendationType("UNDER_PRACTICED_ALGORITHM")
                .reason("다양한 유형을 넓히기 위해 추천했어요.")
                .build();
    }

    private String problemUrl(ProblemEntity problem) {
        if (problem.getUrl() != null && !problem.getUrl().isBlank()) {
            return problem.getUrl();
        }
        return "https://school.programmers.co.kr/learn/courses/30/lessons/"
                + problem.getProblemId()
                + "?language=java";
    }

    private String recommendationType(Candidate c) {
        if (c.matchedSubcategory != null) {
            return "SIMILAR_PATTERN";
        }
        if (c.level != null && c.sourceLevel != null && c.level < c.sourceLevel) {
            return "BASIC_CONCEPT_REVIEW";
        }
        return "SAME_ALGORITHM_PRACTICE";
    }

    private String reason(String type, Candidate c) {
        switch (type) {
            case "SIMILAR_PATTERN":
                return c.weakTagHit
                        ? "최근 자주 막혔던 풀이 흐름을 다시 연습하기 좋아요."
                        : "비슷한 사고 과정을 한 번 더 연습하기 좋아요.";
            case "BASIC_CONCEPT_REVIEW":
                return "조금 더 부담 없는 난이도에서 비슷한 사고 과정을 복습할 수 있는 문제예요.";
            default:
                return c.weakTagHit
                        ? "최근 자주 막혔던 풀이 흐름을 다시 연습하기 좋아요."
                        : "최근 막혔던 접근 방식을 한 번 더 연습해볼 수 있는 문제예요.";
        }
    }

    private static String tagSubKey(String tag, String subcategory) {
        return tag + "|" + subcategory;
    }

    private static Integer parseLevel(String level) {
        if (level == null) {
            return null;
        }
        String digits = level.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        return Integer.valueOf(digits);
    }

    private static String displayTag(String tag) {
        if (tag == null) {
            return "";
        }
        switch (tag) {
            case "bfs":
                return "BFS";
            case "dfs":
                return "DFS";
            case "dp":
                return "DP";
            case "bruteforcing":
                return "완전탐색";
            case "greedy":
                return "그리디";
            case "binary_search":
                return "이분 탐색";
            case "graph_traversal":
                return "그래프 탐색";
            case "hash_set":
                return "해시";
            case "priority_queue":
                return "우선순위 큐";
            case "queue_deque":
                return "큐/덱";
            case "sliding_window":
                return "슬라이딩 윈도우";
            case "two_pointer":
                return "투 포인터";
            case "prefix_sum":
                return "누적합";
            case "backtracking":
                return "백트래킹";
            case "simulation":
                return "시뮬레이션";
            case "sorting":
                return "정렬";
            case "string":
                return "문자열";
            case "trees":
                return "트리";
            case "stack":
                return "스택";
            case "array":
                return "배열";
            case "math":
                return "수학";
            default:
                return tag;
        }
    }

    private static final class Candidate {
        private int problemId;
        private ProblemEntity problem;
        private String primaryTag;
        private String matchedSubcategory;
        private Integer level;
        private Integer sourceLevel;
        private int score;
        private boolean weakTagHit;
        private long practicedCount;

        private int score() {
            return score;
        }
    }
}
