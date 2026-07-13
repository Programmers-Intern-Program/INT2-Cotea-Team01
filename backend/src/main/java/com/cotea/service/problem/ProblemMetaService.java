package com.cotea.service.problem;

import com.cotea.config.CoteaProperties;
import com.cotea.exception.CoteaException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProblemMetaService {

    private final CoteaProperties properties;
    private final ObjectMapper objectMapper;
    private final ProblemMetaRepository problemMetaRepository;
    private final ProblemMetaMapper problemMetaMapper;

    @Transactional(readOnly = true)
    public JsonNode load(int problemId) {
        return problemMetaRepository.findById(problemId)
                .map(problemMetaMapper::toJson)
                .orElseGet(() -> loadFromFile(problemId));
    }

    private JsonNode loadFromFile(int problemId) {
        Path path = Path.of(properties.getProblemMeta().getDirectory(), problemId + ".json");
        if (!Files.exists(path)) {
            throw new CoteaException(
                    "MISSING_PROBLEM_ID",
                    "문제 메타데이터를 찾을 수 없습니다: " + problemId,
                    404
            );
        }
        try {
            return objectMapper.readTree(Files.readString(path));
        } catch (IOException e) {
            throw new CoteaException("AI_SERVICE_ERROR", "문제 메타데이터 로드 실패", 500);
        }
    }
}
