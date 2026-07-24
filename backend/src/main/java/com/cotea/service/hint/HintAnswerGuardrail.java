package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HintAnswerGuardrail {

    /**
     * public: {@link ForbiddenConceptLlmSignal}이 자기점검 마커 목록으로 재사용하고,
     * {@code com.cotea.service.problem.generation} 패키지의 문제 데이터 생성 검증기(§8 체크리스트,
     * docs/problem-data-authoring-rules.md)도 Lv1 금지어 린트에 동일 목록을 재사용한다.
     */
    public static final List<String> LEVEL_1_FORBIDDEN_TERMS = List.of(
            "BFS", "DFS", "DP", "Union-Find", "유니온 파인드",
            "큐", "스택", "visited", "방문 배열"
    );
    private static final List<String> WRONG_ANSWER_BEFORE_REASON_TERMS = List.of(
            "원인", "때문", "시간 복잡도", "반례", "병목", "틀린 이유"
    );
    private static final Pattern CODE_BLOCK = Pattern.compile("```");
    private static final Pattern SOLUTION_CODE = Pattern.compile(
            "(class\\s+Solution|public\\s+\\w+\\s+solution\\s*\\(|public\\s+static\\s+void\\s+main\\s*\\()"
    );

    private final QuestionResolver questionResolver;

    public GuardrailResult inspect(String answer, HintRequest request, int hintLevel) {
        if (answer == null || answer.isBlank()) {
            return GuardrailResult.pass();
        }

        List<String> riskSignals = new ArrayList<>();
        if (hintLevel == 1) {
            addContainedTerms(answer, LEVEL_1_FORBIDDEN_TERMS, "Lv1 금지어 포함", riskSignals);
        }
        if (isWrongAnswerBeforeReason(request)) {
            addContainedTerms(answer, WRONG_ANSWER_BEFORE_REASON_TERMS, "이유 질문 전 진단 표현 포함", riskSignals);
        }
        if (hintLevel <= 3 && CODE_BLOCK.matcher(answer).find()) {
            riskSignals.add("Lv3 이하 코드블록 포함");
        }
        if (hintLevel <= 3 && SOLUTION_CODE.matcher(answer).find()) {
            riskSignals.add("Lv3 이하 완성 코드 형태 포함");
        }

        if (riskSignals.isEmpty()) {
            return GuardrailResult.pass();
        }
        return GuardrailResult.review(riskSignals);
    }

    private boolean isWrongAnswerBeforeReason(HintRequest request) {
        if (!"WRONG_ANSWER".equals(request.getStage())) {
            return false;
        }
        String question = questionResolver.resolve(request);
        return !questionResolver.userAsksReason(question);
    }

    private void addContainedTerms(
            String answer,
            List<String> terms,
            String prefix,
            List<String> riskSignals
    ) {
        for (String term : terms) {
            if (KoreanBoundaryMatcher.containsAsStandaloneTerm(answer, term)) {
                riskSignals.add(prefix + ": " + term);
            }
        }
    }
}
