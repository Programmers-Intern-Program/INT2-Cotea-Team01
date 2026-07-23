package com.cotea.service.hint;

import com.cotea.client.OpenAiClient;
import com.cotea.exception.CoteaException;
import com.cotea.service.hint.OffTopicQuestionClassifier.Verdict;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 규칙으로 판별되지 않은 FREE_TEXT를 저비용 OpenAI로 RELATED / OFF_TOPIC 라우팅한다.
 *
 * <p>미설정·실패·파싱 불가 시 {@link Verdict#RELATED}로 fail-open 한다(튜터 UX 우선).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OffTopicRouteLlmClassifier {

    private static final String SYSTEM_PROMPT =
            "You classify whether a learner's short question is about the current programming problem "
                    + "(RELATED) or unrelated chat / general CS not about this problem (OFF_TOPIC). "
                    + "Reply with exactly one token: RELATED or OFF_TOPIC.";

    private final OpenAiClient openAiClient;

    public Verdict classify(String question, String problemTitle, String problemLevel) {
        if (!openAiClient.isConfigured()) {
            log.info("[OFF_TOPIC_ROUTE] OpenAI 미설정 → RELATED fallback");
            return Verdict.RELATED;
        }
        String userMessage = buildUserMessage(question, problemTitle, problemLevel);
        try {
            String raw = openAiClient.generate(SYSTEM_PROMPT, List.of(), userMessage);
            Verdict parsed = parse(raw);
            log.info("[OFF_TOPIC_ROUTE] verdict={} raw={}", parsed, abbreviate(raw));
            return parsed;
        } catch (CoteaException e) {
            log.warn("[OFF_TOPIC_ROUTE] 실패 → RELATED fallback: {}", e.getMessage());
            return Verdict.RELATED;
        } catch (RuntimeException e) {
            log.warn("[OFF_TOPIC_ROUTE] 예외 → RELATED fallback: {}", e.getMessage());
            return Verdict.RELATED;
        }
    }

    Verdict parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Verdict.RELATED;
        }
        String token = raw.trim().split("\\s+")[0]
                .replaceAll("[^A-Za-z_]", "")
                .toUpperCase(Locale.ROOT);
        if (token.startsWith("OFF")) {
            return Verdict.OFF_TOPIC;
        }
        if (token.startsWith("RELAT")) {
            return Verdict.RELATED;
        }
        return Verdict.RELATED;
    }

    private static String buildUserMessage(String question, String problemTitle, String problemLevel) {
        String title = problemTitle == null || problemTitle.isBlank() ? "(unknown)" : problemTitle.trim();
        String level = problemLevel == null || problemLevel.isBlank() ? "(unknown)" : problemLevel.trim();
        String q = question == null ? "" : question.trim();
        return "problemTitle: " + title + "\nproblemLevel: " + level + "\nquestion: " + q;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= 80 ? t : t.substring(0, 80) + "...";
    }
}
