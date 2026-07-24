package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConceptGapClassifierTest {

    private ConceptGapClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ConceptGapClassifier();
    }

    @Test
    void 개념_정체_질문은_개념부재로_본다() {
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "BFS가 뭐예요?")).isTrue();
        assertThat(classifier.isConceptGap(freeText("BEFORE_SOLVE"), "DP가 뭔지 모르겠어요")).isTrue();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "이게 무슨 알고리즘이죠?")).isTrue();
    }

    @Test
    void 전면적_부재_표현은_개념부재로_본다() {
        assertThat(classifier.isConceptGap(freeText("BEFORE_SOLVE"), "아예 감이 안 잡혀요")).isTrue();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "이 문제 처음 봐서 하나도 모르겠어요")).isTrue();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "어디서부터 손대야 할지 모르겠어요")).isTrue();
    }

    @Test
    void 개념_단어와_부재_표현_조합도_잡는다() {
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "이 알고리즘 개념이 없어요")).isTrue();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "개념 설명 좀 해주세요")).isTrue();
    }

    @Test
    void 개념_설명_요청은_개념부재로_본다() {
        assertThat(classifier.isConceptGap(freeText("BEFORE_SOLVE"), "BFS에 대해 알려주세요")).isTrue();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "그리디 설명해줘")).isTrue();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "DP 가르쳐주세요")).isTrue();
    }

    @Test
    void 구현_디테일_질문은_개념부재가_아니다() {
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "방문 처리를 어떻게 해야 할지 모르겠어요")).isFalse();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "인덱스 경계를 어떻게 잡죠?")).isFalse();
    }

    @Test
    void 방향_검증이나_개선점_요청은_개념부재가_아니다() {
        assertThat(classifier.isConceptGap(
                freeText("SOLVING"), "이 BFS 탐색 방향이 맞나요? 개선점만 짧게 알려주세요")).isFalse();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "개선점만 알려주세요")).isFalse();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "이 접근 방향이 맞을까요?")).isFalse();
    }

    @Test
    void 자기_코드_설명_요청은_개념부재가_아니다() {
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "이 코드 설명해주세요")).isFalse();
        assertThat(classifier.isConceptGap(freeText("WRONG_ANSWER"), "제 코드 어디가 문제인지 알려주세요")).isFalse();
    }

    @Test
    void 오답_디버깅_질문은_개념부재가_아니다() {
        HintRequest wrong = freeText("WRONG_ANSWER");
        wrong.setSubmissionResult("WRONG_ANSWER");
        assertThat(classifier.isConceptGap(wrong, "왜 틀렸는지 모르겠어요")).isFalse();
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "왜 시간초과가 나는지 모르겠어요")).isFalse();
    }

    @Test
    void 단순_모르겠어요는_개념부재가_아니다() {
        assertThat(classifier.isConceptGap(freeText("SOLVING"), "모르겠어요")).isFalse();
        assertThat(classifier.isConceptGap(freeText("BEFORE_SOLVE"), "잘 모르겠어요 도와주세요")).isFalse();
    }

    @Test
    void 버튼_질문과_AFTER_SOLVE는_판정하지_않는다() {
        HintRequest button = freeText("BEFORE_SOLVE");
        button.setQuestionType("BUTTON");
        button.setButtonId("hint_level_1");
        assertThat(classifier.isConceptGap(button, "이 문제를 어떤 관점에서 바라봐야 할지 모르겠어요")).isFalse();

        assertThat(classifier.isConceptGap(freeText("AFTER_SOLVE"), "BFS가 뭐예요?")).isFalse();
    }

    private HintRequest freeText(String stage) {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage(stage);
        request.setQuestionType("FREE_TEXT");
        request.setQuestionText("질문");
        return request;
    }
}
