package com.cotea.service.problem.generation;

/**
 * 문제 상세 페이지 HTML에서 필요한 정보(problemId/title/level/본문)를 추출하지 못했을 때 던진다.
 * 페이지 구조가 바뀌었거나, 문제 상세 페이지가 아닌 다른 HTML이 들어왔을 가능성이 크다.
 */
public class ProblemHtmlParseException extends RuntimeException {
    public ProblemHtmlParseException(String message) {
        super(message);
    }
}
