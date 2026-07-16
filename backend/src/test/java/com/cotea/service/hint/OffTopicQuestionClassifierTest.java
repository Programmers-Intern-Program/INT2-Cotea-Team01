package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
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

        assertThat(classifier.isOffTopic(request, "어떤 알고리즘으로 접근해야 할지 모르겠어요")).isFalse();
    }

    @Test
    void problemSolvingFreeTextIsRelated() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.isOffTopic(request, "방문 처리를 어떻게 해야 할지 모르겠어요")).isFalse();
        assertThat(classifier.isOffTopic(request, "왜 틀렸는지 알려주세요")).isFalse();
    }

    @Test
    void generalCsWithoutProblemSignalsIsOffTopic() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.isOffTopic(request, "자바 HashMap이 뭐야?")).isTrue();
    }

    @Test
    void unrelatedChatIsOffTopic() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.isOffTopic(request, "오늘 점심 뭐 먹지?")).isTrue();
    }

    @Test
    void wordSharingPrefixWithShortRelatedTermIsOffTopic() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.isOffTopic(request, "정육면체 큐브는 어떻게 조립하나요")).isTrue();
    }

    @Test
    void shortRelatedTermFollowedByParticleIsRelated() {
        HintRequest request = freeTextRequest();

        assertThat(classifier.isOffTopic(request, "큐를 어떻게 써야 하나요")).isFalse();
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
