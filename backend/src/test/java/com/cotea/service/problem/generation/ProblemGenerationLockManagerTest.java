package com.cotea.service.problem.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ProblemGenerationLockManagerTest {

    @Mock
    private ProblemGenerationLockRepository lockRepository;

    @Test
    void acquiresLockWhenNoExistingRow() {
        when(lockRepository.findById(1829)).thenReturn(Optional.empty());
        ProblemGenerationLockManager manager = newManager();

        boolean acquired = manager.tryAcquire(1829);

        assertThat(acquired).isTrue();
        verify(lockRepository).insertLock(eq(1829), any(LocalDateTime.class));
    }

    @Test
    void failsToAcquireWhenAnotherRequestAlreadyHoldsFreshLock() {
        when(lockRepository.findById(1829)).thenReturn(Optional.of(lockOf(1829, LocalDateTime.now())));
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(lockRepository).insertLock(eq(1829), any(LocalDateTime.class));
        ProblemGenerationLockManager manager = newManager();

        boolean acquired = manager.tryAcquire(1829);

        assertThat(acquired).isFalse();
        verify(lockRepository, org.mockito.Mockito.never()).deleteById(1829);
    }

    @Test
    void reclaimsStaleLockBeforeAcquiring() {
        when(lockRepository.findById(1829))
                .thenReturn(Optional.of(lockOf(1829, LocalDateTime.now().minusMinutes(10))));
        ProblemGenerationLockManager manager = newManager();

        boolean acquired = manager.tryAcquire(1829);

        assertThat(acquired).isTrue();
        verify(lockRepository).deleteById(1829);
        verify(lockRepository).insertLock(eq(1829), any(LocalDateTime.class));
    }

    @Test
    void releaseDeletesLockRow() {
        ProblemGenerationLockManager manager = new ProblemGenerationLockManager(lockRepository);

        manager.release(1829);

        verify(lockRepository).deleteById(1829);
        verifyNoMoreInteractions(lockRepository);
    }

    private ProblemGenerationLockEntity lockOf(int problemId, LocalDateTime startedAt) {
        return new ProblemGenerationLockEntity(problemId, startedAt);
    }

    // tryAcquire()는 REQUIRES_NEW 트랜잭션 격리를 위해 insertLockInNewTransaction()을
    // self(스프링 프록시)를 거쳐 호출한다. 실제 스프링 컨테이너 밖의 단위 테스트에선 그
    // 자기 자신 주입이 안 되니, 여기서 수동으로 채워서 tryAcquire()가 동작할 수 있게 한다.
    private ProblemGenerationLockManager newManager() {
        ProblemGenerationLockManager manager = new ProblemGenerationLockManager(lockRepository);
        manager.self = manager;
        return manager;
    }
}
