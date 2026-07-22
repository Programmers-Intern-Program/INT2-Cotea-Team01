package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
import com.cotea.service.hint.OffTopicQuestionClassifier.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OffTopicQuestionClassifierTest {

    private OffTopicQuestionClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new OffTopicQuestionClassifier();
    }

    @Test
    void buttonQuestionsAreRelated() {
        HintRequest request = freeTextRequest();
        request.setQuestionType("BUTTON");
        request.setButtonId("hint_level_2");

        assertThat(classifier.classify(request, "어떤 알고리즘으로 접근해야 할지 모르겠어요"))
                .isEqualTo(Verdict.RELATED);
    }

    @Test
    void problemSolvingFreeTextIsRelated() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.classify(request, "방문 처리를 어떻게 해야 할지 모르겠어요"))
                .isEqualTo(Verdict.RELATED);
        assertThat(classifier.classify(request, "왜 틀렸는지 알려주세요"))
                .isEqualTo(Verdict.RELATED);
    }

    @Test
    void generalCsWithoutProblemSignalsIsAmbiguous() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.classify(request, "자바 HashMap이 뭐야?"))
                .isEqualTo(Verdict.AMBIGUOUS);
    }

    @Test
    void shortProblemContextQuestionIsAmbiguous() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.classify(request, "영역이 뭐예요?"))
                .isEqualTo(Verdict.AMBIGUOUS);
        assertThat(classifier.classify(request, "이 그림이 이해가 안 돼요"))
                .isEqualTo(Verdict.AMBIGUOUS);
    }

    @Test
    void unrelatedChatIsOffTopic() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.classify(request, "오늘 점심 뭐 먹지?"))
                .isEqualTo(Verdict.OFF_TOPIC);
    }

    @Test
    void stockKeywordWithProblemTitleIsAmbiguous() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.classify(
                request,
                "주식 가격 계산 결과가 맞게 나오는지 한번만 봐주실 수 있나요?",
                "주식가격"))
                .isEqualTo(Verdict.AMBIGUOUS);
    }

    @Test
    void stockKeywordWithUserCodeIsAmbiguous() {
        HintRequest request = freeTextRequest();
        request.setUserCode("class Solution { public int[] solution(int[] prices) { return prices; } }");

        assertThat(classifier.classify(
                request,
                "주식 가격 계산 결과가 맞게 나오는지 한번만 봐주실 수 있나요?",
                "카카오프렌즈 컬러링북"))
                .isEqualTo(Verdict.AMBIGUOUS);
    }

    @Test
    void stockKeywordWithoutContextIsOffTopic() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.classify(request, "오늘 주식 어때?", "카카오프렌즈 컬러링북"))
                .isEqualTo(Verdict.OFF_TOPIC);
    }

    @Test
    void wordSharingPrefixWithShortRelatedTermIsAmbiguous() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.classify(request, "정육면체 큐브는 어떻게 조립하나요"))
                .isEqualTo(Verdict.AMBIGUOUS);
    }

    @Test
    void shortRelatedTermFollowedByParticleIsRelated() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.classify(request, "큐를 어떻게 써야 하나요"))
                .isEqualTo(Verdict.RELATED);
    }

    private HintRequest freeTextRequest() {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("BEFORE_SOLVE");
        request.setQuestionType("FREE_TEXT");
        request.setQuestionText("질문");
        return request;
    }
}
