package com.cotea.service.problem;

import com.cotea.service.problem.entity.ProblemEntity;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProblemMetaMapper {

    private final ObjectMapper objectMapper;

    public JsonNode toJson(ProblemEntity record) {
        ObjectNode problem = objectMapper.createObjectNode();
        problem.put("problemId", record.getProblemId());

        ObjectNode source = problem.putObject("source");
        source.put("platform", record.getPlatform());
        source.put("title", record.getTitle());
        source.put("level", record.getLevel());
        source.put("url", record.getUrl());
        source.put("language", "java");

        ObjectNode classification = problem.putObject("classification");
        classification.put("difficultyReason", record.getDifficultyReason());
        ArrayNode classifications = objectMapper.createArrayNode();
        record.getClassifications().forEach(item -> {
            ObjectNode node = classifications.addObject();
            node.put("tag", item.getTag());
            node.put("subcategory", item.getSubcategory());
        });
        classification.set("primary", classifications);

        ObjectNode approach = problem.putObject("approach");
        approach.put("keyInsight", record.getKeyInsight());
        approach.put("recommendedApproach", record.getRecommendedApproach());
        approach.put("expectedTimeComplexity", record.getExpectedTimeComplexity());
        approach.put("expectedSpaceComplexity", record.getExpectedSpaceComplexity());
        approach.set("alternativeApproaches", toStringArray(record.getAlternativeApproaches(), item -> item.getApproachName()));
        ArrayNode complexityVariables = objectMapper.createArrayNode();
        record.getComplexityVariables().forEach(item -> {
            ObjectNode node = complexityVariables.addObject();
            node.put("variableName", item.getVariableName());
            node.put("description", item.getVariableDescription());
        });
        approach.set("complexityVariables", complexityVariables);

        ObjectNode solvingSupport = problem.putObject("solvingSupport");
        solvingSupport.set("keyDataStructures", toStringArray(record.getKeyDataStructures(), item -> item.getStructureName()));
        solvingSupport.set("implementationCheckpoints", toStringArray(
                record.getImplementationCheckpoints(),
                item -> item.getCheckpointText()
        ));
        ArrayNode stuckPointHints = objectMapper.createArrayNode();
        record.getStuckPointHints().forEach(item -> {
            ObjectNode node = stuckPointHints.addObject();
            node.put("pointKey", item.getPointKey());
            node.put("hint", item.getHintText());
        });
        solvingSupport.set("stuckPointHints", stuckPointHints);

        ObjectNode wrongAnswerDiagnosis = problem.putObject("wrongAnswerDiagnosis");
        ArrayNode commonMistakes = objectMapper.createArrayNode();
        record.getWrongAnswerMistakes().forEach(item -> {
            ObjectNode node = commonMistakes.addObject();
            node.put("symptom", item.getSymptom());
            node.put("likelyCause", item.getLikelyCause());
            node.put("directionHint", item.getDirectionHint());
        });
        wrongAnswerDiagnosis.set("commonMistakes", commonMistakes);
        wrongAnswerDiagnosis.set("fatalApproachSignals", toStringArray(record.getFatalApproachSignals(), item -> item.getSignalText()));

        problem.set("edgeCases", toStringArray(record.getEdgeCases(), item -> item.getCaseText()));

        ObjectNode afterSolve = problem.putObject("afterSolve");
        afterSolve.set("evaluationCriteria", toStringArray(record.getEvaluationCriteria(), item -> item.getCriteriaText()));
        afterSolve.set("optimizationHints", toStringArray(record.getOptimizationHints(), item -> item.getHintText()));
        afterSolve.set("similarProblems", toStringArray(record.getSimilarProblems(), item -> item.getProblemName()));

        ObjectNode metadata = problem.putObject("metadata");
        metadata.put("version", record.getMetadataVersion());
        metadata.put("reviewedBy", record.getReviewedBy());
        metadata.put("source", "mysql");
        return problem;
    }

    private <T> ArrayNode toStringArray(List<T> values, Function<T, String> mapper) {
        ArrayNode result = objectMapper.createArrayNode();
        values.forEach(value -> result.add(mapper.apply(value)));
        return result;
    }
}
