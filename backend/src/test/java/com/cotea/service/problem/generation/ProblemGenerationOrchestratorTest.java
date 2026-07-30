package com.cotea.service.problem.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotea.client.LlmClient;
import com.cotea.service.problem.ProblemMetaRepository;
import com.cotea.service.problem.entity.ProblemEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProblemGenerationOrchestratorTest {

    @Mock private ProblemMetaRepository problemMetaRepository;
    @Mock private ProblemGenerationLockManager lockManager;
    @Mock private ProblemPageFetcher pageFetcher;
    @Mock private ProblemHtmlParser htmlParser;
    @Mock private LlmClient llmClient;
    @Mock private ProblemGenerationValidator validator;
    @Mock private GeneratedProblemMapper mapper;

    private ProblemGenerationOrchestrator orchestrator;
    /** ensureReady()가 생성 작업을 여기로 던지기만 하고 바로 리턴하므로, 완료를 검증하려는
     * 테스트는 {@link #awaitGeneration()}으로 작업이 끝날 때까지 기다린 뒤 verify해야 한다. */
    private ExecutorService generationExecutor;

    @BeforeEach
    void setUp() {
        generationExecutor = Executors.newSingleThreadExecutor();
        orchestrator = new ProblemGenerationOrchestrator(
                problemMetaRepository, lockManager, pageFetcher, htmlParser,
                llmClient, validator, mapper, new ObjectMapper(), generationExecutor);
    }

    private void awaitGeneration() throws InterruptedException {
        generationExecutor.shutdown();
        generationExecutor.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void doesNothingWhenProblemAlreadyExists() {
        when(problemMetaRepository.existsById(1829)).thenReturn(true);

        orchestrator.ensureReady(1829);

        verify(lockManager, never()).tryAcquire(anyInt());
        verify(pageFetcher, never()).fetchHtml(anyInt());
    }

    @Test
    void doesNothingWhenLockNotAcquired() {
        when(problemMetaRepository.existsById(1829)).thenReturn(false);
        when(lockManager.tryAcquire(1829)).thenReturn(false);

        orchestrator.ensureReady(1829);

        verify(pageFetcher, never()).fetchHtml(anyInt());
        verify(lockManager, never()).release(anyInt());
    }

    @Test
    void generatesAndSavesOnSuccess() throws InterruptedException {
        when(problemMetaRepository.existsById(1829)).thenReturn(false);
        when(lockManager.tryAcquire(1829)).thenReturn(true);
        when(pageFetcher.fetchHtml(1829)).thenReturn("<html></html>");
        when(htmlParser.parse("<html></html>")).thenReturn(textOnlyParsedProblem());
        when(llmClient.generateWithImages(anyString(), anyString(), eq(List.of()))).thenReturn("{}");
        when(validator.validate(any(), eq(1829))).thenReturn(new ValidationResult(true, List.of()));
        ProblemEntity entity = ProblemEntity.builder().problemId(1829).build();
        when(mapper.toEntity(any())).thenReturn(entity);

        orchestrator.ensureReady(1829);
        awaitGeneration();

        verify(problemMetaRepository).save(entity);
        verify(lockManager).release(1829);
    }

    @Test
    void retriesOnceOnValidationFailureThenSucceeds() throws InterruptedException {
        when(problemMetaRepository.existsById(1829)).thenReturn(false);
        when(lockManager.tryAcquire(1829)).thenReturn(true);
        when(pageFetcher.fetchHtml(1829)).thenReturn("<html></html>");
        when(htmlParser.parse("<html></html>")).thenReturn(textOnlyParsedProblem());
        when(llmClient.generateWithImages(anyString(), anyString(), eq(List.of()))).thenReturn("{}");
        when(validator.validate(any(), eq(1829)))
                .thenReturn(new ValidationResult(false, List.of("스키마 위반: 예시")))
                .thenReturn(new ValidationResult(true, List.of()));
        when(mapper.toEntity(any())).thenReturn(ProblemEntity.builder().problemId(1829).build());

        orchestrator.ensureReady(1829);
        awaitGeneration();

        verify(llmClient, times(2)).generateWithImages(anyString(), anyString(), eq(List.of()));
        verify(problemMetaRepository, times(1)).save(any());
        verify(lockManager).release(1829);
    }

    @Test
    void givesUpAfterMaxAttemptsButStillReleasesLock() throws InterruptedException {
        when(problemMetaRepository.existsById(1829)).thenReturn(false);
        when(lockManager.tryAcquire(1829)).thenReturn(true);
        when(pageFetcher.fetchHtml(1829)).thenReturn("<html></html>");
        when(htmlParser.parse("<html></html>")).thenReturn(textOnlyParsedProblem());
        when(llmClient.generateWithImages(anyString(), anyString(), eq(List.of()))).thenReturn("{}");
        when(validator.validate(any(), eq(1829))).thenReturn(new ValidationResult(false, List.of("계속 실패")));

        orchestrator.ensureReady(1829);
        awaitGeneration();

        verify(problemMetaRepository, never()).save(any());
        verify(lockManager).release(1829);
    }

    @Test
    void passesImageUrlsThroughWhenProblemHasImages() throws InterruptedException {
        when(problemMetaRepository.existsById(1829)).thenReturn(false);
        when(lockManager.tryAcquire(1829)).thenReturn(true);
        when(pageFetcher.fetchHtml(1829)).thenReturn("<html></html>");
        when(htmlParser.parse("<html></html>")).thenReturn(new ParsedProblem(
                1829, "제목", "Lv2", "https://example.com/1829",
                "본문", "<p>본문</p>", List.of("https://example.com/image.png")));
        when(llmClient.generateWithImages(anyString(), anyString(), eq(List.of("https://example.com/image.png"))))
                .thenReturn("{}");
        when(validator.validate(any(), eq(1829))).thenReturn(new ValidationResult(true, List.of()));
        when(mapper.toEntity(any())).thenReturn(ProblemEntity.builder().problemId(1829).build());

        orchestrator.ensureReady(1829);
        awaitGeneration();

        verify(llmClient).generateWithImages(anyString(), anyString(), eq(List.of("https://example.com/image.png")));
    }

    @Test
    void statusIsReadyWhenProblemExists() {
        when(problemMetaRepository.existsById(1829)).thenReturn(true);

        assertThat(orchestrator.getStatus(1829)).isEqualTo(ProblemReadyStatus.READY);
    }

    @Test
    void statusIsGeneratingWhenLockInProgress() {
        when(problemMetaRepository.existsById(1829)).thenReturn(false);
        when(lockManager.isInProgress(1829)).thenReturn(true);

        assertThat(orchestrator.getStatus(1829)).isEqualTo(ProblemReadyStatus.GENERATING);
    }

    @Test
    void statusIsNotStartedWhenNeitherExistsNorLocked() {
        when(problemMetaRepository.existsById(1829)).thenReturn(false);
        when(lockManager.isInProgress(1829)).thenReturn(false);

        assertThat(orchestrator.getStatus(1829)).isEqualTo(ProblemReadyStatus.NOT_STARTED);
    }

    private ParsedProblem textOnlyParsedProblem() {
        return new ParsedProblem(1829, "제목", "Lv2", "https://example.com/1829", "본문", "<p>본문</p>", List.of());
    }
}
