package com.cotea.service.problem.generation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 같은 problemId에 대한 동시 생성(다른 유저의 요청이라도)을 막는 락 행.
 * problem_id가 PK라, 같은 problemId로 두 번째 INSERT를 시도하면 유니크 제약 위반으로
 * 실패한다 — 그 실패 자체가 "누군가 이미 생성 중"이라는 신호다.
 * 생성이 끝나면(성공/실패 무관) {@link ProblemGenerationLockManager}가 이 행을 지운다.
 */
@Getter
@Entity
@Table(name = "problem_generation_lock")
@NoArgsConstructor
@AllArgsConstructor
public class ProblemGenerationLockEntity {

    @Id
    @Column(name = "problem_id")
    private Integer problemId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;
}
