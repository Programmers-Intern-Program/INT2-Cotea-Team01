package com.cotea.service.problem.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ProblemGenerationValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProblemGenerationValidator validator = new ProblemGenerationValidator();

    @Test
    void acceptsWellFormedGeneratedProblem() throws IOException {
        JsonNode generated = validProblem();

        ValidationResult result = validator.validate(generated, 1829);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsMismatchedProblemId() throws IOException {
        JsonNode generated = validProblem();

        ValidationResult result = validator.validate(generated, 9999);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("problemId"));
    }

    @Test
    void rejectsSubcategoryNotAllowedForTag() throws IOException {
        JsonNode generated = objectMapper.readTree(baseJson().replace(
                "\"tag\": \"bfs\", \"subcategory\": []",
                "\"tag\": \"bfs\", \"subcategory\": [\"dp_general\"]"));

        ValidationResult result = validator.validate(generated, 1829);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("subcategory"));
    }

    @Test
    void rejectsLevel1ForbiddenTermInKeyInsight() throws IOException {
        JsonNode generated = objectMapper.readTree(baseJson().replace(
                "값이 0인 칸은 애초에 영역으로 세지 않아야 하고, 같은 값이라도 상하좌우로 이어져 있지 않으면 서로 다른 영역이라는 점을 생각해보세요.",
                "BFS로 탐색하면서 값이 0인 칸은 세지 않아야 합니다."));

        ValidationResult result = validator.validate(generated, 1829);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("Lv1 금지어"));
    }

    @Test
    void rejectsSolutionCodeLeak() throws IOException {
        JsonNode generated = objectMapper.readTree(baseJson().replace(
                "이미 확인한 칸을 어떻게 표시할지 생각해보세요.",
                "class Solution { public int[] solution(int[][] p) { return null; } }"));

        ValidationResult result = validator.validate(generated, 1829);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("완성 코드"));
    }

    @Test
    void rejectsWrongSymptomVocabulary() throws IOException {
        JsonNode generated = objectMapper.readTree(baseJson().replace("\"시간초과\"", "\"시간 초과\""));

        ValidationResult result = validator.validate(generated, 1829);

        assertThat(result.valid()).isFalse();
    }

    private JsonNode validProblem() throws IOException {
        return objectMapper.readTree(baseJson());
    }

    private String baseJson() {
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
                      { "tag": "bfs", "subcategory": [] }
                    ],
                    "difficultyReason": "0인 칸은 영역에서 제외해야 하고, 값이 같아도 상하좌우로 이어져 있지 않으면 서로 다른 영역으로 구분해야 한다"
                  },
                  "approach": {
                    "recommendedApproach": "상하좌우로 연결된 같은 값의 칸만 하나의 영역으로 봐야 하므로, 한 칸에서 시작해 인접한 칸으로 넓게 탐색을 넓혀가는 BFS가 적합한 방향",
                    "alternativeApproaches": ["DFS(재귀 또는 스택)로 동일하게 구현", "Union-Find로 연결된 칸들을 묶어 관리"],
                    "expectedTimeComplexity": "O(m*n)",
                    "expectedSpaceComplexity": "O(m*n)",
                    "complexityVariables": {
                      "m": "그림의 세로 크기(행 개수)",
                      "n": "그림의 가로 크기(열 개수)"
                    },
                    "keyInsight": "값이 0인 칸은 애초에 영역으로 세지 않아야 하고, 같은 값이라도 상하좌우로 이어져 있지 않으면 서로 다른 영역이라는 점을 생각해보세요."
                  },
                  "solvingSupport": {
                    "keyDataStructures": ["Queue<int[]>", "boolean[][] 방문 배열"],
                    "implementationCheckpoints": [
                      "값이 0인 칸을 탐색 대상에서 제외했는가",
                      "아직 방문하지 않은 칸을 찾을 때마다 새 영역 탐색을 시작하는가",
                      "같은 값을 가진 상하좌우 인접 칸만 같은 영역으로 묶는가"
                    ],
                    "stuckPointHints": {
                      "방문 처리": "이미 확인한 칸을 어떻게 표시할지 생각해보세요.",
                      "초기값 설정": "영역 개수와 최대 넓이를 세기 시작하는 초기값을 어떻게 둘지 생각해보세요."
                    }
                  },
                  "wrongAnswerDiagnosis": {
                    "commonMistakes": [
                      {
                        "symptom": "시간초과",
                        "likelyCause": "칸을 방문 처리하지 않고 같은 영역을 반복해서 다시 탐색하는 방식일 수 있다",
                        "directionHint": "한 번 확인한 칸을 다시 탐색 대상으로 삼고 있지는 않은지 확인해보세요."
                      }
                    ],
                    "fatalApproachSignals": [
                      "값만 같으면 서로 떨어져 있어도 같은 영역으로 묶는 접근"
                    ]
                  },
                  "edgeCases": [
                    "그림 전체가 0으로만 이루어져 영역이 하나도 없는 경우"
                  ],
                  "afterSolve": {
                    "evaluationCriteria": ["0인 칸을 올바르게 제외했는가"],
                    "optimizationHints": ["방문 배열 대신 원본 배열 값을 0으로 바꿔가며 방문 처리해 공간을 아낄 수 있는지 검토"],
                    "similarProblems": []
                  }
                }
                """;
    }
}
