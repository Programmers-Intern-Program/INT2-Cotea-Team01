package com.cotea.controller;

import com.cotea.service.problem.generation.ProblemGenerationOrchestrator;
import com.cotea.service.problem.generation.ProblemReadyStatus;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 익스텐션이 문제 페이지에 "입장"할 때 호출한다. POST ensure-ready는 트리거일 뿐이다 — 문제 데이터가
 * 이미 있으면 아무 것도 안 하고, 없으면 생성 작업을 별도 스레드에 던지기만 하고 즉시 리턴한다(응답을
 * 기다려도 생성 완료를 보장하지 않음). 다른 요청이 이미 생성 중이어도 마찬가지로 즉시 리턴한다.
 * 실제 생성 완료 여부는 GET status를 폴링해서 확인해야 한다 — ensure-ready 응답 자체로는 "생성이
 * 끝났다"를 절대 판단할 수 없다(docs/problem-data-generation-prompt.md §1 참고).
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemGenerationOrchestrator orchestrator;

    @PostMapping("/{problemId}/ensure-ready")
    public ResponseEntity<Void> ensureReady(@PathVariable int problemId) {
        orchestrator.ensureReady(problemId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{problemId}/status")
    public ResponseEntity<Map<String, ProblemReadyStatus>> status(@PathVariable int problemId) {
        return ResponseEntity.ok(Map.of("status", orchestrator.getStatus(problemId)));
    }
}
