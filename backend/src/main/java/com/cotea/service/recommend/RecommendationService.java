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

    private Set<String> resolveSourceTags(ProblemEntity source, List<String> tagOverride) {
        if (tagOverride != null && !tagOverride.isEmpty()) {
            return tagOverride.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return source.getClassifications().stream()
                .map(ProblemClassificationEntity::getTag)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
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
                .url(c.problem.getUrl())
                .tag(c.primaryTag)
                .subcategory(c.matchedSubcategory)
                .recommendationType(type)
                .reason(reason(type, c))
                .build();
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
        String tag = displayTag(c.primaryTag);
        String base;
        switch (type) {
            case "SIMILAR_PATTERN":
                base = String.format(
                        "지금 문제와 같은 %s 유형(%s)이라, 같은 접근 방식을 한 번 더 연습하기 좋아요.",
                        tag, c.matchedSubcategory);
                break;
            case "BASIC_CONCEPT_REVIEW":
                base = String.format(
                        "%s 개념을 더 쉬운 난이도(%s)에서 연습할 수 있는 문제예요. 부담 없이 복습한 뒤 원래 문제로 돌아오세요.",
                        tag, c.problem.getLevel());
                break;
            default:
                base = String.format(
                        "같은 %s 유형 문제예요. 접근 방식을 반복해서 익혀볼 수 있어요.",
                        tag);
                break;
        }
        if (c.weakTagHit) {
            return base + " 최근 이 유형에서 자주 막혀서 연습용으로 추천했어요.";
        }
        return base;
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

        private int score() {
            return score;
        }
    }
}
