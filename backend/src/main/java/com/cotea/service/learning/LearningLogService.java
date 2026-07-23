package com.cotea.service.learning;

import com.cotea.controller.dto.HintRequest;
import com.cotea.exception.CoteaException;
import com.cotea.service.auth.JwtTokenProvider;
import com.cotea.service.learning.entity.UserHintLogEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningLogService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserHintLogRepository userHintLogRepository;
    private final WeaknessClassifier weaknessClassifier;
    private final ObjectMapper objectMapper;

    public void saveIfAuthenticated(String authorization, HintRequest request, HintLogContext context) {
        if (authorization == null || authorization.isBlank()) {
            return;
        }

        Long userId;
        try {
            userId = jwtTokenProvider.parseUserId(authorization);
        } catch (CoteaException e) {
            log.warn("Skip hint log because auth token is invalid: {}", e.getErrorCode());
            return;
        }

        WeaknessClassification classification = weaknessClassifier.classify(
                request,
                context.question(),
                context.route()
        );

        UserHintLogEntity log = UserHintLogEntity.create(new UserHintLogEntity.CreateCommand(
                userId,
                request.getProblemId(),
                context.problem().path("source").path("title").asText(""),
                context.problem().path("source").path("level").asText(""),
                toJson(context.tags()),
                request.getStage(),
                request.getHintLevel(),
                request.getQuestionType(),
                request.getButtonId(),
                context.question(),
                request.getSubmissionResult(),
                request.getLanguage(),
                classification.weaknessType(),
                classification.detectedIntent(),
                codeLength(request.getUserCode()),
                codeLineCount(request.getUserCode()),
                context.policy().path("schemaVersion").asText(""),
                toJson(selectedProblemFields(context.problemContext())),
                context.route(),
                context.llmProvider(),
                request.getSolved()
        ));
        userHintLogRepository.save(log);
    }

    private Integer codeLength(String code) {
        return code == null ? 0 : code.length();
    }

    private Integer codeLineCount(String code) {
        if (code == null || code.isBlank()) {
            return 0;
        }
        return code.split("\\R", -1).length;
    }

    private List<String> selectedProblemFields(JsonNode problemContext) {
        List<String> fields = new ArrayList<>();
        if (problemContext == null) {
            return fields;
        }
        JsonNode fieldNode = problemContext.path("fields");
        if (!fieldNode.isObject()) {
            return fields;
        }
        Iterator<String> names = fieldNode.fieldNames();
        while (names.hasNext()) {
            fields.add(names.next());
        }
        return fields;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
