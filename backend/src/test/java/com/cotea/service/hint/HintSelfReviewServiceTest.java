package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cotea.client.LlmClient;
import com.cotea.controller.dto.ConversationMessage;
import com.cotea.controller.dto.HintRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

class HintSelfReviewServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsFinalAnswerFromReviewJson() throws Exception {
        LlmClient llmClient = new StubLlmClient("""
                {
                  "passed": false,
                  "violations": ["Lv1 금지어 포함"],
                  "finalAnswer": "선택지를 한 단계씩 넓혀보는 관점으로 바라보면 좋아요. 다음 상태는 어떻게 정의할 수 있을까요?"
                }
                """);
        HintSelfReviewService service = new HintSelfReviewService(
                llmClient,
                objectMapper,
                new QuestionResolver()
        );

        String result = service.reviewAndFix(
                policy(),
                beforeSolveRequest(),
                1,
                "user message",
                "이 문제는 BFS로 보면 좋아요.",
                GuardrailResult.review(List.of("Lv1 금지어 포함: BFS"))
        );

        assertThat(result).isEqualTo("선택지를 한 단계씩 넓혀보는 관점으로 바라보면 좋아요. 다음 상태는 어떻게 정의할 수 있을까요?");
    }

    @Test
    void returnsDraftAnswerWhenReviewJsonCannotBeParsed() throws Exception {
        HintSelfReviewService service = new HintSelfReviewService(
                new StubLlmClient("not json"),
                objectMapper,
                new QuestionResolver()
        );

        String result = service.reviewAndFix(
                policy(),
                beforeSolveRequest(),
                1,
                "user message",
                "초안 답변",
                GuardrailResult.review(List.of("위험 신호"))
        );

        assertThat(result).isEqualTo("초안 답변");
    }

    @Test
    void returnsFinalAnswerWhenPassedFieldIsMissing() throws Exception {
        LlmClient llmClient = new StubLlmClient("""
                {
                  "finalAnswer": "다음 상태는 어떻게 정의할 수 있을까요?"
                }
                """);
        HintSelfReviewService service = new HintSelfReviewService(
                llmClient,
                objectMapper,
                new QuestionResolver()
        );

        String result = service.reviewAndFix(
                policy(),
                beforeSolveRequest(),
                1,
                "user message",
                "초안 답변",
                GuardrailResult.review(List.of("위험 신호"))
        );

        assertThat(result).isEqualTo("다음 상태는 어떻게 정의할 수 있을까요?");
    }

    @Test
    void logsReviewOutcomeWithSelfReviewLogTypeForObservability() throws Exception {
        LlmClient llmClient = new StubLlmClient("""
                {
                  "passed": false,
                  "violations": ["Lv1 금지어 포함"],
                  "finalAnswer": "다음 상태는 어떻게 정의할 수 있을까요?"
                }
                """);
        HintSelfReviewService service = new HintSelfReviewService(
                llmClient,
                objectMapper,
                new QuestionResolver()
        );

        Logger logger = (Logger) LoggerFactory.getLogger(HintSelfReviewService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.reviewAndFix(
                    policy(),
                    beforeSolveRequest(),
                    1,
                    "user message",
                    "초안 답변",
                    GuardrailResult.review(List.of("Lv1 금지어 포함: BFS"))
            );
        } finally {
            logger.detachAppender(appender);
        }

        ILoggingEvent event = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("SELF_REVIEW"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("SELF_REVIEW 로그가 남지 않았습니다."));

        assertThat(event.getMDCPropertyMap()).containsEntry("logType", "self-review");
        assertThat(event.getFormattedMessage()).contains("passed=false");
    }

    private JsonNode policy() throws Exception {
        return objectMapper.readTree(new ClassPathResource("config/prompt-policy.json").getInputStream());
    }

    private HintRequest beforeSolveRequest() {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("BEFORE_SOLVE");
        request.setHintLevel(1);
        request.setQuestionType("BUTTON");
        request.setButtonId("hint_level_1");
        return request;
    }

    private record StubLlmClient(String response) implements LlmClient {

        @Override
        public String generate(String systemPrompt, List<ConversationMessage> history, String userMessage) {
            return response;
        }
    }
}
