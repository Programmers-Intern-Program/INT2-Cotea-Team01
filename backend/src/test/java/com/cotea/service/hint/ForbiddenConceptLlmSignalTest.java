package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.service.hint.ForbiddenConceptLlmSignal.Parsed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ForbiddenConceptLlmSignalTest {

    private ForbiddenConceptLlmSignal signal;

    @BeforeEach
    void setUp() {
        signal = new ForbiddenConceptLlmSignal();
    }

    @Test
    void 목록에_있는_개념을_자기신고하면_파싱하고_마커를_제거한다() {
        String raw = "우선순위큐를 활용해보면 어떨까요?\n[[FORBIDDEN_CONCEPT: 큐]]";

        Parsed parsed = signal.parse(raw);

        assertThat(parsed.forbiddenConcepts()).containsExactly("큐");
        assertThat(parsed.text()).doesNotContain("FORBIDDEN_CONCEPT");
        assertThat(parsed.text()).isEqualTo("우선순위큐를 활용해보면 어떨까요?");
    }

    @Test
    void 여러_개념을_쉼표로_신고하면_모두_파싱한다() {
        Parsed parsed = signal.parse("이 문제는 큐와 스택을 함께 써보세요.\n[[FORBIDDEN_CONCEPT: 큐, 스택]]");

        assertThat(parsed.forbiddenConcepts()).containsExactlyInAnyOrder("큐", "스택");
    }

    @Test
    void NONE이면_빈_결과로_파싱한다() {
        Parsed parsed = signal.parse("이 문제는 어떤 자료구조가 필요할지 생각해보세요.\n[[FORBIDDEN_CONCEPT: NONE]]");

        assertThat(parsed.forbiddenConcepts()).isEmpty();
        assertThat(parsed.text()).doesNotContain("FORBIDDEN_CONCEPT");
    }

    @Test
    void 목록에_없는_값은_무시한다() {
        Parsed parsed = signal.parse("답변입니다.\n[[FORBIDDEN_CONCEPT: 이진탐색, 큐]]");

        assertThat(parsed.forbiddenConcepts()).containsExactly("큐");
    }

    @Test
    void 마커가_없으면_결과는_비어있고_텍스트는_유지된다() {
        Parsed parsed = signal.parse("이 문제는 그래프 탐색으로 접근해요.");

        assertThat(parsed.forbiddenConcepts()).isEmpty();
        assertThat(parsed.text()).isEqualTo("이 문제는 그래프 탐색으로 접근해요.");
    }

    @Test
    void null_원문은_빈_텍스트와_빈_결과로_처리한다() {
        Parsed parsed = signal.parse(null);

        assertThat(parsed.forbiddenConcepts()).isEmpty();
        assertThat(parsed.text()).isEmpty();
    }

    @Test
    void 지시문에_금지목록_전체가_포함된다() {
        String instruction = signal.instruction();

        assertThat(instruction).contains("FORBIDDEN_CONCEPT");
        assertThat(instruction).contains("큐").contains("스택").contains("BFS").contains("DFS");
    }
}
