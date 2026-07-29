package com.cotea.service.hint;

import com.cotea.client.LlmClient;
import com.cotea.controller.dto.HintRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HintSelfReviewService {

    /**
     * 1차 검수(passed=false)와 위반 피드백을 반영한 재시도(passed=false)까지 모두 실패했을 때
     * 사용자에게 보여줄 안전한 대체 응답. 내부 판정 실패 사실을 노출하지 않는다.
     */
    static final String SAFE_FALLBACK_ANSWER =
            "지금은 안전한 형태로 답변을 정리하지 못했어요. 질문을 조금 더 구체적으로 다시 말씀해 주시겠어요?";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final QuestionResolver questionResolver;

    public String reviewAndFix(
            JsonNode policy,
            HintRequest request,
            int hintLevel,
            String userMessage,
            String draftAnswer,
            GuardrailResult guardrail
    ) {
        String reviewResponse = llmClient.generate(
                buildReviewSystemPrompt(policy),
                null,
                buildReviewUserMessage(policy, request, hintLevel, userMessage, draftAnswer, guardrail)
        );
        Optional<ReviewOutcome> outcome = parseReviewOutcome(reviewResponse);
        if (outcome.isEmpty()) {
            return draftAnswer;
        }
        if (outcome.get().passed()) {
            return outcome.get().finalAnswer();
        }

        log.warn("[SELF_REVIEW] 1차 검수 실패, 위반 피드백을 반영해 재시도합니다. violations={}", outcome.get().violations());
        String retryResponse = llmClient.generate(
                buildReviewSystemPrompt(policy),
                null,
                buildRetryUserMessage(policy, request, hintLevel, userMessage, draftAnswer, guardrail, outcome.get().violations())
        );
        Optional<ReviewOutcome> retryOutcome = parseReviewOutcome(retryResponse);
        if (retryOutcome.isPresent() && retryOutcome.get().passed()) {
            return retryOutcome.get().finalAnswer();
        }

        log.warn("[SELF_REVIEW] 재시도도 실패해 안전 응답으로 대체합니다.");
        return SAFE_FALLBACK_ANSWER;
    }

    private String buildReviewSystemPrompt(JsonNode policy) {
        JsonNode responseFormat = policy.path("responseFormat");
        return """
                너는 Cotea AI 튜터 답변 검수자다.

                목표:
                - 초안 답변이 Cotea 정책을 지키는지 검토한다.
                - 정책 위반이 있으면 사용자에게 보여줄 최종 답변으로 수정한다.
                - 정답 코드, 완성 풀이, 과도한 힌트를 제공하지 않는다.
                - 초안에 없는 새로운 풀이 정보를 과하게 추가하지 않는다.

                검토 기준:
                1. hintLevel 제한을 지켰는가?
                2. stage 제한을 지켰는가?
                3. 사용자가 묻지 않은 내용을 먼저 설명하지 않았는가?
                4. 정답 코드나 완성 의사코드를 제공하지 않았는가?
                5. 메타데이터를 그대로 복사하지 않았는가?
                6. 답변이 너무 길지 않은가?

                응답 형식:
                - 톤: %s
                - 최대 %d문단
                - 마지막에 되묻는 질문 포함: %s

                반드시 JSON만 출력하라.
                {
                  "passed": true 또는 false,
                  "violations": ["위반 사항"],
                  "finalAnswer": "사용자에게 보여줄 최종 답변"
                }
                """.formatted(
                responseFormat.path("tone").asText("친절하지만 간결하게"),
                responseFormat.path("maxParagraphs").asInt(3),
                responseFormat.path("preferQuestion").asBoolean(true)
        );
    }

    private String buildReviewUserMessage(
            JsonNode policy,
            HintRequest request,
            int hintLevel,
            String userMessage,
            String draftAnswer,
            GuardrailResult guardrail
    ) {
        return """
                ## 요청 정보
                stage: %s
                hintLevel: %d
                사용자 질문: %s

                ## 적용 정책 요약
                doNotReveal: %s
                hintLevelPolicy: %s
                phasePolicy: %s
                reasonExplanationPolicy: %s

                ## 룰 기반 위험 신호
                %s

                ## 원래 사용자 메시지와 문제 컨텍스트
                %s

                ## 초안 답변
                %s
                """.formatted(
                request.getStage(),
                hintLevel,
                questionResolver.resolve(request),
                policy.path("doNotReveal"),
                policy.path("hintLevelPolicy").path(String.valueOf(hintLevel)),
                policy.path("phasePolicy").path(request.getStage()),
                policy.path("reasonExplanationPolicy"),
                guardrail.riskSignals(),
                userMessage,
                draftAnswer
        );
    }

    /**
     * 1차 검수에서 passed=false가 나왔을 때, 지적된 위반 사항을 명시적으로 피드백으로 넣어
     * 같은 초안을 다시 검토하게 한다. 막연한 재시도가 아니라 "이전에 이런 이유로 실패했다"는
     * 근거를 함께 주어야 같은 실수를 반복할 위험이 줄어든다.
     */
    private String buildRetryUserMessage(
            JsonNode policy,
            HintRequest request,
            int hintLevel,
            String userMessage,
            String draftAnswer,
            GuardrailResult guardrail,
            List<String> previousViolations
    ) {
        String base = buildReviewUserMessage(policy, request, hintLevel, userMessage, draftAnswer, guardrail);
        String violationsText = previousViolations.isEmpty()
                ? "- (구체적 위반 사유 없음, 정책 전반을 다시 점검할 것)"
                : previousViolations.stream().map(v -> "- " + v).collect(Collectors.joining("\n"));
        return base + """

                ## 1차 검수에서 지적된 위반 사항 (이번에는 반드시 고쳐서 finalAnswer에 반영할 것)
                %s
                """.formatted(violationsText);
    }

    private Optional<ReviewOutcome> parseReviewOutcome(String reviewResponse) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(reviewResponse));
            logReviewOutcome(root);
            String finalAnswer = root.path("finalAnswer").asText();
            if (finalAnswer == null || finalAnswer.isBlank()) {
                log.warn("Self-review response did not include finalAnswer.");
                return Optional.empty();
            }
            boolean passed = root.path("passed").asBoolean(true);
            List<String> violations = new ArrayList<>();
            root.path("violations").forEach(node -> violations.add(node.asText()));
            return Optional.of(new ReviewOutcome(passed, violations, finalAnswer.trim()));
        } catch (Exception e) {
            log.warn("Failed to parse self-review response. Returning draft answer.", e);
            return Optional.empty();
        }
    }

    /**
     * 재검수 결과(passed/violations)를 관측용으로 로그에 남긴다. logs/cotea.log(전체)와
     * logs/self-review.log(이 로그만) 양쪽에 남는다.
     */
    private void logReviewOutcome(JsonNode root) {
        try {
            boolean passed = root.path("passed").asBoolean(true);
            JsonNode violations = root.path("violations");
            MDC.put("logType", "self-review");
            try {
                log.info("[SELF_REVIEW] passed={} violations={}", passed, violations);
            } finally {
                MDC.remove("logType");
            }
        } catch (Exception e) {
            log.warn("Failed to log self-review outcome.", e);
        }
    }

    private String extractJson(String value) {
        if (value == null) {
            return "{}";
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) {
            return value;
        }
        return value.substring(start, end + 1);
    }

    private record ReviewOutcome(boolean passed, List<String> violations, String finalAnswer) {
    }
}
