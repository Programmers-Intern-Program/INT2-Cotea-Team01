package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
import com.cotea.service.hint.ConceptGapLlmSignal.Parsed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConceptGapLlmSignalTest {

    private ConceptGapLlmSignal signal;

    @BeforeEach
    void setUp() {
        signal = new ConceptGapLlmSignal();
    }

    @Test
    void YES_마커는_개념부재로_파싱하고_사용자_텍스트에서_제거한다() {
        String raw = "BFS는 너비 우선 탐색이에요.\n어떻게 접근할지 생각해볼까요?\n[[CONCEPT_GAP: YES]]";
        Parsed parsed = signal.parse(raw);

        assertThat(parsed.conceptGap()).contains(true);
        assertThat(parsed.text()).doesNotContain("CONCEPT_GAP");
        assertThat(parsed.text()).endsWith("생각해볼까요?");
    }

    @Test
    void NO_마커는_개념부재_아님으로_파싱한다() {
        Parsed parsed = signal.parse("코드의 이 부분을 다시 보세요.\n[[CONCEPT_GAP: NO]]");

        assertThat(parsed.conceptGap()).contains(false);
        assertThat(parsed.text()).doesNotContain("CONCEPT_GAP");
    }

    @Test
    void 마커가_없으면_판정은_비어있고_텍스트는_유지된다() {
        Parsed parsed = signal.parse("이 문제는 그래프 탐색으로 접근해요.");

        assertThat(parsed.conceptGap()).isEmpty();
        assertThat(parsed.text()).isEqualTo("이 문제는 그래프 탐색으로 접근해요.");
    }

    @Test
    void 마커가_중간에_섞여도_모두_제거한다() {
        Parsed parsed = signal.parse("앞부분 [[CONCEPT_GAP: NO]] 뒷부분\n[[CONCEPT_GAP: YES]]");

        assertThat(parsed.conceptGap()).contains(true);
        assertThat(parsed.text()).doesNotContain("CONCEPT_GAP");
    }

    @Test
    void null_원문은_빈_텍스트로_처리한다() {
        Parsed parsed = signal.parse(null);

        assertThat(parsed.conceptGap()).isEmpty();
        assertThat(parsed.text()).isEmpty();
    }

    @Test
    void 지시문에_방향검증_개선점은_NO로_명시한다() {
        String instruction = signal.instruction();

        assertThat(instruction).contains("방향이 맞는지 검증");
        assertThat(instruction).contains("개선점");
        assertThat(instruction).contains("반드시 NO");
    }

    @Test
    void FREE_TEXT_풀이중은_적용대상이다() {
        assertThat(signal.isApplicable(freeText("SOLVING"))).isTrue();
        assertThat(signal.isApplicable(freeText("BEFORE_SOLVE"))).isTrue();
    }

    @Test
    void 버튼질문과_AFTER_SOLVE는_적용대상이_아니다() {
        HintRequest button = freeText("SOLVING");
        button.setQuestionType("BUTTON");
        assertThat(signal.isApplicable(button)).isFalse();

        assertThat(signal.isApplicable(freeText("AFTER_SOLVE"))).isFalse();
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
