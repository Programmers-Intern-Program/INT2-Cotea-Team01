package com.cotea.service.hint;

import com.cotea.client.LlmClient;
import com.cotea.controller.dto.HintRequest;
import com.cotea.controller.dto.HintResponse;
import com.cotea.exception.CoteaException;
import com.cotea.service.policy.PromptPolicyLoader;
import com.cotea.service.problem.ProblemMetaService;
import com.cotea.service.rag.RagChunk;
import com.cotea.service.rag.RagRetrievalService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HintService {

    private final PromptPolicyLoader promptPolicyLoader;
    private final ProblemMetaService problemMetaService;
    private final HintLevelResolver hintLevelResolver;
    private final ProblemContextSelector problemContextSelector;
    private final PromptAssembler promptAssembler;
    private final RagRetrievalService ragRetrievalService;
    private final LlmClient llmClient;
    private final QuestionResolver questionResolver;

    public HintResponse generate(HintRequest request) {
        if (request.getProblemId() == null) {
            throw new CoteaException("MISSING_PROBLEM_ID", "problemId가 필요합니다.", 400);
        }
        validateStage(request.getStage());

        JsonNode policy = promptPolicyLoader.getPolicy();
        JsonNode problem = problemMetaService.load(request.getProblemId());
        int hintLevel = hintLevelResolver.resolve(request, policy);
        request.setHintLevel(hintLevel);

        List<String> tags = problemContextSelector.extractTags(problem);
        String question = questionResolver.resolve(request);
        ObjectNode problemContext = problemContextSelector.select(problem, policy, request, hintLevel);
        List<RagChunk> ragChunks = ragRetrievalService.retrieve(tags, hintLevel, question);

        String systemPrompt = promptAssembler.buildSystemPrompt(policy, hintLevel, request);
        String userMessage;
        try {
            userMessage = promptAssembler.buildUserMessage(request, problemContext, ragChunks);
        } catch (JsonProcessingException e) {
            throw new CoteaException("AI_SERVICE_ERROR", "프롬프트 조립 실패", 500);
        }

        if (Boolean.TRUE.equals(request.getDryRun())) {
            return HintResponse.builder()
                    .dryRun(true)
                    .stage(request.getStage())
                    .hintLevel(hintLevel)
                    .tags(tags)
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .ragChunkCount(ragChunks.size())
                    .build();
        }

        String responseText = llmClient.generate(
                systemPrompt,
                request.getConversationHistory(),
                userMessage
        );

        return HintResponse.builder()
                .responseText(responseText)
                .stage(request.getStage())
                .hintLevel(hintLevel)
                .build();
    }

    private void validateStage(String stage) {
        if (!List.of("BEFORE_SOLVE", "SOLVING", "WRONG_ANSWER", "AFTER_SOLVE").contains(stage)) {
            throw new CoteaException("INVALID_STAGE", "stage 값이 올바르지 않습니다.", 400);
        }
    }
}
