package com.cotea.service.problem.generation;

import com.cotea.client.LlmClient;
import com.cotea.service.problem.ProblemMetaRepository;
import com.cotea.service.problem.entity.ProblemEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * "문제 입장" 시점에 문제 데이터가 없으면 생성한다(docs/problem-data-generation-prompt.md).
 * {@link ProblemGenerationLockManager}로 같은 problemId에 대한 동시 생성을 막고, 락을 획득한
 * 요청만 실제로 HTML 조회 → 파싱 → LLM 생성 → 검증 → DB 저장을 수행한다. 검증 실패 시 실패 사유를
 * 프롬프트에 첨부해 1회 재시도하고(§5 재시도 정책), 그래도 실패하면 락만 풀고 종료한다 — 다음
 * "문제 입장" 요청이 처음부터 다시 시도할 수 있게 한다.
 *
 * <p>프론트엔드는 이 서비스 호출 결과를 기다리지 않는(fire-and-forget) 것을 전제로 설계됐다 —
 * 그래서 이 클래스 어디에도 클라이언트 타임아웃을 고려한 코드가 없다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemGenerationOrchestrator {

    private static final String SYSTEM_PROMPT_RESOURCE = "prompts/problem-generation-system-prompt.txt";
    private static final String SCHEMA_RESOURCE = "schema/problem-generation-schema.json";
    private static final int MAX_ATTEMPTS = 2;

    private final ProblemMetaRepository problemMetaRepository;
    private final ProblemGenerationLockManager lockManager;
    private final ProblemPageFetcher pageFetcher;
    private final ProblemHtmlParser htmlParser;
    private final LlmClient llmClient;
    private final ProblemGenerationValidator validator;
    private final GeneratedProblemMapper mapper;
    private final ObjectMapper objectMapper;

    public void ensureReady(int problemId) {
        if (problemMetaRepository.existsById(problemId)) {
            return;
        }
        if (!lockManager.tryAcquire(problemId)) {
            log.info("problemId={} 이미 다른 요청이 생성 중", problemId);
            return;
        }
        try {
            generateWithRetry(problemId);
        } finally {
            lockManager.release(problemId);
        }
    }

    private void generateWithRetry(int problemId) {
        List<String> lastErrors = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                JsonNode generated = generateOnce(problemId, lastErrors);
                ValidationResult result = validator.validate(generated, problemId);
                if (result.valid()) {
                    ProblemEntity entity = mapper.toEntity(generated);
                    problemMetaRepository.save(entity);
                    log.info("problemId={} 생성 성공 (attempt={})", problemId, attempt);
                    return;
                }
                log.warn("problemId={} 검증 실패 (attempt={}): {}", problemId, attempt, result.errors());
                lastErrors = result.errors();
            } catch (Exception e) {
                log.warn("problemId={} 생성 시도 실패 (attempt={}): {}", problemId, attempt, e.getMessage());
                lastErrors = List.of(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        log.error("problemId={} {}회 시도 모두 실패, 생성 포기: {}", problemId, MAX_ATTEMPTS, lastErrors);
    }

    private JsonNode generateOnce(int problemId, List<String> previousErrors) throws IOException {
        String html = pageFetcher.fetchHtml(problemId);
        ParsedProblem parsed = htmlParser.parse(html);

        String systemPrompt = loadResource(SYSTEM_PROMPT_RESOURCE) + "\n\n" + loadResource(SCHEMA_RESOURCE);
        String userMessage = buildUserMessage(parsed, previousErrors);

        // generateWithImages()는 이미지가 없어도 동작한다 - 문제 생성 응답은 항상 이 경로로 보내서
        // 힌트 응답용 max_tokens(1024, ANTHROPIC_MAX_TOKENS)가 아니라 문제 생성 전용 max_tokens
        // (problem-generation-max-tokens)를 쓰게 한다. 스키마 전체를 채운 JSON은 1024로는 잘린다.
        String rawResponse = llmClient.generateWithImages(systemPrompt, userMessage, parsed.imageUrls());

        return objectMapper.readTree(extractJson(rawResponse));
    }

    private String buildUserMessage(ParsedProblem parsed, List<String> previousErrors) {
        StringBuilder message = new StringBuilder();
        message.append("고정값(그대로 복사):\n");
        message.append("problemId: ").append(parsed.problemId()).append('\n');
        message.append("source.title: ").append(parsed.title()).append('\n');
        message.append("source.level: ").append(parsed.level()).append('\n');
        message.append("source.url: ").append(parsed.url()).append('\n');
        message.append("metadataVersion: 1.1.0\n");
        message.append("lastUpdated: ").append(LocalDate.now()).append('\n');
        message.append("\n문제 원문:\n").append(parsed.bodyText());

        if (!previousErrors.isEmpty()) {
            message.append("\n\n이전 시도가 아래 항목을 위반했습니다. 수정해서 다시 출력하세요:\n");
            for (String error : previousErrors) {
                message.append("- ").append(error).append('\n');
            }
        }
        return message.toString();
    }

    /** LLM이 "출력 규칙"을 어기고 JSON 앞뒤로 설명 텍스트를 덧붙이는 경우를 대비해 첫 '{'부터 마지막 '}'까지만 취한다. */
    private String extractJson(String rawResponse) {
        int start = rawResponse.indexOf('{');
        int end = rawResponse.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            return rawResponse;
        }
        return rawResponse.substring(start, end + 1);
    }

    private String loadResource(String location) throws IOException {
        return StreamUtils.copyToString(new ClassPathResource(location).getInputStream(), StandardCharsets.UTF_8);
    }
}
