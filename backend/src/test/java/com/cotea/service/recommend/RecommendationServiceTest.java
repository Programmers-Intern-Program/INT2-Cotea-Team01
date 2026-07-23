package com.cotea.service.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cotea.controller.dto.RecommendationResponse;
import com.cotea.controller.dto.RecommendedProblem;
import com.cotea.exception.CoteaException;
import com.cotea.service.problem.ProblemClassificationRepository;
import com.cotea.service.problem.ProblemMetaRepository;
import com.cotea.service.problem.entity.ProblemClassificationEntity;
import com.cotea.service.problem.entity.ProblemEntity;
import com.cotea.service.auth.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private ProblemMetaRepository problemMetaRepository;

    @Mock
    private ProblemClassificationRepository classificationRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserWeaknessProvider userWeaknessProvider;

    @InjectMocks
    private RecommendationService recommendationService;

    private ProblemEntity source;

    @BeforeEach
    void setUp() {
        when(jwtTokenProvider.resolveUserId(any())).thenReturn(Optional.empty());
        source = problem(1829, "카카오프렌즈 컬러링북", "Lv2");
        addClassification(source, "bfs", null);
    }

    @Test
    void 같은_태그의_더_쉬운_문제가_먼저_추천된다() {
        when(problemMetaRepository.findById(1829)).thenReturn(Optional.of(source));
        when(classificationRepository.findByTagIn(any())).thenReturn(List.of(
                classification(100, "bfs", null),
                classification(200, "bfs", null)));
        ProblemEntity easier = problem(100, "쉬운 BFS", "Lv1");
        ProblemEntity harder = problem(200, "어려운 BFS", "Lv3");
        when(problemMetaRepository.findAllById(any())).thenReturn(List.of(easier, harder));

        RecommendationResponse response = recommendationService.recommend(1829, null, null);

        assertThat(response.getRecommendations()).hasSize(2);
        RecommendedProblem first = response.getRecommendations().get(0);
        assertThat(first.getProblemId()).isEqualTo(100);
        assertThat(first.getRecommendationType()).isEqualTo("BASIC_CONCEPT_REVIEW");
        assertThat(response.getRecommendations().get(1).getProblemId()).isEqualTo(200);
    }

    @Test
    void subcategory가_일치하면_더_쉬운_문제보다_우선한다() {
        ProblemEntity dpSource = problem(43105, "정수 삼각형", "Lv3");
        addClassification(dpSource, "dp", "dp_general");
        when(problemMetaRepository.findById(43105)).thenReturn(Optional.of(dpSource));
        when(classificationRepository.findByTagIn(any())).thenReturn(List.of(
                classification(42895, "dp", "dp_general"),
                classification(12913, "dp", null)));
        ProblemEntity subMatch = problem(42895, "N으로 표현", "Lv3");
        ProblemEntity easierPlain = problem(12913, "땅따먹기", "Lv2");
        when(problemMetaRepository.findAllById(any())).thenReturn(List.of(subMatch, easierPlain));

        RecommendationResponse response = recommendationService.recommend(43105, null, null);

        RecommendedProblem first = response.getRecommendations().get(0);
        assertThat(first.getProblemId()).isEqualTo(42895);
        assertThat(first.getRecommendationType()).isEqualTo("SIMILAR_PATTERN");
        assertThat(first.getSubcategory()).isEqualTo("dp_general");
    }

    @Test
    void limit로_추천_개수를_제한한다() {
        when(problemMetaRepository.findById(1829)).thenReturn(Optional.of(source));
        when(classificationRepository.findByTagIn(any())).thenReturn(List.of(
                classification(100, "bfs", null),
                classification(200, "bfs", null),
                classification(300, "bfs", null)));
        when(problemMetaRepository.findAllById(any())).thenReturn(List.of(
                problem(100, "BFS 1", "Lv1"),
                problem(200, "BFS 2", "Lv2"),
                problem(300, "BFS 3", "Lv3")));

        RecommendationResponse response = recommendationService.recommend(1829, null, 1);

        assertThat(response.getRecommendations()).hasSize(1);
    }

    @Test
    void 태그를_직접_지정하면_문제_분류_대신_사용한다() {
        ProblemEntity noTag = problem(999, "태그 없는 문제", "Lv2");
        when(problemMetaRepository.findById(999)).thenReturn(Optional.of(noTag));
        when(classificationRepository.findByTagIn(any())).thenReturn(List.of(
                classification(100, "greedy", null)));
        when(problemMetaRepository.findAllById(any())).thenReturn(List.of(
                problem(100, "그리디 문제", "Lv1")));

        RecommendationResponse response = recommendationService.recommend(999, List.of("greedy"), null);

        assertThat(response.getSourceTags()).containsExactly("greedy");
        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getProblemId()).isEqualTo(100);
    }

    @Test
    void 분류가_없으면_빈_추천을_반환한다() {
        ProblemEntity noTag = problem(999, "태그 없는 문제", "Lv2");
        when(problemMetaRepository.findById(999)).thenReturn(Optional.of(noTag));

        RecommendationResponse response = recommendationService.recommend(999, null, null);

        assertThat(response.getRecommendations()).isEmpty();
    }

    @Test
    void 존재하지_않는_문제는_예외를_던진다() {
        when(problemMetaRepository.findById(404404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recommendationService.recommend(404404, null, null))
                .isInstanceOf(CoteaException.class)
                .hasMessageContaining("404404");
    }

    @Test
    void 프로필에_푼_문제는_추천에서_제외한다() {
        when(jwtTokenProvider.resolveUserId("Bearer token")).thenReturn(Optional.of(1L));
        when(userWeaknessProvider.getProfile(1L)).thenReturn(
                new UserRecommendationProfile(Set.of(100), Map.of()));
        when(problemMetaRepository.findById(1829)).thenReturn(Optional.of(source));
        when(classificationRepository.findByTagIn(any())).thenReturn(List.of(
                classification(100, "bfs", null),
                classification(200, "bfs", null)));
        when(problemMetaRepository.findAllById(any())).thenReturn(List.of(
                problem(100, "이미 푼 BFS", "Lv1"),
                problem(200, "새 BFS", "Lv1")));

        RecommendationResponse response = recommendationService.recommend(1829, null, null, "Bearer token");

        assertThat(response.getRecommendations()).hasSize(1);
        assertThat(response.getRecommendations().get(0).getProblemId()).isEqualTo(200);
    }

    @Test
    void 약점_태그가_있으면_해당_태그_후보에_가산점을_준다() {
        addClassification(source, "dfs", null);
        when(jwtTokenProvider.resolveUserId("Bearer token")).thenReturn(Optional.of(1L));
        when(userWeaknessProvider.getProfile(1L)).thenReturn(
                new UserRecommendationProfile(Set.of(), Map.of("bfs", 5L)));
        when(problemMetaRepository.findById(1829)).thenReturn(Optional.of(source));
        when(classificationRepository.findByTagIn(any())).thenReturn(List.of(
                classification(100, "bfs", null),
                classification(200, "dfs", null)));
        when(problemMetaRepository.findAllById(any())).thenReturn(List.of(
                problem(100, "BFS 연습", "Lv2"),
                problem(200, "DFS 연습", "Lv2")));

        RecommendationResponse response = recommendationService.recommend(1829, null, null, "Bearer token");

        assertThat(response.getRecommendations()).hasSize(2);
        assertThat(response.getRecommendations().get(0).getProblemId()).isEqualTo(100);
        assertThat(response.getRecommendations().get(0).getReason())
                .contains("최근 자주 막혔던 풀이 흐름");
        assertThat(response.getRecommendations().get(1).getReason())
                .doesNotContain("BFS", "DFS", "이 유형");
    }

    private static ProblemEntity problem(int id, String title, String level) {
        ProblemEntity entity = new ProblemEntity();
        ReflectionTestUtils.setField(entity, "problemId", id);
        ReflectionTestUtils.setField(entity, "title", title);
        ReflectionTestUtils.setField(entity, "level", level);
        ReflectionTestUtils.setField(entity, "url",
                "https://school.programmers.co.kr/learn/courses/30/lessons/" + id);
        return entity;
    }

    private static void addClassification(ProblemEntity problem, String tag, String subcategory) {
        problem.getClassifications().add(classification(problem.getProblemId(), tag, subcategory));
    }

    private static ProblemClassificationEntity classification(int problemId, String tag, String subcategory) {
        ProblemClassificationEntity entity = new ProblemClassificationEntity();
        ReflectionTestUtils.setField(entity, "problemId", problemId);
        ReflectionTestUtils.setField(entity, "tag", tag);
        ReflectionTestUtils.setField(entity, "subcategory", subcategory);
        return entity;
    }
}
