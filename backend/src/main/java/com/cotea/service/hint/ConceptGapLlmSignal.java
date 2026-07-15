package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 기존 힌트 LLM 호출에 "개념부재 판단"을 피기백한다(추가 API 호출 없음).
 *
 * <p>모델은 답변을 모두 작성한 뒤 맨 마지막에 제어 라인 {@code [[CONCEPT_GAP: YES|NO]]} 을 덧붙이고,
 * 백엔드는 이를 파싱해 플래그로 쓴 뒤 <b>사용자에게 보이기 전에 제거</b>한다. 마커는 가드레일/셀프리뷰보다
 * 먼저 제거되므로 사용자 답변에 절대 노출되지 않는다.
 *
 * <p>룰({@link ConceptGapClassifier})과 OR로 합치는 폴백형 하이브리드 — 룰이 놓치는 맥락 케이스를
 * LLM 판단으로 보완하되, LLM이 신호를 안 주면 룰 결과만 사용한다.
 */
@Component
public class ConceptGapLlmSignal {

    private static final Pattern MARKER = Pattern.compile(
            "\\[\\[\\s*CONCEPT_GAP\\s*:\\s*([A-Za-z가-힣]+)\\s*\\]\\]",
            Pattern.CASE_INSENSITIVE
    );

    private static final String INSTRUCTION = """


            ## 내부 판정 (사용자에게 절대 보이지 않음)
            위 답변을 모두 작성한 뒤, 맨 마지막 줄에 아래 제어 라인을 정확히 하나만 덧붙여라.
            이 줄은 시스템이 제거하므로 사용자에게 노출되지 않는다. 제어 라인 외 다른 위치에는 절대 쓰지 마라.
            [[CONCEPT_GAP: YES]] 또는 [[CONCEPT_GAP: NO]]
            - YES: 학습자가 이 문제의 '유형/개념 자체'를 모르는 것으로 보일 때
              (예: 알고리즘·자료구조가 무엇인지 물음, 어디서부터 시작할지 감이 전혀 없음, 개념 설명 자체를 요청).
            - NO: 단순히 힌트 양을 조절하려는 경우, 자기 코드 디버깅·구현 세부·오답 원인을 묻는 경우.
            """;

    /** FREE_TEXT이고 풀이완료 단계가 아닐 때만 LLM 판정을 적용한다(버튼 힌트 답변은 깨끗하게 유지). */
    public boolean isApplicable(HintRequest request) {
        return request != null
                && "FREE_TEXT".equals(request.getQuestionType())
                && !"AFTER_SOLVE".equals(request.getStage());
    }

    /** 시스템 프롬프트 끝에 덧붙일 제어 라인 지시문. */
    public String instruction() {
        return INSTRUCTION;
    }

    /**
     * 원문에서 제어 라인을 찾아 개념부재 여부를 파싱하고, 마커를 제거한 사용자 노출용 텍스트를 반환한다.
     * 마커가 없으면 {@code conceptGap}은 비어 있고 텍스트는 사실상 원문 그대로다.
     */
    public Parsed parse(String rawText) {
        if (rawText == null) {
            return new Parsed("", Optional.empty());
        }
        Matcher matcher = MARKER.matcher(rawText);
        Optional<Boolean> gap = Optional.empty();
        while (matcher.find()) {
            gap = Optional.of(toBoolean(matcher.group(1)));
        }
        String cleaned = MARKER.matcher(rawText).replaceAll("").stripTrailing();
        return new Parsed(cleaned, gap);
    }

    private boolean toBoolean(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return v.startsWith("y") || v.startsWith("t") || v.startsWith("예") || v.startsWith("참");
    }

    public record Parsed(String text, Optional<Boolean> conceptGap) {
    }
}
