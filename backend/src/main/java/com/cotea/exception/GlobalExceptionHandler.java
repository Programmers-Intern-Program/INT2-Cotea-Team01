package com.cotea.exception;

import com.cotea.controller.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CoteaException.class)
    public ResponseEntity<ErrorResponse> handleCotea(CoteaException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "요청 값이 올바르지 않습니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        // 여기도 로그가 없어서, /status 폴링이 404를 맞는데도 백엔드 콘솔엔 아무 흔적이 안 남는
        // 문제가 있었다. 스프링이 실제로 어떤 경로/메서드를 못 찾았다고 판단했는지 남긴다.
        log.warn("경로를 찾을 수 없음: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return ResponseEntity.status(404)
                .body(new ErrorResponse("NOT_FOUND", "요청한 경로를 찾을 수 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        // 이 핸들러가 로그를 안 남기고 있어서, 500이 실제로 왜 났는지 서버 로그에서
        // 전혀 추적이 안 되는 문제가 있었다(응답 바디의 ex.getMessage()로만 확인 가능).
        log.error("처리되지 않은 예외로 500 응답", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("AI_SERVICE_ERROR", ex.getMessage()));
    }
}
