package com.cotea.service.problem.generation;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여러 유저가 동시에 같은 문제를 처음 풀려고 해도, 문제 데이터 생성(HTML 조회 → LLM 생성 →
 * 검증 → DB 저장)이 딱 한 번만 실행되도록 보장한다. DB 유니크 제약(problem_generation_lock의
 * problem_id PK)을 뮤텍스로 쓴다 — Redis 같은 별도 분산 락 인프라 없이 이미 있는 MySQL로 충분하다.
 *
 * <p>사용 흐름(호출부는 별도 오케스트레이션 서비스에서 구현):
 * <pre>
 * if (lockManager.tryAcquire(problemId)) {
 *     try {
 *         // HTML 조회 → 파싱 → LLM 생성 → 검증 → problem 테이블에 저장
 *     } finally {
 *         lockManager.release(problemId);
 *     }
 * } else {
 *     // 다른 요청이 이미 생성 중 — "잠시 후 다시 시도해주세요" 류의 응답
 * }
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProblemGenerationLockManager {

    /** 이 시간보다 오래된 락은 생성 프로세스가 죽어서 안 지워진 것(stale)으로 보고 재사용을 허용한다. */
    private static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final ProblemGenerationLockRepository lockRepository;

    @Transactional
    public boolean tryAcquire(int problemId) {
        reclaimIfStale(problemId);
        try {
            lockRepository.insertLock(problemId, LocalDateTime.now());
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("problemId={} 생성 락 획득 실패 — 다른 요청이 이미 생성 중", problemId);
            return false;
        }
    }

    @Transactional
    public void release(int problemId) {
        lockRepository.deleteById(problemId);
    }

    private void reclaimIfStale(int problemId) {
        lockRepository.findById(problemId).ifPresent(lock -> {
            if (lock.getStartedAt().isBefore(LocalDateTime.now().minus(STALE_AFTER))) {
                log.warn("problemId={} 락이 {} 이상 지속돼 stale로 판단, 재사용 허용", problemId, STALE_AFTER);
                lockRepository.deleteById(problemId);
            }
        });
    }
}
