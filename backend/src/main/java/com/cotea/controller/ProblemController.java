package com.cotea.controller;

import com.cotea.service.problem.generation.ProblemGenerationOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 익스텐션이 문제 페이지에 "입장"할 때 fire-and-forget으로 호출한다 — 응답을 기다리지 않는다.
 * 문제 데이터가 이미 있으면 즉시 끝나고, 없으면 이 요청 스레드 안에서 동기로 생성을 수행한다
 * (그래서 이 요청 자체는 5~40초 걸릴 수 있지만, 아무도 그 응답을 기다리지 않으므로 문제 없다 —
 * docs/problem-data-generation-prompt.md §1 참고). 다른 요청이 이미 생성 중이면 아무 것도 안 하고
 * 즉시 반환한다. 힌트 요청 엔드포인트(POST /api/hint)는 이 흐름과 무관하게 그대로 동작하며,
 * 드물게 생성이 아직 안 끝난 상태에서 힌트를 요청하면 기존 에러 응답 경로로 자연스럽게 처리된다.
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemGenerationOrchestrator orchestrator;

    @PostMapping("/{problemId}/ensure-ready")
    public ResponseEntity<Void> ensureReady(@PathVariable int problemId) {
        orchestrator.ensureReady(problemId);
        return ResponseEntity.ok().build();
    }
}
