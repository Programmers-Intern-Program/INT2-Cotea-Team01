package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ProblemContextSelectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProblemContextSelector selector;
    private JsonNode policy;

    @BeforeEach
    void setUp() throws IOException {
        selector = new ProblemContextSelector(new QuestionResolver(), objectMapper);
        policy = objectMapper.readTree(new ClassPathResource("config/prompt-policy.json").getInputStream());
    }

    @Test
    void filtersCommonMistakesByWrongAnswerSubmissionResult() throws IOException {
        JsonNode problem = sampleProblem();
        HintRequest request = wrongAnswerReasonRequest("WRONG_ANSWER", "why_wrong");

        ObjectNode context = selector.select(problem, policy, request, 2);
        JsonNode mistakes = context.path("fields").path("wrongAnswerDiagnosis.commonMistakes");

        assertThat(mistakes.isArray()).isTrue();
        assertThat(mistakes).hasSize(2);
        assertThat(mistakes.findValuesAsText("symptom")).containsOnly("오답");
    }

    @Test
    void filtersCommonMistakesByRuntimeErrorSubmissionResult() throws IOException {
        JsonNode problem = sampleProblem();
        HintRequest request = wrongAnswerReasonRequest("RUNTIME_ERROR", "why_runtime_error");

        ObjectNode context = selector.select(problem, policy, request, 2);
        JsonNode mistakes = context.path("fields").path("wrongAnswerDiagnosis.commonMistakes");

        assertThat(mistakes.isArray()).isTrue();
        assertThat(mistakes).hasSize(1);
        assertThat(mistakes.get(0).path("symptom").asText()).isEqualTo("런타임에러");
    }

    @Test
    void filtersCommonMistakesByTimeLimitSubmissionResult() throws IOException {
        JsonNode problem = sampleProblem();
        HintRequest request = wrongAnswerReasonRequest("TIME_LIMIT_EXCEEDED", "why_tle");

        ObjectNode context = selector.select(problem, policy, request, 2);
        JsonNode mistakes = context.path("fields").path("wrongAnswerDiagnosis.commonMistakes");

        assertThat(mistakes.isArray()).isTrue();
        assertThat(mistakes).hasSize(1);
        assertThat(mistakes.get(0).path("symptom").asText()).isEqualTo("시간초과");
    }

    @Test
    void excludesDiagnosisFieldsBeforeUserAsksReason() throws IOException {
        JsonNode problem = sampleProblem();
        HintRequest request = wrongAnswerReasonRequest("WRONG_ANSWER", "wrong_result_only");

        ObjectNode context = selector.select(problem, policy, request, 2);

        assertThat(context.path("fields").isEmpty()).isTrue();
    }

    @Test
    void extractsSubcategoriesWhenPresent() throws IOException {
        JsonNode problem = objectMapper.readTree("""
                {
                  "classification": {
                    "primary": [
                      { "tag": "dp", "subcategory": "dp_knapsack" },
                      { "tag": "math" }
                    ]
                  }
                }
                """);

        assertThat(selector.extractSubcategories(problem)).containsExactly("dp_knapsack");
    }

    @Test
    void extractsEmptySubcategoriesWhenNoneSpecified() throws IOException {
        JsonNode problem = sampleProblem();

        assertThat(selector.extractSubcategories(problem)).isEmpty();
    }

    private HintRequest wrongAnswerReasonRequest(String submissionResult, String buttonId) {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("WRONG_ANSWER");
        request.setHintLevel(2);
        request.setQuestionType("BUTTON");
        request.setButtonId(buttonId);
        request.setSubmissionResult(submissionResult);
        return request;
    }

    private JsonNode sampleProblem() throws IOException {
        String json = """
                {
                  "problemId": 1829,
                  "source": { "title": "테스트", "level": "Lv2" },
                  "classification": { "primary": [{ "tag": "dfs" }] },
                  "approach": {
                    "recommendedApproach": "탐색",
                    "expectedTimeComplexity": "O(n)",
                    "expectedSpaceComplexity": "O(n)"
                  },
                  "wrongAnswerDiagnosis": {
                    "commonMistakes": [
                      { "symptom": "오답", "likelyCause": "원인1", "directionHint": "힌트1" },
                      { "symptom": "오답", "likelyCause": "원인2", "directionHint": "힌트2" },
                      { "symptom": "런타임에러", "likelyCause": "원인3", "directionHint": "힌트3" },
                      { "symptom": "시간초과", "likelyCause": "원인4", "directionHint": "힌트4" }
                    ],
                    "fatalApproachSignals": ["잘못된 접근"]
                  }
                }
                """;
        return objectMapper.readTree(json);
    }
}
