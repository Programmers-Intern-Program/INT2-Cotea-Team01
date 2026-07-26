package com.cotea.service.problem.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.service.problem.entity.ProblemEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class GeneratedProblemMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeneratedProblemMapper mapper = new GeneratedProblemMapper();

    @Test
    void mapsScalarFields() throws IOException {
        ProblemEntity entity = mapper.toEntity(sample());

        assertThat(entity.getProblemId()).isEqualTo(1829);
        assertThat(entity.getPlatform()).isEqualTo("programmers");
        assertThat(entity.getTitle()).isEqualTo("카카오프렌즈 컬러링북");
        assertThat(entity.getLevel()).isEqualTo("Lv2");
        assertThat(entity.getReviewedBy()).isNull();
    }

    @Test
    void mapsMultipleSubcategoriesForOneTagAsSeparateRows() throws IOException {
        ProblemEntity entity = mapper.toEntity(sample());

        assertThat(entity.getClassifications()).hasSize(2);
        assertThat(entity.getClassifications()).allMatch(c -> "string".equals(c.getTag()));
        assertThat(entity.getClassifications())
                .extracting("subcategory")
                .containsExactlyInAnyOrder("string_general", "string_pattern_basic");
    }

    @Test
    void mapsTagWithoutSubcategoryAsSingleRowWithNullSubcategory() throws IOException {
        JsonNode noSubcategoryJson = objectMapper.readTree("""
                {
                  "problemId": 1829,
                  "metadataVersion": "1.1.0",
                  "reviewedBy": null,
                  "lastUpdated": "2026-07-23",
                  "source": {
                    "platform": "programmers",
                    "title": "카카오프렌즈 컬러링북",
                    "level": "Lv2",
                    "url": "https://school.programmers.co.kr/learn/courses/30/lessons/1829",
                    "language": "java"
                  },
                  "classification": {
                    "primary": [
                      { "tag": "bfs", "subcategory": [] }
                    ],
                    "difficultyReason": "설명"
                  },
                  "approach": {
                    "recommendedApproach": "설명",
                    "alternativeApproaches": ["DFS"],
                    "expectedTimeComplexity": "O(n)",
                    "expectedSpaceComplexity": "O(n)",
                    "complexityVariables": { "n": "길이" },
                    "keyInsight": "설명"
                  },
                  "solvingSupport": {
                    "keyDataStructures": ["Queue<int[]>"],
                    "implementationCheckpoints": ["1단계", "2단계"],
                    "stuckPointHints": {
                      "방문 처리": "힌트1",
                      "초기값 설정": "힌트2"
                    }
                  },
                  "wrongAnswerDiagnosis": {
                    "commonMistakes": [
                      { "symptom": "시간초과", "likelyCause": "원인", "directionHint": "힌트" }
                    ],
                    "fatalApproachSignals": ["신호1"]
                  },
                  "edgeCases": ["엣지케이스1"],
                  "afterSolve": {
                    "evaluationCriteria": ["기준1"],
                    "optimizationHints": ["힌트1"],
                    "similarProblems": []
                  }
                }
                """);

        ProblemEntity entity = mapper.toEntity(noSubcategoryJson);

        assertThat(entity.getClassifications()).hasSize(1);
        assertThat(entity.getClassifications().get(0).getTag()).isEqualTo("bfs");
        assertThat(entity.getClassifications().get(0).getSubcategory()).isNull();
    }

    @Test
    void mapsStuckPointHintsFromObjectToKeyValueRows() throws IOException {
        ProblemEntity entity = mapper.toEntity(sample());

        assertThat(entity.getStuckPointHints()).hasSize(2);
        assertThat(entity.getStuckPointHints())
                .extracting("pointKey")
                .containsExactlyInAnyOrder("방문 처리", "초기값 설정");
    }

    @Test
    void mapsImplementationCheckpointsWithSequentialOrderIndex() throws IOException {
        ProblemEntity entity = mapper.toEntity(sample());

        assertThat(entity.getImplementationCheckpoints()).hasSize(2);
        assertThat(entity.getImplementationCheckpoints().get(0).getOrderIndex()).isEqualTo(1);
        assertThat(entity.getImplementationCheckpoints().get(1).getOrderIndex()).isEqualTo(2);
    }

    @Test
    void mapsEmptySimilarProblemsToEmptyList() throws IOException {
        ProblemEntity entity = mapper.toEntity(sample());

        assertThat(entity.getSimilarProblems()).isEmpty();
    }

    private JsonNode sample() throws IOException {
        return objectMapper.readTree(sampleJson());
    }

    private String sampleJson() {
        return """
                {
                  "problemId": 1829,
                  "metadataVersion": "1.1.0",
                  "reviewedBy": null,
                  "lastUpdated": "2026-07-23",
                  "source": {
                    "platform": "programmers",
                    "title": "카카오프렌즈 컬러링북",
                    "level": "Lv2",
                    "url": "https://school.programmers.co.kr/learn/courses/30/lessons/1829",
                    "language": "java"
                  },
                  "classification": {
                    "primary": [
                      { "tag": "string", "subcategory": ["string_general", "string_pattern_basic"] }
                    ],
                    "difficultyReason": "설명"
                  },
                  "approach": {
                    "recommendedApproach": "설명",
                    "alternativeApproaches": ["DFS"],
                    "expectedTimeComplexity": "O(n)",
                    "expectedSpaceComplexity": "O(n)",
                    "complexityVariables": { "n": "길이" },
                    "keyInsight": "설명"
                  },
                  "solvingSupport": {
                    "keyDataStructures": ["Queue<int[]>"],
                    "implementationCheckpoints": ["1단계", "2단계"],
                    "stuckPointHints": {
                      "방문 처리": "힌트1",
                      "초기값 설정": "힌트2"
                    }
                  },
                  "wrongAnswerDiagnosis": {
                    "commonMistakes": [
                      { "symptom": "시간초과", "likelyCause": "원인", "directionHint": "힌트" }
                    ],
                    "fatalApproachSignals": ["신호1"]
                  },
                  "edgeCases": ["엣지케이스1"],
                  "afterSolve": {
                    "evaluationCriteria": ["기준1"],
                    "optimizationHints": ["힌트1"],
                    "similarProblems": []
                  }
                }
                """;
    }
}
