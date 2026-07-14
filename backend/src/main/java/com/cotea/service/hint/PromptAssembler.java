package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import com.cotea.service.rag.RagChunk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PromptAssembler {

    private final ObjectMapper objectMapper;
    private final QuestionResolver questionResolver;

    public String buildSystemPrompt(JsonNode policy, int hintLevel, HintRequest request) {
        String question = questionResolver.resolve(request);
        boolean wrongAnswerBeforeReason = "WRONG_ANSWER".equals(request.getStage())
                && !questionResolver.userAsksReason(question);

        JsonNode levelPolicy = policy.path("hintLevelPolicy").path(String.valueOf(hintLevel));
        JsonNode stagePolicy = policy.path("phasePolicy").path(request.getStage());
        JsonNode responseFormat = policy.path("responseFormat");

        StringBuilder sb = new StringBuilder();
        sb.append(policy.path("tutorIdentity").asText()).append("\n\n");
        sb.append("## 핵심 규칙\n");
        policy.path("coreRules").forEach(rule -> sb.append("- ").append(rule.asText()).append('\n'));
        sb.append("\n## 절대 제공하지 말 것\n");
        policy.path("doNotReveal").forEach(item -> sb.append("- ").append(item.asText()).append('\n'));

        if (wrongAnswerBeforeReason) {
            JsonNode before = policy.path("reasonExplanationPolicy").path("beforeUserAskReason");
            sb.append("\n## 현재 stage: WRONG_ANSWER (이유 질문 전)\n");
            sb.append(stagePolicy.path("description").asText()).append('\n');
            sb.append("\n## 오답 진단 정책\n").append(before.path("behavior").asText()).append('\n');
            sb.append("예시: ").append(before.path("example").asText()).append('\n');
            appendSubmissionResultGuidance(sb, policy, request, true);
            sb.append("\n### 이번 응답에서 추가 금지 (hintLevel 무시)\n");
            sb.append("- 알고리즘·자료구조 이름\n");
            sb.append("- 오답·시간초과 원인 추정\n");
            sb.append("- 구현 방향·접근법 제안\n");
        } else {
            sb.append("\n## 현재 힌트 레벨: ").append(hintLevel)
                    .append(" (").append(levelPolicy.path("name").asText()).append(")\n");
            sb.append(levelPolicy.path("description").asText()).append("\n\n");
            sb.append("### 이 레벨에서 허용\n");
            levelPolicy.path("allow").forEach(item -> sb.append("- ").append(item.asText()).append('\n'));
            sb.append("\n### 이 레벨에서 금지\n");
            levelPolicy.path("forbid").forEach(item -> sb.append("- ").append(item.asText()).append('\n'));

            sb.append("\n## 현재 stage: ").append(request.getStage()).append('\n');
            sb.append(stagePolicy.path("description").asText()).append('\n');

            if ("WRONG_ANSWER".equals(request.getStage())) {
                JsonNode after = policy.path("reasonExplanationPolicy").path("afterUserAskReason");
                sb.append("\n## 오답 진단 정책\n").append(after.path("behavior").asText());
                appendSubmissionResultGuidance(sb, policy, request, false);
                if (questionResolver.userAsksReason(question)) {
                    appendUserCodeDiagnosisGuidance(sb, policy, request);
                }
            }
        }

        sb.append("\n\n## 응답 형식\n")
                .append("- 톤: ").append(responseFormat.path("tone").asText()).append('\n')
                .append("- 최대 ").append(responseFormat.path("maxParagraphs").asInt(3)).append("문단\n")
                .append("- 마지막에 되묻는 질문 포함: ")
                .append(responseFormat.path("preferQuestion").asBoolean(true));
        JsonNode structure = responseFormat.path("structure");
        if (structure.isArray() && structure.size() > 0) {
            sb.append("\n- 응답 구조:\n");
            structure.forEach(item -> sb.append("  - ").append(item.asText()).append('\n'));
        }
        return sb.toString();
    }

    public String buildUserMessage(HintRequest request, ObjectNode problemContext, List<RagChunk> ragChunks)
            throws JsonProcessingException {
        String question = questionResolver.resolve(request);
        StringBuilder sb = new StringBuilder();
        sb.append("문제: ").append(problemContext.path("title").asText())
                .append(" (").append(problemContext.path("level").asText()).append(")\n");
        sb.append("stage: ").append(request.getStage()).append('\n');
        sb.append("hintLevel: ").append(request.getHintLevel()).append("\n\n");
        sb.append("## 문제별 메타데이터 (참고용, 그대로 복사하지 말 것)\n");
        sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(problemContext));

        if (!ragChunks.isEmpty()) {
            sb.append("\n\n## RAG 검색 결과 (참고용)\n");
            for (RagChunk chunk : ragChunks) {
                sb.append("- [").append(chunk.getSource()).append('/').append(chunk.getChunkId())
                        .append("] ").append(chunk.getContent()).append('\n');
            }
        }

        sb.append("\n## 사용자 질문\n").append(question);
        return sb.toString();
    }

    private void appendSubmissionResultGuidance(
            StringBuilder sb,
            JsonNode policy,
            HintRequest request,
            boolean beforeReason
    ) {
        if (request.getSubmissionResult() == null) {
            return;
        }
        JsonNode byResult = policy.path("reasonExplanationPolicy")
                .path("bySubmissionResult")
                .path(request.getSubmissionResult());
        if (!byResult.isObject()) {
            return;
        }

        sb.append("\n### 채점 결과별 안내 (submissionResult)\n");
        if (beforeReason) {
            if (byResult.hasNonNull("resultPhrase")) {
                sb.append("- 안내 문구: ").append(byResult.path("resultPhrase").asText()).append('\n');
            }
            if (byResult.hasNonNull("beforeAskFocus")) {
                sb.append("- 유도 초점: ").append(byResult.path("beforeAskFocus").asText()).append('\n');
            }
            return;
        }
        if (byResult.hasNonNull("afterAskFocus")) {
            sb.append("- 진단 초점: ").append(byResult.path("afterAskFocus").asText()).append('\n');
        }
    }

    private void appendUserCodeDiagnosisGuidance(StringBuilder sb, JsonNode policy, HintRequest request) {
        if (request.getUserCode() == null || request.getUserCode().isBlank()) {
            return;
        }
        JsonNode withUserCode = policy.path("reasonExplanationPolicy")
                .path("afterUserAskReason")
                .path("withUserCode");
        if (!withUserCode.isObject()) {
            return;
        }

        sb.append("\n### 사용자 코드 참고 지침 (userCode 제공됨)\n");
        if (withUserCode.hasNonNull("behavior")) {
            sb.append(withUserCode.path("behavior").asText()).append('\n');
        }
        JsonNode forbid = withUserCode.path("forbid");
        if (forbid.isArray() && forbid.size() > 0) {
            sb.append("금지:\n");
            forbid.forEach(item -> sb.append("- ").append(item.asText()).append('\n'));
        }
    }
}
