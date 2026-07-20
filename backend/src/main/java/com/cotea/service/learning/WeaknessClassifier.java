package com.cotea.service.learning;

import com.cotea.controller.dto.HintRequest;
import org.springframework.stereotype.Component;

@Component
public class WeaknessClassifier {

    public WeaknessClassification classify(HintRequest request, String question, String route) {
        if ("OFF_TOPIC".equals(route)) {
            return new WeaknessClassification(WeaknessType.ETC, DetectedIntent.OFF_TOPIC);
        }

        String buttonId = request.getButtonId();
        if ("hint_level_1".equals(buttonId) || "hint_level_2".equals(buttonId)) {
            return new WeaknessClassification(WeaknessType.APPROACH, DetectedIntent.APPROACH_UNKNOWN);
        }
        if ("hint_level_3".equals(buttonId)) {
            return new WeaknessClassification(WeaknessType.IMPLEMENTATION, DetectedIntent.IMPLEMENTATION_ORDER);
        }
        if ("hint_level_4".equals(buttonId) || "why_wrong".equals(buttonId)) {
            return new WeaknessClassification(WeaknessType.DEBUGGING, DetectedIntent.DEBUG_REASON);
        }
        if ("why_tle".equals(buttonId)) {
            return new WeaknessClassification(WeaknessType.COMPLEXITY, DetectedIntent.TIME_COMPLEXITY);
        }
        if ("why_runtime_error".equals(buttonId)) {
            return new WeaknessClassification(WeaknessType.DEBUGGING, DetectedIntent.RUNTIME_REASON);
        }

        String normalized = question == null ? "" : question.toLowerCase();
        if (containsAny(normalized, "시간초과", "시간 초과", "시간복잡도", "시간 복잡도", "느려", "timeout")) {
            return new WeaknessClassification(WeaknessType.COMPLEXITY, DetectedIntent.TIME_COMPLEXITY);
        }
        if (containsAny(normalized, "방문", "visited")) {
            return new WeaknessClassification(WeaknessType.IMPLEMENTATION, DetectedIntent.VISITED_HANDLING);
        }
        if (containsAny(normalized, "자료구조", "큐", "queue", "스택", "stack", "map", "set")) {
            return new WeaknessClassification(WeaknessType.IMPLEMENTATION, DetectedIntent.DATA_STRUCTURE);
        }
        if (containsAny(normalized, "경계", "인덱스", "index", "범위", "반례")) {
            return new WeaknessClassification(WeaknessType.DEBUGGING, DetectedIntent.BOUNDARY_CONDITION);
        }
        if (containsAny(normalized, "상태", "state", "정의")) {
            return new WeaknessClassification(WeaknessType.IMPLEMENTATION, DetectedIntent.STATE_DEFINITION);
        }
        if (containsAny(normalized, "개념", "뭔지", "모르겠", "이해")) {
            return new WeaknessClassification(WeaknessType.CONCEPT, DetectedIntent.ALGORITHM_CONCEPT);
        }
        if (containsAny(normalized, "접근", "관점", "어떻게 풀", "어떻게 봐")) {
            return new WeaknessClassification(WeaknessType.APPROACH, DetectedIntent.APPROACH_UNKNOWN);
        }
        if (containsAny(normalized, "왜 틀", "오답", "틀렸", "원인")) {
            return new WeaknessClassification(WeaknessType.DEBUGGING, DetectedIntent.DEBUG_REASON);
        }
        if (containsAny(normalized, "문법", "에러", "컴파일", "syntax")) {
            return new WeaknessClassification(WeaknessType.SYNTAX, DetectedIntent.SYNTAX);
        }
        return new WeaknessClassification(WeaknessType.ETC, DetectedIntent.ETC);
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
