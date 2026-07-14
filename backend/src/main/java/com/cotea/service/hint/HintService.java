package com.cotea.service.hint;

import com.cotea.client.LlmClient;
import com.cotea.config.CoteaProperties;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HintService {

    private final PromptPolicyLoader promptPolicyLoader;
    private final ProblemMetaService problemMetaService;
    private final HintLevelResolver hintLevelResolver;
    private final ProblemContextSelector problemContextSelector;
    private final PromptAssembler promptAssembler;
    private final RagRetrievalService ragRetrievalService;
    private final LlmClient llmClient;
    private final QuestionResolver questionResolver;
    private final OffTopicQuestionClassifier offTopicQuestionClassifier;
    private final OffTopicLlmRouter offTopicLlmRouter;
    private final CoteaProperties coteaProperties;

    public HintResponse generate(HintRequest request) {
        if (request.getProblemId() == null) {
            throw new CoteaException("MISSING_PROBLEM_ID", "problemId가 필요합니다.", 400);
        }
        validateStage(request.getStage());

        JsonNode policy = promptPolicyLoader.getPolicy();
        JsonNode problem = problemMetaService.load(request.getProblemId());
        int hintLevel = hintLevelResolver.resolve(request, policy);
        request.setHintLevel(hintLevel);

        String question = questionResolver.resolve(request);
        boolean offTopic = coteaProperties.getOffTopic().isEnabled()
                && offTopicQuestionClassifier.isOffTopic(request, question);

        if (offTopic) {
            return generateOffTopic(request, policy, problem, hintLevel, question);
        }
        return generateRelated(request, policy, problem, hintLevel, question);
    }

    private HintResponse generateRelated(
            HintRequest request,
            JsonNode policy,
            JsonNode problem,
            int hintLevel,
            String question
    ) {
        List<String> tags = problemContextSelector.extractTags(problem);
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
                    .route("RELATED")
                    .llmProvider("claude")
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
                .route("RELATED")
                .llmProvider("claude")
                .stage(request.getStage())
                .hintLevel(hintLevel)
                .build();
    }

    private HintResponse generateOffTopic(
            HintRequest request,
            JsonNode policy,
            JsonNode problem,
            int hintLevel,
            String question
    ) {
        String title = problem.path("source").path("title").asText("");
        String level = problem.path("source").path("level").asText("");
        String systemPrompt = promptAssembler.buildOffTopicSystemPrompt(policy);
        String userMessage = promptAssembler.buildOffTopicUserMessage(request, title, level);

        log.info("[OFF_TOPIC] problemId={} question={}", request.getProblemId(), abbreviate(question));

        if (Boolean.TRUE.equals(request.getDryRun())) {
            return HintResponse.builder()
                    .dryRun(true)
                    .route("OFF_TOPIC")
                    .llmProvider(offTopicLlmRouter.preferredProviderForDryRun())
                    .stage(request.getStage())
                    .hintLevel(hintLevel)
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .ragChunkCount(0)
                    .build();
        }

        OffTopicLlmRouter.Result result = offTopicLlmRouter.generate(
                systemPrompt,
                request.getConversationHistory(),
                userMessage
        );

        return HintResponse.builder()
                .responseText(result.getResponseText())
                .route("OFF_TOPIC")
                .llmProvider(result.getLlmProvider())
                .stage(request.getStage())
                .hintLevel(hintLevel)
                .build();
    }

    private void validateStage(String stage) {
        if (!List.of("BEFORE_SOLVE", "SOLVING", "WRONG_ANSWER", "AFTER_SOLVE").contains(stage)) {
            throw new CoteaException("INVALID_STAGE", "stage 값이 올바르지 않습니다.", 400);
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }
}
