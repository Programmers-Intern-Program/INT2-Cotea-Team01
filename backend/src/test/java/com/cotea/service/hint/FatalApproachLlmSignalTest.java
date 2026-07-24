package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
import com.cotea.service.hint.FatalApproachLlmSignal.Parsed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FatalApproachLlmSignalTest {

    private FatalApproachLlmSignal signal;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        signal = new FatalApproachLlmSignal();
        objectMapper = new ObjectMapper();
    }

    @Test
    void YES_마커는_파싱하고_텍스트에서_제거한다() {
        String raw = "다른 관점도 생각해보세요.\n[[FATAL_APPROACH: YES]]";
        Parsed parsed = signal.parse(raw);

        assertThat(parsed.fatalApproach()).contains(true);
        assertThat(parsed.text()).doesNotContain("FATAL_APPROACH");
        assertThat(parsed.text()).isEqualTo("다른 관점도 생각해보세요.");
    }

    @Test
    void NO_마커는_false로_파싱한다() {
        Parsed parsed = signal.parse("이 부분을 다시 보세요.\n[[FATAL_APPROACH: NO]]");

        assertThat(parsed.fatalApproach()).contains(false);
        assertThat(parsed.text()).doesNotContain("FATAL_APPROACH");
    }

    @Test
    void 마커_없으면_판정_비움() {
        Parsed parsed = signal.parse("힌트만 드릴게요.");

        assertThat(parsed.fatalApproach()).isEmpty();
        assertThat(parsed.text()).isEqualTo("힌트만 드릴게요.");
    }

    @Test
    void YES면_경고_블록을_앞에_붙인다() {
        String result = signal.ensureWarningPrefix("방향을 바꿔보세요.", true);

        assertThat(result).startsWith(FatalApproachLlmSignal.WARNING_BLOCK.strip());
        assertThat(result).contains("방향을 바꿔보세요.");
    }

    @Test
    void NO면_원문_유지() {
        assertThat(signal.ensureWarningPrefix("그대로 가세요.", false))
                .isEqualTo("그대로 가세요.");
    }

    @Test
    void 이미_경고가_있으면_중복_삽입하지_않는다() {
        String once = signal.ensureWarningPrefix("본문", true);
        String twice = signal.ensureWarningPrefix(once, true);

        assertThat(twice).isEqualTo(once);
    }

    @Test
    void userCode와_fatal신호_있으면_적용() throws Exception {
        HintRequest request = requestWithCode("SOLVING");
        assertThat(signal.isApplicable(request, problemWithFatalSignals())).isTrue();
    }

    @Test
    void 코드없거나_신호없으면_미적용() throws Exception {
        HintRequest noCode = requestWithCode("SOLVING");
        noCode.setUserCode("  ");
        assertThat(signal.isApplicable(noCode, problemWithFatalSignals())).isFalse();

        HintRequest withCode = requestWithCode("SOLVING");
        assertThat(signal.isApplicable(withCode, objectMapper.createObjectNode())).isFalse();
    }

    @Test
    void AFTER_SOLVE는_미적용() throws Exception {
        assertThat(signal.isApplicable(requestWithCode("AFTER_SOLVE"), problemWithFatalSignals()))
                .isFalse();
    }

    @Test
    void 지시문에_신호_본문_노출_금지가_있다() {
        String instruction = signal.instruction();

        assertThat(instruction).contains("YES/NO 판정에만");
        assertThat(instruction).contains("나열하거나");
        assertThat(instruction).contains("복사하지");
    }

    @Test
    void ensureSignalsInContext_빈_fields에도_신호를_강제_포함한다() {
        ObjectNode problem = problemWithFatalSignals();
        ObjectNode problemContext = objectMapper.createObjectNode();
        problemContext.putObject("fields");

        signal.ensureSignalsInContext(problemContext, problem);

        JsonNode signals = problemContext.path("fields").path(FatalApproachLlmSignal.FATAL_SIGNALS_FIELD);
        assertThat(signals.isArray()).isTrue();
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).asText()).isEqualTo("0인 칸을 영역 계산에 포함하는 방식");
    }

    @Test
    void ensureSignalsInContext_이미_있으면_덮어쓴다() {
        ObjectNode problem = problemWithFatalSignals();
        ObjectNode problemContext = objectMapper.createObjectNode();
        ObjectNode fields = problemContext.putObject("fields");
        fields.putArray(FatalApproachLlmSignal.FATAL_SIGNALS_FIELD).add("오래된신호");

        signal.ensureSignalsInContext(problemContext, problem);

        assertThat(problemContext.path("fields").path(FatalApproachLlmSignal.FATAL_SIGNALS_FIELD).get(0).asText())
                .isEqualTo("0인 칸을 영역 계산에 포함하는 방식");
    }

    @Test
    void ensureSignalsInContext_신호없으면_fields를_건드리지_않는다() {
        ObjectNode problemContext = objectMapper.createObjectNode();
        problemContext.putObject("fields");

        signal.ensureSignalsInContext(problemContext, objectMapper.createObjectNode());

        assertThat(problemContext.path("fields").isEmpty()).isTrue();
    }

    private HintRequest requestWithCode(String stage) {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage(stage);
        request.setQuestionType("FREE_TEXT");
        request.setQuestionText("이 코드가 맞나요?");
        request.setUserCode("class Solution { public int[] solution(int[][] p) { return new int[2]; } }");
        return request;
    }

    private ObjectNode problemWithFatalSignals() {
        ObjectNode problem = objectMapper.createObjectNode();
        ObjectNode diagnosis = problem.putObject("wrongAnswerDiagnosis");
        ArrayNode signals = diagnosis.putArray("fatalApproachSignals");
        signals.add("0인 칸을 영역 계산에 포함하는 방식");
        return problem;
    }
}
