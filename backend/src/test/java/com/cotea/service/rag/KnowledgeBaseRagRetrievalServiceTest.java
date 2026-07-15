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
        List<RagChunk> chunks = service.retrieve(List.of("bfs"), 2, "질문");

        assertThat(chunks).hasSize(1);
        RagChunk chunk = chunks.get(0);
        assertThat(chunk.getSource()).isEqualTo("knowledge_base");
        assertThat(chunk.getChunkId()).isEqualTo("bfs");
        assertThat(chunk.getContent()).contains("BFS 정의 문장", "BFS 사용 시점 문장", "BFS 적용 규모 문장");
        assertThat(chunk.getContent()).doesNotContain("BFS 자바 팁 문장");
    }

    @Test
    void returnsEmptyListWhenTagDoesNotMatch() {
        List<RagChunk> chunks = service.retrieve(List.of("dfs"), 2, "질문");

        assertThat(chunks).isEmpty();
    }

    @Test
    void returnsEmptyListWhenNoFieldIsExposedAtThisLevel() {
        List<RagChunk> chunks = service.retrieve(List.of("bfs"), 1, "질문");

        assertThat(chunks).isEmpty();
    }

    @Test
    void returnsEmptyListWhenTagsIsEmpty() {
        List<RagChunk> chunks = service.retrieve(List.of(), 2, "질문");

        assertThat(chunks).isEmpty();
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

        List<RagChunk> chunks = realService.retrieve(List.of("bfs"), 2, "이 문제는 어떻게 접근해야 하나요?");

        chunks.forEach(chunk ->
                System.out.println("[" + chunk.getSource() + "/" + chunk.getChunkId() + "] " + chunk.getContent()));

        assertThat(chunks).isNotEmpty();
    }
}
