package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HintAnswerGuardrailTest {

    private HintAnswerGuardrail guardrail;

    @BeforeEach
    void setUp() {
        guardrail = new HintAnswerGuardrail(new QuestionResolver());
    }

    @Test
    void requestsReviewWhenLevelOneAnswerRevealsAlgorithmName() {
        HintRequest request = beforeSolveRequest(1, "hint_level_1");

        GuardrailResult result = guardrail.inspect("이 문제는 BFS로 보면 좋아요.", request, 1);

        assertThat(result.needsReview()).isTrue();
        assertThat(result.riskSignals()).contains("Lv1 금지어 포함: BFS");
    }

    @Test
    void requestsReviewWhenWrongAnswerBeforeReasonContainsDiagnosis() {
        HintRequest request = wrongAnswerRequest("wrong_result_only");

        GuardrailResult result = guardrail.inspect("오답이에요. 원인은 경계 조건 때문일 수 있어요.", request, 2);

        assertThat(result.needsReview()).isTrue();
        assertThat(result.riskSignals()).anyMatch(signal -> signal.contains("이유 질문 전 진단 표현 포함"));
    }

    @Test
    void passesWhenWrongAnswerUserAsksReason() {
        HintRequest request = wrongAnswerRequest("why_wrong");

        GuardrailResult result = guardrail.inspect("원인은 경계 조건일 가능성이 있어요.", request, 2);

        assertThat(result.needsReview()).isFalse();
    }

    @Test
    void requestsReviewWhenLowerLevelAnswerContainsCodeBlock() {
        HintRequest request = beforeSolveRequest(3, "hint_level_3");

        GuardrailResult result = guardrail.inspect("""
                ```java
                class Solution {}
                ```
                """, request, 3);

        assertThat(result.needsReview()).isTrue();
        assertThat(result.riskSignals()).contains("Lv3 이하 코드블록 포함");
    }

    @Test
    void doesNotFlagUnrelatedWordSharingPrefixWithForbiddenTerm() {
        HintRequest request = beforeSolveRequest(1, "hint_level_1");

        GuardrailResult result = guardrail.inspect("이 문제는 큐브 모양으로 생각해보면 도움이 됩니다.", request, 1);

        assertThat(result.needsReview()).isFalse();
    }

    @Test
    void flagsForbiddenTermFollowedByParticleWithoutSpace() {
        HintRequest request = beforeSolveRequest(1, "hint_level_1");

        GuardrailResult result = guardrail.inspect("이 문제는 큐를 사용하면 됩니다.", request, 1);

        assertThat(result.needsReview()).isTrue();
        assertThat(result.riskSignals()).contains("Lv1 금지어 포함: 큐");
    }

    private HintRequest beforeSolveRequest(int hintLevel, String buttonId) {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("BEFORE_SOLVE");
        request.setHintLevel(hintLevel);
        request.setQuestionType("BUTTON");
        request.setButtonId(buttonId);
        return request;
    }

    private HintRequest wrongAnswerRequest(String buttonId) {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("WRONG_ANSWER");
        request.setHintLevel(2);
        request.setQuestionType("BUTTON");
        request.setButtonId(buttonId);
        request.setSubmissionResult("WRONG_ANSWER");
        return request;
    }
}
