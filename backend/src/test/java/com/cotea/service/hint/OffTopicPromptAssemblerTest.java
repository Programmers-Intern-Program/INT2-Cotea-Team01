package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OffTopicPromptAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PromptAssembler assembler;
    private JsonNode policy;

    @BeforeEach
    void setUp() throws IOException {
        assembler = new PromptAssembler(objectMapper, new QuestionResolver());
        policy = objectMapper.readTree(new ClassPathResource("config/prompt-policy.json").getInputStream());
    }

    @Test
    void buildsOffTopicSystemPromptFromPolicy() {
        String systemPrompt = assembler.buildOffTopicSystemPrompt(policy);

        assertThat(systemPrompt).contains("전처리 범위 밖 질문 정책");
        assertThat(systemPrompt).contains("문제별 전처리 메타데이터는 사용하지 않는다");
        assertThat(systemPrompt).contains("전체 정답 코드");
    }

    @Test
    void buildsOffTopicUserMessageWithoutProblemMetaDump() {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("BEFORE_SOLVE");
        request.setQuestionType("FREE_TEXT");
        request.setQuestionText("자바 HashMap이 뭐야?");

        String userMessage = assembler.buildOffTopicUserMessage(request, "카카오프렌즈 컬러링북", "Lv2");

        assertThat(userMessage).contains("카카오프렌즈 컬러링북");
        assertThat(userMessage).contains("자바 HashMap이 뭐야?");
        assertThat(userMessage).contains("전처리 메타 없이");
        assertThat(userMessage).doesNotContain("wrongAnswerDiagnosis");
    }
}
