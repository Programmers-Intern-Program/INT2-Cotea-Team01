package com.cotea.service.hint;

import java.util.List;

public record GuardrailResult(boolean needsReview, List<String> riskSignals) {

    public static GuardrailResult pass() {
        return new GuardrailResult(false, List.of());
    }

    public static GuardrailResult review(List<String> riskSignals) {
        return new GuardrailResult(true, List.copyOf(riskSignals));
    }
}
