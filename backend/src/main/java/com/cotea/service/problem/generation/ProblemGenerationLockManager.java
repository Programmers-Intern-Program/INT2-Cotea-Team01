package com.cotea.service.problem.generation;

import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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

    // insertLockInNewTransaction()을 REQUIRES_NEW로 격리된 별도 트랜잭션에서 실행하려면
    // Spring 프록시를 거쳐서 호출해야 한다(같은 인스턴스 안에서 this.insertLockInNewTransaction(...)
    // 으로 직접 부르면 AOP가 안 걸려서 @Transactional 자체가 무시된다) - 그래서 자기 자신을
    // 주입받는다. @Lazy는 빈 생성 시점의 순환 참조를 피하기 위함.
    @Lazy
    @Autowired
    // 테스트(ProblemGenerationLockManagerTest)가 같은 패키지에서 self를 직접 주입해야 해서
    // private이 아니라 package-private으로 둔다.
    ProblemGenerationLockManager self;

    // insertLock()이 유니크 제약 위반으로 실패하는 건 "이미 다른 요청이 생성 중"이라는
    // 정상적인 신호다. 처음엔 이걸 트랜잭션 "안에서" 잡아서 false로 흡수하려 했는데, 그렇게
    // 하면 안 된다는 걸 확인했다: DataIntegrityViolationException이 한 번이라도 트랜잭션
    // 안에서 발생하면(REQUIRES_NEW로 격리된 트랜잭션이어도 마찬가지) Hibernate가 그 트랜잭션을
    // 커밋 불가 상태로 표시해버려서, 자바 코드가 예외를 잡고 정상 리턴해도 실제 커밋 시점에
    // Spring이 UnexpectedRollbackException을 던진다("코드는 성공이라는데 트랜잭션은 실패"라는
    // 모순 상태라서). 그래서 여기서는 반대로 한다 - insertLockInNewTransaction()은 예외를
    // 잡지 않고 그대로 던져서(@Transactional 메서드가 예외로 끝나면 정상적인 예상된 롤백)
    // 트랜잭션 경계 "밖"인 여기서 잡는다. 이게 POST /ensure-ready가 "이미 생성 중"인 지극히
    // 정상적인 경우마다 500을 반환했던 실제 원인이었다.
    public boolean tryAcquire(int problemId) {
        reclaimIfStale(problemId);
        try {
            self.insertLockInNewTransaction(problemId);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("problemId={} 생성 락 획득 실패 — 다른 요청이 이미 생성 중", problemId);
            return false;
        }
    }

    // REQUIRES_NEW: tryAcquire()엔 활성 트랜잭션이 없어서 지금은 굳이 필요하진 않지만,
    // 나중에 누가 tryAcquire()를 다른 @Transactional 메서드 안에서 호출하게 되더라도
    // 이 삽입 시도가 바깥 트랜잭션에 절대 얽히지 않도록 명시적으로 격리해둔다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertLockInNewTransaction(int problemId) {
        lockRepository.insertLock(problemId, LocalDateTime.now());
    }

    @Transactional
    public void release(int problemId) {
        lockRepository.deleteById(problemId);
    }

    /** GET /status에서 "지금 생성 중인지" 판단할 때 쓴다. */
    @Transactional(readOnly = true)
    public boolean isInProgress(int problemId) {
        return lockRepository.findById(problemId).isPresent();
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
