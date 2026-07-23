package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 힌트 LLM 호출에 "치명적 접근 이탈" 판정을 피기백한다(추가 API 호출 없음).
 *
 * <p>모델은 답변 맨 마지막에 {@code [[FATAL_APPROACH: YES|NO]]} 를 붙이고,
 * 백엔드는 파싱·제거한 뒤 YES이면 사용자 답변 앞에 선제 경고 블록을 강제한다.
 *
 * <p>{@code userCode}가 있고 문제 메타에 {@code fatalApproachSignals}가 있을 때만 적용한다.
 */
@Component
public class FatalApproachLlmSignal {

    static final String WARNING_BLOCK =
            "지금 코드 방향은 이 문제에서 답이 나오기 어려운 접근일 수 있어요. "
                    + "다른 관점으로 다시 생각해보면 좋아요.\n\n";

    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[\\s*FATAL_APPROACH\\s*:\\s*([A-Za-z가-힣]+)\\s*\\]\\]",
            Pattern.CASE_INSENSITIVE
    );

    private static final String INSTRUCTION = """


            ## 내부 판정 (사용자에게 절대 보이지 않음)
            위 답변을 모두 작성한 뒤, 맨 마지막 줄에 아래 제어 라인을 정확히 하나만 덧붙여라.
            이 줄은 시스템이 제거하므로 사용자에게 노출되지 않는다. 제어 라인 외 다른 위치에는 절대 쓰지 마라.
            [[FATAL_APPROACH: YES]] 또는 [[FATAL_APPROACH: NO]]
            - YES: 사용자 코드/질문이 문제 메타의 fatalApproachSignals에 해당하는,
              답이 구조적으로 나올 수 없는 접근일 때 (비효율이지만 원리상 가능한 접근은 NO).
            - NO: 접근이 틀리지 않았거나, 코드가 부족해 판단이 어렵거나, 단순 구현/디버깅 질문일 때.
            """;

    public boolean isApplicable(HintRequest request, JsonNode problem) {
        if (request == null) {
            return false;
        }
        if ("AFTER_SOLVE".equals(request.getStage())) {
            return false;
        }
        String code = request.getUserCode();
        if (code == null || code.isBlank()) {
            return false;
        }
        return hasFatalApproachSignals(problem);
    }

    public String instruction() {
        return INSTRUCTION;
    }

    public Parsed parse(String rawText) {
        if (rawText == null) {
            return new Parsed("", Optional.empty());
        }
        Matcher matcher = MARKER.matcher(rawText);
        Optional<Boolean> fatal = Optional.empty();
        while (matcher.find()) {
            fatal = Optional.of(toBoolean(matcher.group(1)));
        }
        String cleaned = MARKER.matcher(rawText).replaceAll("").stripTrailing();
        return new Parsed(cleaned, fatal);
    }

    /** YES이면 경고 블록을 맨 앞에 강제한다(이미 같은 문구로 시작하면 중복 삽입하지 않음). */
    public String ensureWarningPrefix(String text, boolean fatalApproach) {
        if (!fatalApproach) {
            return text == null ? "" : text;
        }
        String body = text == null ? "" : text;
        if (body.startsWith(WARNING_BLOCK.strip()) || body.contains("답이 나오기 어려운 접근")) {
            return body;
        }
        return WARNING_BLOCK + body;
    }

    private boolean hasFatalApproachSignals(JsonNode problem) {
        if (problem == null || problem.isMissingNode()) {
            return false;
        }
        JsonNode signals = problem.path("wrongAnswerDiagnosis").path("fatalApproachSignals");
        return signals.isArray() && !signals.isEmpty();
    }

    private boolean toBoolean(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("y") || v.startsWith("t") || v.startsWith("예") || v.startsWith("참");
    }

    public record Parsed(String text, Optional<Boolean> fatalApproach) {
    }
}
