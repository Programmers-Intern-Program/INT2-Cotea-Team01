package com.cotea.service.problem.generation;

import com.cotea.service.hint.HintAnswerGuardrail;
import com.cotea.service.hint.KoreanBoundaryMatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * LLM이 생성한 문제 메타데이터 JSON을 docs/problem-data-authoring-rules.md §8 체크리스트에 따라
 * 검증한다. 구조 검증(필수 필드/타입/enum)은 networknt json-schema-validator가 담당하고, 스키마로는
 * 표현할 수 없는 검사(tag별 subcategory 교차검증, Lv1 금지어, 코드 유출 휴리스틱)는 여기서 수행한다.
 *
 * <p>Lv1 금지어 목록({@link HintAnswerGuardrail#LEVEL_1_FORBIDDEN_TERMS})과 한국어 조사 경계 판정
 * ({@link KoreanBoundaryMatcher})은 힌트 응답 가드레일에 이미 있는 로직을 그대로 재사용한다 —
 * 문제 데이터도 결국 같은 힌트 프롬프트에 실리므로 같은 기준으로 걸러야 한다.
 */
@Component
public class ProblemGenerationValidator {

    private static final Map<String, Set<String>> CONTROLLED_SUBCATEGORIES = Map.of(
            "array", Set.of("array_general", "array_sorted_pattern"),
            "string", Set.of("string_general", "string_pattern_basic"),
            "hash_set", Set.of("hash_set_general", "hash_set_frequency"),
            "trees", Set.of("trees_general", "trees_bst"),
            "graph_traversal", Set.of("graph_general", "graph_connectivity"),
            "greedy", Set.of("greedy_general", "greedy_sort_based"),
            "simulation", Set.of("simulation_general", "simulation_grid"),
            "dp", Set.of("dp_general", "dp_subsequence", "dp_knapsack", "dp_path_counting")
    );

    /** HintAnswerGuardrail.SOLUTION_CODE와 동일한 완성 코드 탐지 휴리스틱(그 필드는 private이라 재사용 대신 동일 정의). */
    private static final Pattern SOLUTION_CODE_LEAK = Pattern.compile(
            "(class\\s+Solution|public\\s+\\w+(\\[\\])?\\s+solution\\s*\\(|public\\s+static\\s+void\\s+main\\s*\\()"
    );

    private final JsonSchema jsonSchema;

    public ProblemGenerationValidator() {
        this.jsonSchema = loadSchema();
    }

    private JsonSchema loadSchema() {
        try (InputStream inputStream = new ClassPathResource("schema/problem-generation-schema.json").getInputStream()) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("problem-generation-schema.json을 읽을 수 없습니다.", e);
        }
    }

    public ValidationResult validate(JsonNode generated, int expectedProblemId) {
        List<String> errors = new ArrayList<>();

        for (ValidationMessage message : jsonSchema.validate(generated)) {
            errors.add("스키마 위반: " + message.getMessage());
        }

        if (generated.path("problemId").asInt(-1) != expectedProblemId) {
            errors.add("problemId가 요청한 problemId(" + expectedProblemId + ")와 다릅니다: "
                    + generated.path("problemId").asText());
        }

        validateSubcategories(generated, errors);
        checkForbiddenTerms(generated.path("approach").path("keyInsight").asText(""), "approach.keyInsight", errors);
        checkForbiddenTerms(
                generated.path("classification").path("difficultyReason").asText(""),
                "classification.difficultyReason", errors);
        collectStringLeaves(generated, "", errors);

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private void validateSubcategories(JsonNode generated, List<String> errors) {
        JsonNode primary = generated.path("classification").path("primary");
        if (!primary.isArray()) {
            return;
        }
        for (JsonNode item : primary) {
            String tag = item.path("tag").asText("");
            List<String> subcategories = new ArrayList<>();
            item.path("subcategory").forEach(node -> subcategories.add(node.asText("")));

            Set<String> allowed = CONTROLLED_SUBCATEGORIES.get(tag);
            if (allowed == null) {
                if (!subcategories.isEmpty()) {
                    errors.add("tag '" + tag + "'는 subcategory가 없는 카테고리인데 " + subcategories + "가 지정됨 (빈 배열이어야 함)");
                }
            } else {
                for (String subcategory : subcategories) {
                    if (!allowed.contains(subcategory)) {
                        errors.add("tag '" + tag + "'에 허용되지 않은 subcategory: '" + subcategory + "' (허용값: " + allowed + ")");
                    }
                }
            }
        }
    }

    private void checkForbiddenTerms(String text, String fieldName, List<String> errors) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String term : HintAnswerGuardrail.LEVEL_1_FORBIDDEN_TERMS) {
            if (KoreanBoundaryMatcher.containsAsStandaloneTerm(text, term)) {
                errors.add(fieldName + "에 Lv1 금지어 '" + term + "'가 포함되어 있습니다.");
            }
        }
    }

    private void collectStringLeaves(JsonNode node, String path, List<String> errors) {
        if (node.isTextual()) {
            if (SOLUTION_CODE_LEAK.matcher(node.asText()).find()) {
                errors.add((path.isEmpty() ? "(root)" : path) + "에 완성 코드로 의심되는 텍스트가 포함되어 있습니다.");
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String childPath = path.isEmpty() ? entry.getKey() : path + "." + entry.getKey();
                collectStringLeaves(entry.getValue(), childPath, errors);
            }
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectStringLeaves(node.get(i), path + "[" + i + "]", errors);
            }
        }
    }
}
