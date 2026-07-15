package com.cotea.service.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.service.problem.entity.EdgeCaseEntity;
import com.cotea.service.problem.entity.ProblemEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProblemMetaMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProblemMetaMapper mapper = new ProblemMetaMapper(objectMapper);

    @Test
    void toJson_fillsSourceLanguageAsJava() {
        ProblemEntity problem = sampleProblem();

        JsonNode result = mapper.toJson(problem);

        assertThat(result.path("source").path("language").asText()).isEqualTo("java");
    }

    @Test
    void toJson_movesEdgeCasesToTopLevel() {
        ProblemEntity problem = sampleProblem();
        EdgeCaseEntity edgeCase = new EdgeCaseEntity();
        ReflectionTestUtils.setField(edgeCase, "caseText", "격자가 1x1로 최소 크기인 경우");
        problem.getEdgeCases().add(edgeCase);

        JsonNode result = mapper.toJson(problem);

        assertThat(result.path("edgeCases").isArray()).isTrue();
        assertThat(result.path("edgeCases").get(0).asText()).isEqualTo("격자가 1x1로 최소 크기인 경우");
        assertThat(result.path("afterSolve").has("edgeCases"))
                .as("edgeCases는 afterSolve 하위가 아니라 최상위에만 있어야 한다")
                .isFalse();
    }

    private ProblemEntity sampleProblem() {
        ProblemEntity problem = new ProblemEntity();
        ReflectionTestUtils.setField(problem, "problemId", 1829);
        ReflectionTestUtils.setField(problem, "platform", "programmers");
        ReflectionTestUtils.setField(problem, "title", "카카오프렌즈 컬러링북");
        ReflectionTestUtils.setField(problem, "level", "Lv2");
        ReflectionTestUtils.setField(problem, "url", "https://school.programmers.co.kr/learn/courses/30/lessons/1829");
        return problem;
    }
}
