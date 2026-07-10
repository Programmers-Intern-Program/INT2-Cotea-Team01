package com.cotea.service.hint;

import com.cotea.controller.dto.HintRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProblemContextSelector {

    private static final String COMMON_MISTAKES_PATH = "wrongAnswerDiagnosis.commonMistakes";
    private static final Map<String, String> SUBMISSION_RESULT_TO_SYMPTOM = Map.of(
            "WRONG_ANSWER", "오답",
            "TIME_LIMIT_EXCEEDED", "시간초과",
            "RUNTIME_ERROR", "런타임에러"
    );

    private final QuestionResolver questionResolver;
    private final ObjectMapper objectMapper;

    public List<String> extractTags(JsonNode problem) {
        List<String> tags = new ArrayList<>();
        JsonNode primary = problem.path("classification").path("primary");
        if (primary.isArray()) {
            primary.forEach(item -> {
                if (item.hasNonNull("tag")) {
                    tags.add(item.get("tag").asText());
                }
            });
        }
        return tags;
    }

    public ObjectNode select(JsonNode problem, JsonNode policy, HintRequest request, int hintLevel) {
        String stage = request.getStage();
        JsonNode levelPolicy = policy.path("hintLevelPolicy").path(String.valueOf(hintLevel));
        List<String> fieldPaths = collectFieldPaths(levelPolicy);

        if ("WRONG_ANSWER".equals(stage)) {
            String question = questionResolver.resolve(request);
            JsonNode reasonPolicy = policy.path("reasonExplanationPolicy");
            if (questionResolver.userAsksReason(question)) {
                List<String> extended = new ArrayList<>(fieldPaths);
                extended.addAll(collectFieldPaths(reasonPolicy.path("afterUserAskReason")));
                fieldPaths = extended;
            } else {
                // 이유 질문 전: 진단·접근 메타 제외 (결과 안내만)
                fieldPaths = List.of();
            }
        }

        Map<String, JsonNode> fields = new LinkedHashMap<>();
        for (String path : dedupe(fieldPaths)) {
            JsonNode value = getNested(problem, path);
            if (COMMON_MISTAKES_PATH.equals(path) && request.getSubmissionResult() != null) {
                value = filterCommonMistakesBySubmissionResult(value, request.getSubmissionResult());
            }
            if (value != null && !value.isNull() && !value.isMissingNode() && !isEmptyNode(value)) {
                fields.put(path, value);
            }
        }

        ObjectNode context = objectMapper.createObjectNode();
        context.put("problemId", problem.path("problemId").asInt());
        context.put("title", problem.path("source").path("title").asText(""));
        context.put("level", problem.path("source").path("level").asText(""));
        context.set("fields", objectMapper.valueToTree(fields));

        if (request.getSubmissionResult() != null) {
            context.put("submissionResult", request.getSubmissionResult());
        }
        if (request.getUserCode() != null && !request.getUserCode().isBlank()) {
            context.put("userCode", request.getUserCode());
        }
        return context;
    }

    private List<String> dedupe(List<String> paths) {
        return new ArrayList<>(new LinkedHashSet<>(paths));
    }

    private List<String> collectFieldPaths(JsonNode node) {
        List<String> paths = new ArrayList<>();
        JsonNode fields = node.path("usesProblemFields");
        if (fields.isArray()) {
            for (JsonNode field : fields) {
                paths.add(field.asText());
            }
        }
        return paths;
    }

    private JsonNode filterCommonMistakesBySubmissionResult(JsonNode mistakes, String submissionResult) {
        String symptom = SUBMISSION_RESULT_TO_SYMPTOM.get(submissionResult);
        if (symptom == null || mistakes == null || !mistakes.isArray()) {
            return mistakes;
        }
        ArrayNode filtered = objectMapper.createArrayNode();
        for (JsonNode item : mistakes) {
            if (symptom.equals(item.path("symptom").asText())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private boolean isEmptyNode(JsonNode value) {
        return value.isArray() && value.isEmpty();
    }

    private JsonNode getNested(JsonNode problem, String dottedPath) {
        if ("classification.tags".equals(dottedPath)) {
            return objectMapper.valueToTree(extractTags(problem));
        }
        JsonNode current = problem;
        for (String key : dottedPath.split("\\.")) {
            if (!current.has(key)) {
                return null;
            }
            current = current.get(key);
        }
        return current;
    }
}
