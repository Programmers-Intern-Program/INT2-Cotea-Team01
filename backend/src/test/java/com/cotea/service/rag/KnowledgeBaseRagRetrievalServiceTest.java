package com.cotea.service.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.cotea.config.CoteaProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeBaseRagRetrievalServiceTest {

    private static final String KNOWLEDGE_BASE_DOCS_JSON = """
            [
              {
                "category": "bfs",
                "subcategory": null,
                "language": "ko",
                "definition": "BFS 정의 문장",
                "when_to_use": "BFS 사용 시점 문장",
                "java_specific_notes": "BFS 자바 팁 문장",
                "applicable_scale": "BFS 적용 규모 문장"
              }
            ]
            """;

    private static final String FIELD_LEVEL_MAPPING_JSON = """
            {
              "knowledge_base": {
                "definition": { "related_levels": [2] },
                "when_to_use": { "related_levels": [2, 3] },
                "java_specific_notes": { "related_levels": [3, 4] },
                "applicable_scale": { "related_levels": [2, 4] }
              }
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KnowledgeBaseRagRetrievalService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("build"));
        Files.createDirectories(tempDir.resolve("config"));
        Files.writeString(tempDir.resolve("build/knowledge_base_docs.json"), KNOWLEDGE_BASE_DOCS_JSON);
        Files.writeString(tempDir.resolve("config/field_level_mapping.json"), FIELD_LEVEL_MAPPING_JSON);

        CoteaProperties properties = new CoteaProperties();
        properties.getRag().setEnabled(true);
        properties.getRag().setDirectory(tempDir.toString());

        service = new KnowledgeBaseRagRetrievalService(properties, objectMapper);
    }

    @Test
    void returnsLevelAppropriateFieldsWhenTagMatches() {
        List<RagChunk> chunks = service.retrieve(List.of("bfs"), List.of(), 2, "질문");

        assertThat(chunks).hasSize(1);
        RagChunk chunk = chunks.get(0);
        assertThat(chunk.getSource()).isEqualTo("knowledge_base");
        assertThat(chunk.getChunkId()).isEqualTo("bfs");
        assertThat(chunk.getContent()).contains("BFS 정의 문장", "BFS 사용 시점 문장", "BFS 적용 규모 문장");
        assertThat(chunk.getContent()).doesNotContain("BFS 자바 팁 문장");
    }

    @Test
    void returnsEmptyListWhenTagDoesNotMatch() {
        List<RagChunk> chunks = service.retrieve(List.of("dfs"), List.of(), 2, "질문");

        assertThat(chunks).isEmpty();
    }

    @Test
    void returnsEmptyListWhenNoFieldIsExposedAtThisLevel() {
        List<RagChunk> chunks = service.retrieve(List.of("bfs"), List.of(), 1, "질문");

        assertThat(chunks).isEmpty();
    }

    @Test
    void returnsEmptyListWhenTagsIsEmpty() {
        List<RagChunk> chunks = service.retrieve(List.of(), List.of(), 2, "질문");

        assertThat(chunks).isEmpty();
    }

    /**
     * subcategory가 있는 category(예: dp)는 문제가 subcategory를 지정하지 않으면
     * 그 category에 속한 문서를 전부 포함한다 — 하위호환 기본값.
     */
    @Test
    void returnsAllSubcategoryDocsWhenProblemDoesNotSpecifySubcategory(@TempDir Path tempDir) throws IOException {
        KnowledgeBaseRagRetrievalService multiService = serviceWithMultiSubcategoryFixture(tempDir);

        List<RagChunk> chunks = multiService.retrieve(List.of("dp"), List.of(), 2, "질문");

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(RagChunk::getContent)
                .anyMatch(content -> content.contains("DP 일반 정의"));
        assertThat(chunks).extracting(RagChunk::getContent)
                .anyMatch(content -> content.contains("DP 냅색 정의"));
    }

    /** 문제가 subcategory를 지정하면, 일치하는 subcategory 문서만 좁혀서 반환한다. */
    @Test
    void returnsOnlyMatchingSubcategoryDocWhenProblemSpecifiesIt(@TempDir Path tempDir) throws IOException {
        KnowledgeBaseRagRetrievalService multiService = serviceWithMultiSubcategoryFixture(tempDir);

        List<RagChunk> chunks = multiService.retrieve(List.of("dp"), List.of("dp_knapsack"), 2, "질문");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).contains("DP 냅색 정의");
    }

    private KnowledgeBaseRagRetrievalService serviceWithMultiSubcategoryFixture(Path tempDir) throws IOException {
        String docsJson = """
                [
                  {
                    "category": "dp",
                    "subcategory": "dp_general",
                    "language": "ko",
                    "definition": "DP 일반 정의",
                    "when_to_use": "DP 일반 사용 시점",
                    "java_specific_notes": "DP 일반 자바 팁",
                    "applicable_scale": "DP 일반 적용 규모"
                  },
                  {
                    "category": "dp",
                    "subcategory": "dp_knapsack",
                    "language": "ko",
                    "definition": "DP 냅색 정의",
                    "when_to_use": "DP 냅색 사용 시점",
                    "java_specific_notes": "DP 냅색 자바 팁",
                    "applicable_scale": "DP 냅색 적용 규모"
                  }
                ]
                """;
        Files.createDirectories(tempDir.resolve("build"));
        Files.createDirectories(tempDir.resolve("config"));
        Files.writeString(tempDir.resolve("build/knowledge_base_docs.json"), docsJson);
        Files.writeString(tempDir.resolve("config/field_level_mapping.json"), FIELD_LEVEL_MAPPING_JSON);

        CoteaProperties properties = new CoteaProperties();
        properties.getRag().setEnabled(true);
        properties.getRag().setDirectory(tempDir.toString());
        return new KnowledgeBaseRagRetrievalService(properties, objectMapper);
    }

    /** often_combined_with는 [{ref, note}] 배열이라, 매칭 레벨에서 사람이 읽을 문장으로 렌더링돼야 한다. */
    @Test
    void rendersOftenCombinedWithAsReadableTextWhenLevelMatches(@TempDir Path tempDir) throws IOException {
        KnowledgeBaseRagRetrievalService multiService = serviceWithOftenCombinedWithFixture(tempDir);

        List<RagChunk> chunks = multiService.retrieve(List.of("bfs"), List.of(), 3, "질문");

        assertThat(chunks).hasSize(1);
        String content = chunks.get(0).getContent();
        assertThat(content).contains("priority_queue", "다익스트라에서 결합", "simulation_grid", "격자 확산 문제에서 결합");
    }

    /** definition은 Lv2에만, often_combined_with는 Lv3에만 노출되므로 Lv2 조회 시 후자는 섞이면 안 된다. */
    @Test
    void excludesOftenCombinedWithWhenLevelDoesNotMatch(@TempDir Path tempDir) throws IOException {
        KnowledgeBaseRagRetrievalService multiService = serviceWithOftenCombinedWithFixture(tempDir);

        List<RagChunk> chunks = multiService.retrieve(List.of("bfs"), List.of(), 2, "질문");

        assertThat(chunks).hasSize(1);
        String content = chunks.get(0).getContent();
        assertThat(content).contains("BFS 정의");
        assertThat(content).doesNotContain("priority_queue", "함께 자주 쓰이는 기법");
    }

    private KnowledgeBaseRagRetrievalService serviceWithOftenCombinedWithFixture(Path tempDir) throws IOException {
        String docsJson = """
                [
                  {
                    "category": "bfs",
                    "subcategory": null,
                    "language": "ko",
                    "definition": "BFS 정의",
                    "often_combined_with": [
                      { "ref": "priority_queue", "note": "다익스트라에서 결합" },
                      { "ref": "simulation_grid", "note": "격자 확산 문제에서 결합" }
                    ]
                  }
                ]
                """;
        String mappingJson = """
                {
                  "knowledge_base": {
                    "definition": { "related_levels": [2] },
                    "often_combined_with": { "related_levels": [3] }
                  }
                }
                """;
        Files.createDirectories(tempDir.resolve("build"));
        Files.createDirectories(tempDir.resolve("config"));
        Files.writeString(tempDir.resolve("build/knowledge_base_docs.json"), docsJson);
        Files.writeString(tempDir.resolve("config/field_level_mapping.json"), mappingJson);

        CoteaProperties properties = new CoteaProperties();
        properties.getRag().setEnabled(true);
        properties.getRag().setDirectory(tempDir.toString());
        return new KnowledgeBaseRagRetrievalService(properties, objectMapper);
    }

    /**
     * 테스트 픽스처가 아니라 실제 rag/ 폴더 데이터를 읽어서 눈으로 확인하기 위한 테스트.
     * ./gradlew test --tests "...KnowledgeBaseRagRetrievalServiceTest" --info 로 실행하면 콘솔에서 내용을 볼 수 있다.
     */
    @Test
    void manualCheck_realKnowledgeBaseDataForBfsTag() {
        CoteaProperties properties = new CoteaProperties();
        properties.getRag().setEnabled(true);
        properties.getRag().setDirectory("../rag");
        KnowledgeBaseRagRetrievalService realService = new KnowledgeBaseRagRetrievalService(properties, objectMapper);

        List<RagChunk> chunks = realService.retrieve(List.of("bfs"), List.of(), 2, "이 문제는 어떻게 접근해야 하나요?");

        chunks.forEach(chunk ->
                System.out.println("[" + chunk.getSource() + "/" + chunk.getChunkId() + "] " + chunk.getContent()));

        assertThat(chunks).isNotEmpty();
    }
}
