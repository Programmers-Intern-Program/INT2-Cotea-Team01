package com.cotea.service.problem.generation;

/** GET /api/problems/{problemId}/status 응답에 쓰는 문제 데이터 생성 상태. */
public enum ProblemReadyStatus {
    READY,
    GENERATING,
    NOT_STARTED
}
