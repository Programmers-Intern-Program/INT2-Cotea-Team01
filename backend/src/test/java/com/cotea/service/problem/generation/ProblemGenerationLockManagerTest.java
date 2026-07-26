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
        ProblemGenerationLockManager manager = new ProblemGenerationLockManager(lockRepository);

        boolean acquired = manager.tryAcquire(1829);

        assertThat(acquired).isTrue();
        verify(lockRepository).insertLock(eq(1829), any(LocalDateTime.class));
    }

    @Test
    void failsToAcquireWhenAnotherRequestAlreadyHoldsFreshLock() {
        when(lockRepository.findById(1829)).thenReturn(Optional.of(lockOf(1829, LocalDateTime.now())));
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(lockRepository).insertLock(eq(1829), any(LocalDateTime.class));
        ProblemGenerationLockManager manager = new ProblemGenerationLockManager(lockRepository);

        boolean acquired = manager.tryAcquire(1829);

        assertThat(acquired).isFalse();
        verify(lockRepository, org.mockito.Mockito.never()).deleteById(1829);
    }

    @Test
    void reclaimsStaleLockBeforeAcquiring() {
        when(lockRepository.findById(1829))
                .thenReturn(Optional.of(lockOf(1829, LocalDateTime.now().minusMinutes(10))));
        ProblemGenerationLockManager manager = new ProblemGenerationLockManager(lockRepository);

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
}
