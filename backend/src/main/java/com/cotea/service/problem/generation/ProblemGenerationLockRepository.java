package com.cotea.service.problem.generation;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemGenerationLockRepository extends JpaRepository<ProblemGenerationLockEntity, Integer> {

    /**
     * 반드시 INSERT만 수행한다(UPSERT 아님). Spring Data JPA의 save()는 @Id가 이미 채워져
     * 있으면 merge()를 호출해 기존 행을 조용히 덮어쓸 수 있어 락 용도로 쓸 수 없다 — 그래서
     * 네이티브 INSERT를 직접 쓴다. problem_id가 이미 있으면 유니크 제약(PK) 위반으로
     * DataIntegrityViolationException이 그대로 던져진다.
     */
    @Modifying
    @Query(value = "INSERT INTO problem_generation_lock (problem_id, started_at) VALUES (:problemId, :startedAt)",
            nativeQuery = true)
    void insertLock(@Param("problemId") int problemId, @Param("startedAt") LocalDateTime startedAt);
}
