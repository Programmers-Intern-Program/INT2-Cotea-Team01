package com.cotea.service.hint;

import com.cotea.client.LlmClient;
import com.cotea.config.CoteaProperties;
import com.cotea.controller.dto.HintRequest;
import com.cotea.controller.dto.HintResponse;
import com.cotea.exception.CoteaException;
import com.cotea.service.learning.HintLogContext;
import com.cotea.service.learning.LearningLogService;
import com.cotea.service.policy.PromptPolicyLoader;
import com.cotea.service.problem.ProblemMetaService;
import com.cotea.service.rag.RagChunk;
import com.cotea.service.rag.RagRetrievalService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    private final HintRequestValidator hintRequestValidator;
    private final HintAnswerGuardrail hintAnswerGuardrail;
    private final HintSelfReviewService hintSelfReviewService;
    private final ForbiddenConceptLlmSignal forbiddenConceptLlmSignal;
    private final OffTopicQuestionClassifier offTopicQuestionClassifier;
    private final OffTopicRouteLlmClassifier offTopicRouteLlmClassifier;
    private final OffTopicLlmRouter offTopicLlmRouter;
    private final ConceptGapClassifier conceptGapClassifier;
    private final ConceptGapLlmSignal conceptGapLlmSignal;
    private final CoteaProperties coteaProperties;
    private final LearningLogService learningLogService;

    public HintResponse generate(HintRequest request) {
        return generate(request, null);
    }

    public HintResponse generate(HintRequest request, String authorization) {
        hintRequestValidator.validate(request);

        JsonNode policy = promptPolicyLoader.getPolicy();
        JsonNode problem = problemMetaService.load(request.getProblemId());
        int hintLevel = hintLevelResolver.resolve(request, policy);
        request.setHintLevel(hintLevel);

        String question = questionResolver.resolve(request);
        if (isOffTopicRoute(request, question, problem)) {
            return generateOffTopic(request, policy, problem, hintLevel, question, authorization);
        }
        boolean conceptGap = conceptGapClassifier.isConceptGap(request, question);
        return generateRelated(request, policy, problem, hintLevel, question, conceptGap, authorization);
    }

    private boolean isOffTopicRoute(HintRequest request, String question, JsonNode problem) {
        if (!coteaProperties.getOffTopic().isEnabled()) {
            return false;
        }
        String title = problem.path("source").path("title").asText("");
        String level = problem.path("source").path("level").asText("");
        OffTopicQuestionClassifier.Verdict verdict =
                offTopicQuestionClassifier.classify(request, question, title);
        if (verdict == OffTopicQuestionClassifier.Verdict.RELATED) {
            return false;
        }
        if (verdict == OffTopicQuestionClassifier.Verdict.OFF_TOPIC) {
            return true;
        }
        // AMBIGUOUS
        if (!coteaProperties.getOffTopic().isLlmRouteEnabled()) {
            log.info("[OFF_TOPIC_ROUTE] llmRouteEnabled=false → RELATED");
            return false;
        }
        OffTopicQuestionClassifier.Verdict llmVerdict =
                offTopicRouteLlmClassifier.classify(question, title, level);
        return llmVerdict == OffTopicQuestionClassifier.Verdict.OFF_TOPIC;
    }

    private HintResponse generateRelated(
            HintRequest request,
            JsonNode policy,
            JsonNode problem,
            int hintLevel,
            String question,
            boolean conceptGap,
            String authorization
    ) {
        List<String> tags = problemContextSelector.extractTags(problem);
        ObjectNode problemContext = problemContextSelector.select(problem, policy, request, hintLevel);
        List<RagChunk> ragChunks = ragRetrievalService.retrieve(tags, hintLevel, question);

        boolean llmSignalApplicable = conceptGapLlmSignal.isApplicable(request);
        String systemPrompt = promptAssembler.buildSystemPrompt(policy, hintLevel, request);
        if (llmSignalApplicable) {
            systemPrompt = systemPrompt + conceptGapLlmSignal.instruction();
        }
        if (hintLevel == 1) {
            systemPrompt = systemPrompt + forbiddenConceptLlmSignal.instruction();
        }
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
                    .suggestConceptDrill(conceptGap)
                    .tags(tags)
                    .systemPrompt(systemPrompt)
                    .userMessage(userMessage)
                    .ragChunkCount(ragChunks.size())
                    .build();
        }

        String rawText = llmClient.generate(
                systemPrompt,
                request.getConversationHistory(),
                userMessage
        );

        // 마커는 가드레일/셀프리뷰보다 먼저 제거해 사용자 노출 및 재검수 오염을 막는다.
        String responseText;
        boolean llmConceptGap = false;
        if (llmSignalApplicable) {
            ConceptGapLlmSignal.Parsed parsed = conceptGapLlmSignal.parse(rawText);
            responseText = parsed.text();
            llmConceptGap = parsed.conceptGap().orElse(false);
        } else {
            responseText = rawText;
        }
        Set<String> selfReportedForbiddenConcepts = Set.of();
        if (hintLevel == 1) {
            ForbiddenConceptLlmSignal.Parsed forbiddenParsed = forbiddenConceptLlmSignal.parse(responseText);
            responseText = forbiddenParsed.text();
            selfReportedForbiddenConcepts = forbiddenParsed.forbiddenConcepts();
        }
        responseText = applyGuardrailIfNeeded(
                policy, request, hintLevel, userMessage, responseText, selfReportedForbiddenConcepts
        );

        boolean suggestConceptDrill = conceptGap || llmConceptGap;
        log.info("[CONCEPT_GAP] problemId={} rule={} llm={} suggest={}",
                request.getProblemId(), conceptGap, llmConceptGap, suggestConceptDrill);

        HintResponse response = HintResponse.builder()
                .responseText(responseText)
                .route("RELATED")
                .llmProvider("claude")
                .stage(request.getStage())
                .hintLevel(hintLevel)
                .suggestConceptDrill(suggestConceptDrill)
                .build();
        learningLogService.saveIfAuthenticated(authorization, request, new HintLogContext(
                policy,
                problem,
                problemContext,
                tags,
                question,
                "RELATED",
                "claude"
        ));
        return response;
    }

    private HintResponse generateOffTopic(
            HintRequest request,
            JsonNode policy,
            JsonNode problem,
            int hintLevel,
            String question,
            String authorization
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
        String responseText = applyGuardrailIfNeeded(
                policy,
                request,
                hintLevel,
                userMessage,
                result.getResponseText(),
                Set.of()
        );

        List<String> tags = problemContextSelector.extractTags(problem);
        HintResponse response = HintResponse.builder()
                .responseText(responseText)
                .route("OFF_TOPIC")
                .llmProvider(result.getLlmProvider())
                .stage(request.getStage())
                .hintLevel(hintLevel)
                .build();
        learningLogService.saveIfAuthenticated(authorization, request, new HintLogContext(
                policy,
                problem,
                null,
                tags,
                question,
                "OFF_TOPIC",
                result.getLlmProvider()
        ));
        return response;
    }

    private String applyGuardrailIfNeeded(
            JsonNode policy,
            HintRequest request,
            int hintLevel,
            String userMessage,
            String responseText,
            Set<String> llmSelfReportedForbiddenConcepts
    ) {
        GuardrailResult guardrail = hintAnswerGuardrail.inspect(responseText, request, hintLevel);
        if (!llmSelfReportedForbiddenConcepts.isEmpty()) {
            List<String> merged = new ArrayList<>(guardrail.riskSignals());
            llmSelfReportedForbiddenConcepts.forEach(concept -> merged.add("Lv1 금지어 자기신고: " + concept));
            guardrail = GuardrailResult.review(merged);
        }
        if (!guardrail.needsReview()) {
            return responseText;
        }
        return hintSelfReviewService.reviewAndFix(
                policy,
                request,
                hintLevel,
                userMessage,
                responseText,
                guardrail
        );
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }
}
