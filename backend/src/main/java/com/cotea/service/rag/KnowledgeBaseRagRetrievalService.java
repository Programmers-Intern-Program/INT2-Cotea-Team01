package com.cotea.service.rag;

import com.cotea.config.CoteaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * knowledge_base_docs.json을 태그 정확 매칭으로 조회한다(벡터 검색 아님).
 * 문서당 하나의 RagChunk를 반환하며, 어떤 필드를 노출할지는 field_level_mapping.json(hintLevel 기준)이 결정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cotea.rag", name = "enabled", havingValue = "true")
public class KnowledgeBaseRagRetrievalService implements RagRetrievalService {

    private static final String KNOWLEDGE_BASE_MAPPING_KEY = "knowledge_base";

    private final CoteaProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public List<RagChunk> retrieve(List<String> tags, List<String> subcategories, int hintLevel, String question) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<String> effectiveSubcategories = subcategories == null ? List.of() : subcategories;

        JsonNode docs = readJsonFile(knowledgeBaseDocsPath(), objectMapper.createArrayNode());
        JsonNode fieldLevelMapping = readJsonFile(fieldLevelMappingPath(), objectMapper.createObjectNode())
                .path(KNOWLEDGE_BASE_MAPPING_KEY);

        List<RagChunk> chunks = new ArrayList<>();
        for (JsonNode doc : docs) {
            String category = doc.path("category").asText(null);
            if (category == null || !tags.contains(category)) {
                continue;
            }
            // 문제가 subcategory를 지정하지 않았으면(effectiveSubcategories 비어있음) 이 category의
            // 모든 subcategory 문서를 그대로 포함한다(하위호환). 지정했다면 일치하는 subcategory
            // 문서만 좁힌다 — "general" 자동 포함 같은 건 하지 않고, 필요하면 문제 쪽에서 명시하게 한다.
            String docSubcategory = asNullableText(doc.path("subcategory"));
            if (docSubcategory != null
                    && !effectiveSubcategories.isEmpty()
                    && !effectiveSubcategories.contains(docSubcategory)) {
                continue;
            }
            String content = buildContent(doc, fieldLevelMapping, hintLevel);
            if (content.isBlank()) {
                continue;
            }
            chunks.add(RagChunk.builder()
                    .source("knowledge_base")
                    .chunkId(category)
                    .content(content)
                    .distance(0.0)
                    .build());
        }
        return chunks;
    }

    private String asNullableText(JsonNode node) {
        return (node.isMissingNode() || node.isNull()) ? null : node.asText();
    }

    private String buildContent(JsonNode doc, JsonNode fieldLevelMapping, int hintLevel) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> fieldNames = fieldLevelMapping.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!isLevelIncluded(fieldLevelMapping.path(fieldName).path("related_levels"), hintLevel)) {
                continue;
            }
            String value = doc.path(fieldName).asText("");
            if (value.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(value);
        }
        return sb.toString();
    }

    private boolean isLevelIncluded(JsonNode relatedLevels, int hintLevel) {
        if (!relatedLevels.isArray()) {
            return false;
        }
        for (JsonNode level : relatedLevels) {
            if (level.asInt() == hintLevel) {
                return true;
            }
        }
        return false;
    }

    private Path knowledgeBaseDocsPath() {
        return Path.of(properties.getRag().getDirectory(), "build", "knowledge_base_docs.json");
    }

    private Path fieldLevelMappingPath() {
        return Path.of(properties.getRag().getDirectory(), "config", "field_level_mapping.json");
    }

    private JsonNode readJsonFile(Path path, JsonNode fallback) {
        if (!Files.exists(path)) {
            log.warn("RAG 파일을 찾을 수 없습니다: {}", path);
            return fallback;
        }
        try {
            return objectMapper.readTree(Files.readString(path));
        } catch (IOException e) {
            log.warn("RAG 파일 읽기 실패: {}", path, e);
            return fallback;
        }
    }
}
