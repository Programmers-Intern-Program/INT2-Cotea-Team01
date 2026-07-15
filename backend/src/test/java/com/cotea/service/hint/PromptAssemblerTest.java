package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.controller.dto.HintRequest;
import com.cotea.service.rag.RagChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PromptAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PromptAssembler assembler;
    private JsonNode policy;

    @BeforeEach
    void setUp() throws IOException {
        assembler = new PromptAssembler(objectMapper, new QuestionResolver());
        policy = objectMapper.readTree(new ClassPathResource("config/prompt-policy.json").getInputStream());
    }

    @Test
    void includesBeforeAskSubmissionResultGuidance() {
        HintRequest request = wrongAnswerRequest("WRONG_ANSWER", "wrong_result_only");

        String systemPrompt = assembler.buildSystemPrompt(policy, 2, request);

        assertThat(systemPrompt).contains("제출 결과가 오답이에요.");
        assertThat(systemPrompt).contains("로직·경계 조건을 스스로 점검하도록 유도");
        assertThat(systemPrompt).doesNotContain("expectedTimeComplexity와 비교");
    }

    @Test
    void includesAfterAskSubmissionResultGuidanceForTimeLimit() {
        HintRequest request = wrongAnswerRequest("TIME_LIMIT_EXCEEDED", "why_tle");

        String systemPrompt = assembler.buildSystemPrompt(policy, 2, request);

        assertThat(systemPrompt).contains("expectedTimeComplexity와 비교해 병목 가능성 제시");
        assertThat(systemPrompt).doesNotContain("로직·경계 조건을 스스로 점검하도록 유도");
    }

    @Test
    void includesAfterAskSubmissionResultGuidanceForRuntimeError() {
        HintRequest request = wrongAnswerRequest("RUNTIME_ERROR", "why_runtime_error");

        String systemPrompt = assembler.buildSystemPrompt(policy, 2, request);

        assertThat(systemPrompt).contains("배열 범위·방문 배열 크기 등 런타임 원인 제시");
    }

    @Test
    void includesUserCodeDiagnosisGuidanceWhenCodeProvided() {
        HintRequest request = wrongAnswerRequest("WRONG_ANSWER", "why_wrong");
        request.setUserCode("class Solution { }");

        String systemPrompt = assembler.buildSystemPrompt(policy, 2, request);

        assertThat(systemPrompt).contains("사용자 코드 참고 지침 (userCode 제공됨)");
        assertThat(systemPrompt).contains("가장 관련 있는 항목 1~2개만");
        assertThat(systemPrompt).contains("commonMistakes 후보 전부 나열");
    }

    @Test
    void includesResponseStructureGuidance() {
        HintRequest request = wrongAnswerRequest("WRONG_ANSWER", "why_wrong");

        String systemPrompt = assembler.buildSystemPrompt(policy, 2, request);

        assertThat(systemPrompt).contains("응답 구조");
        assertThat(systemPrompt).contains("핵심 힌트 1~2문단");
        assertThat(systemPrompt).contains("마지막에 되묻는 질문 1문장");
    }

    @Test
    void omitsUserCodeDiagnosisGuidanceWhenCodeMissing() {
        HintRequest request = wrongAnswerRequest("WRONG_ANSWER", "why_wrong");

        String systemPrompt = assembler.buildSystemPrompt(policy, 2, request);

        assertThat(systemPrompt).doesNotContain("사용자 코드 참고 지침");
    }

    @Test
    void includesRagChunksSectionWhenChunksProvided() throws IOException {
        HintRequest request = beforeSolveRequest();
        ObjectNode problemContext = objectMapper.createObjectNode();
        problemContext.put("title", "카카오프렌즈 컬러링북");
        problemContext.put("level", "Lv2");
        problemContext.set("fields", objectMapper.createObjectNode());
        List<RagChunk> ragChunks = List.of(
                RagChunk.builder().source("knowledge_base").chunkId("bfs").content("BFS 정의 문장").distance(0.0).build()
        );

        String userMessage = assembler.buildUserMessage(request, problemContext, ragChunks);

        assertThat(userMessage).contains("## RAG 검색 결과 (참고용)");
        assertThat(userMessage).contains("[knowledge_base/bfs] BFS 정의 문장");
    }

    @Test
    void omitsRagChunksSectionWhenChunksEmpty() throws IOException {
        HintRequest request = beforeSolveRequest();
        ObjectNode problemContext = objectMapper.createObjectNode();
        problemContext.put("title", "카카오프렌즈 컬러링북");
        problemContext.put("level", "Lv2");
        problemContext.set("fields", objectMapper.createObjectNode());

        String userMessage = assembler.buildUserMessage(request, problemContext, List.of());

        assertThat(userMessage).doesNotContain("RAG 검색 결과");
    }

    private HintRequest beforeSolveRequest() {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("BEFORE_SOLVE");
        request.setHintLevel(2);
        request.setQuestionType("BUTTON");
        request.setButtonId("hint_level_2");
        return request;
    }

    private HintRequest wrongAnswerRequest(String submissionResult, String buttonId) {
        HintRequest request = new HintRequest();
        request.setProblemId(1829);
        request.setStage("WRONG_ANSWER");
        request.setHintLevel(2);
        request.setQuestionType("BUTTON");
        request.setButtonId(buttonId);
        request.setSubmissionResult(submissionResult);
        return request;
    }
}
