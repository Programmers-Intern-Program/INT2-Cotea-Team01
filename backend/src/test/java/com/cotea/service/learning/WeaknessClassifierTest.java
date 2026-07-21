package com.cotea.service.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
import org.junit.jupiter.api.Test;

class WeaknessClassifierTest {

    private final WeaknessClassifier classifier = new WeaknessClassifier();

    @Test
    void classifiesImplementationOrderFromLevelThreeButton() {
        HintRequest request = request("hint_level_3");

        WeaknessClassification result = classifier.classify(request, "구현 순서가 잘 안 잡혀요", "RELATED");

        assertThat(result.weaknessType()).isEqualTo(WeaknessType.IMPLEMENTATION);
        assertThat(result.detectedIntent()).isEqualTo(DetectedIntent.IMPLEMENTATION_ORDER);
    }

    @Test
    void classifiesTimeComplexityFromWrongAnswerButton() {
        HintRequest request = request("why_tle");

        WeaknessClassification result = classifier.classify(request, "왜 시간초과가 났는지 알려주세요", "RELATED");

        assertThat(result.weaknessType()).isEqualTo(WeaknessType.COMPLEXITY);
        assertThat(result.detectedIntent()).isEqualTo(DetectedIntent.TIME_COMPLEXITY);
    }

    @Test
    void classifiesVisitedHandlingFromFreeText() {
        HintRequest request = request(null);

        WeaknessClassification result = classifier.classify(request, "visited 방문 처리를 어떻게 해야 하나요?", "RELATED");

        assertThat(result.weaknessType()).isEqualTo(WeaknessType.IMPLEMENTATION);
        assertThat(result.detectedIntent()).isEqualTo(DetectedIntent.VISITED_HANDLING);
    }

    @Test
    void classifiesOffTopicRouteSeparately() {
        HintRequest request = request(null);

        WeaknessClassification result = classifier.classify(request, "자바 문법이 궁금해요", "OFF_TOPIC");

        assertThat(result.weaknessType()).isEqualTo(WeaknessType.ETC);
        assertThat(result.detectedIntent()).isEqualTo(DetectedIntent.OFF_TOPIC);
    }

    private HintRequest request(String buttonId) {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("BEFORE_SOLVE");
        request.setQuestionType(buttonId == null ? "FREE_TEXT" : "BUTTON");
        request.setButtonId(buttonId);
        return request;
    }
}
