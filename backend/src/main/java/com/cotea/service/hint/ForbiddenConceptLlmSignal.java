package com.cotea.service.hint;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Lv1 답변 생성에 "금지 개념 자기점검"을 피기백한다(추가 API 호출 없음).
 *
 * <p>모델은 답변을 모두 작성한 뒤 맨 마지막에 제어 라인 {@code [[FORBIDDEN_CONCEPT: ...]]}을 덧붙이고,
 * 백엔드는 이를 파싱해 {@link HintAnswerGuardrail#LEVEL_1_FORBIDDEN_TERMS}에 실제로 속하는 값만 신뢰한 뒤
 * <b>사용자에게 보이기 전에 제거</b>한다.
 *
 * <p>"우선순위큐"처럼 단순 문자열 경계 판정(포함되는 다른 단어인지)으로는 놓치는 합성어 케이스를,
 * 의미를 이해하는 LLM 스스로의 닫힌목록 자기 신고로 보완한다. 목록을 벗어난 값은 무시하므로
 * 자유 서술이 아니라 항상 우리가 아는 개념 중에서만 응답이 나온다.
 */
@Component
public class ForbiddenConceptLlmSignal {

    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[\\s*FORBIDDEN_CONCEPT\\s*:\\s*([^\\]]*)]]",
            Pattern.CASE_INSENSITIVE
    );

    /** 시스템 프롬프트 끝에 덧붙일 제어 라인 지시문. Lv1 답변에만 사용한다. */
    public String instruction() {
        String options = String.join(", ", HintAnswerGuardrail.LEVEL_1_FORBIDDEN_TERMS);
        return """


                ## 내부 자기점검 (사용자에게 절대 보이지 않음)
                위 답변을 모두 작성한 뒤, 맨 마지막 줄에 아래 제어 라인을 정확히 하나만 덧붙여라.
                이 줄은 시스템이 제거하므로 사용자에게 노출되지 않는다. 제어 라인 외 다른 위치에는 절대 쓰지 마라.
                답변에서 아래 목록의 개념을 직접 언급이든, 합성어(예: 우선순위큐)든, 풀어서 설명했든
                하나라도 드러냈다면 해당하는 것만 쉼표로 나열하고, 하나도 없으면 NONE이라고 써라.
                [[FORBIDDEN_CONCEPT: 목록 중 해당 값 또는 NONE]]
                목록: %s
                """.formatted(options);
    }

    /**
     * 원문에서 제어 라인을 찾아 자기신고된 금지 개념을 파싱하고, 마커를 제거한 사용자 노출용 텍스트를 반환한다.
     * 목록에 없는 값은 무시한다. 마커가 없으면 신고 결과는 비어 있고 텍스트는 사실상 원문 그대로다.
     */
    public Parsed parse(String rawText) {
        if (rawText == null) {
            return new Parsed("", Set.of());
        }
        Matcher matcher = MARKER.matcher(rawText);
        Set<String> forbiddenConcepts = new LinkedHashSet<>();
        while (matcher.find()) {
            forbiddenConcepts.clear();
            for (String candidate : matcher.group(1).split(",")) {
                String trimmed = candidate.trim();
                if (HintAnswerGuardrail.LEVEL_1_FORBIDDEN_TERMS.contains(trimmed)) {
                    forbiddenConcepts.add(trimmed);
                }
            }
        }
        String cleaned = MARKER.matcher(rawText).replaceAll("").stripTrailing();
        return new Parsed(cleaned, forbiddenConcepts);
    }

    public record Parsed(String text, Set<String> forbiddenConcepts) {
    }
}
