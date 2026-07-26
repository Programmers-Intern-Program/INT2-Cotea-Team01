package com.cotea.service.problem.generation;

import com.cotea.service.problem.entity.ApproachAlternativeEntity;
import com.cotea.service.problem.entity.ComplexityVariableEntity;
import com.cotea.service.problem.entity.EdgeCaseEntity;
import com.cotea.service.problem.entity.EvaluationCriteriaEntity;
import com.cotea.service.problem.entity.FatalApproachSignalEntity;
import com.cotea.service.problem.entity.KeyDataStructureEntity;
import com.cotea.service.problem.entity.OptimizationHintEntity;
import com.cotea.service.problem.entity.ProblemClassificationEntity;
import com.cotea.service.problem.entity.ProblemEntity;
import com.cotea.service.problem.entity.SimilarProblemEntity;
import com.cotea.service.problem.entity.SolvingCheckpointEntity;
import com.cotea.service.problem.entity.StuckPointHintEntity;
import com.cotea.service.problem.entity.WrongAnswerMistakeEntity;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link ProblemGenerationValidator}를 통과한 LLM 생성 JSON을 problem 테이블 + 12개 자식 테이블
 * 엔티티로 변환한다. `D:\INT2\json_to_sql.py`의 convert() 함수와 동일한 매핑 규칙을 따른다 —
 * 특히 classification.primary[].subcategory 배열은 tag당 여러 subcategory가 있으면
 * ProblemClassificationEntity 행을 여러 개(tag는 같고 subcategory만 다른) 만든다.
 */
@Component
public class GeneratedProblemMapper {

    public ProblemEntity toEntity(JsonNode generated) {
        JsonNode source = generated.path("source");
        JsonNode classification = generated.path("classification");
        JsonNode approach = generated.path("approach");
        JsonNode solvingSupport = generated.path("solvingSupport");
        JsonNode wrongAnswerDiagnosis = generated.path("wrongAnswerDiagnosis");
        JsonNode afterSolve = generated.path("afterSolve");

        Integer problemId = generated.path("problemId").asInt();

        return ProblemEntity.builder()
                .problemId(problemId)
                .platform(source.path("platform").asText())
                .title(source.path("title").asText())
                .level(source.path("level").asText())
                .url(source.path("url").asText())
                .difficultyReason(classification.path("difficultyReason").asText())
                .recommendedApproach(approach.path("recommendedApproach").asText())
                .expectedTimeComplexity(approach.path("expectedTimeComplexity").asText())
                .expectedSpaceComplexity(approach.path("expectedSpaceComplexity").asText())
                .keyInsight(approach.path("keyInsight").asText())
                .metadataVersion(generated.path("metadataVersion").asText())
                .reviewedBy(generated.path("reviewedBy").isNull() ? null : generated.path("reviewedBy").asText())
                .classifications(toClassifications(problemId, classification.path("primary")))
                .alternativeApproaches(toSimpleList(approach.path("alternativeApproaches"),
                        node -> ApproachAlternativeEntity.builder().problemId(problemId).approachName(node.asText()).build()))
                .keyDataStructures(toSimpleList(solvingSupport.path("keyDataStructures"),
                        node -> KeyDataStructureEntity.builder().problemId(problemId).structureName(node.asText()).build()))
                .implementationCheckpoints(toSolvingCheckpoints(problemId, solvingSupport.path("implementationCheckpoints")))
                .stuckPointHints(toStuckPointHints(problemId, solvingSupport.path("stuckPointHints")))
                .wrongAnswerMistakes(toWrongAnswerMistakes(problemId, wrongAnswerDiagnosis.path("commonMistakes")))
                .complexityVariables(toComplexityVariables(problemId, approach.path("complexityVariables")))
                .fatalApproachSignals(toSimpleList(wrongAnswerDiagnosis.path("fatalApproachSignals"),
                        node -> FatalApproachSignalEntity.builder().problemId(problemId).signalText(node.asText()).build()))
                .edgeCases(toSimpleList(generated.path("edgeCases"),
                        node -> EdgeCaseEntity.builder().problemId(problemId).caseText(node.asText()).build()))
                .evaluationCriteria(toSimpleList(afterSolve.path("evaluationCriteria"),
                        node -> EvaluationCriteriaEntity.builder().problemId(problemId).criteriaText(node.asText()).build()))
                .optimizationHints(toSimpleList(afterSolve.path("optimizationHints"),
                        node -> OptimizationHintEntity.builder().problemId(problemId).hintText(node.asText()).build()))
                .similarProblems(toSimpleList(afterSolve.path("similarProblems"),
                        node -> SimilarProblemEntity.builder().problemId(problemId).problemName(node.asText()).build()))
                .build();
    }

    private List<ProblemClassificationEntity> toClassifications(Integer problemId, JsonNode primary) {
        List<ProblemClassificationEntity> result = new ArrayList<>();
        for (JsonNode item : primary) {
            String tag = item.path("tag").asText();
            JsonNode subcategoryNode = item.path("subcategory");
            if (subcategoryNode.isArray() && !subcategoryNode.isEmpty()) {
                for (JsonNode sub : subcategoryNode) {
                    result.add(ProblemClassificationEntity.builder()
                            .problemId(problemId).tag(tag).subcategory(sub.asText()).build());
                }
            } else {
                result.add(ProblemClassificationEntity.builder()
                        .problemId(problemId).tag(tag).subcategory(null).build());
            }
        }
        return result;
    }

    private List<SolvingCheckpointEntity> toSolvingCheckpoints(Integer problemId, JsonNode checkpoints) {
        List<SolvingCheckpointEntity> result = new ArrayList<>();
        int order = 1;
        for (JsonNode checkpoint : checkpoints) {
            result.add(SolvingCheckpointEntity.builder()
                    .problemId(problemId)
                    .checkpointText(checkpoint.asText())
                    .orderIndex(order++)
                    .build());
        }
        return result;
    }

    private List<StuckPointHintEntity> toStuckPointHints(Integer problemId, JsonNode hints) {
        List<StuckPointHintEntity> result = new ArrayList<>();
        if (!hints.isObject()) {
            return result;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = hints.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.add(StuckPointHintEntity.builder()
                    .problemId(problemId)
                    .pointKey(entry.getKey())
                    .hintText(entry.getValue().asText())
                    .build());
        }
        return result;
    }

    private List<WrongAnswerMistakeEntity> toWrongAnswerMistakes(Integer problemId, JsonNode mistakes) {
        List<WrongAnswerMistakeEntity> result = new ArrayList<>();
        for (JsonNode mistake : mistakes) {
            result.add(WrongAnswerMistakeEntity.builder()
                    .problemId(problemId)
                    .symptom(mistake.path("symptom").asText())
                    .likelyCause(mistake.path("likelyCause").asText())
                    .directionHint(mistake.path("directionHint").asText())
                    .build());
        }
        return result;
    }

    private List<ComplexityVariableEntity> toComplexityVariables(Integer problemId, JsonNode variables) {
        List<ComplexityVariableEntity> result = new ArrayList<>();
        if (!variables.isObject()) {
            return result;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = variables.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            result.add(ComplexityVariableEntity.builder()
                    .problemId(problemId)
                    .variableName(entry.getKey())
                    .variableDescription(entry.getValue().asText())
                    .build());
        }
        return result;
    }

    private <T> List<T> toSimpleList(JsonNode arrayNode, java.util.function.Function<JsonNode, T> mapper) {
        List<T> result = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            result.add(mapper.apply(node));
        }
        return result;
    }
}
