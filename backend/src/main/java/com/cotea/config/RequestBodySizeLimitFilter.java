package com.cotea.config;

import com.cotea.controller.dto.ErrorResponse;
import com.cotea.exception.CoteaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fix/be#161 - HintRequestValidator의 questionText 길이 검증(Fix/be#154)은 Jackson이
 * 요청 바디 전체를 이미 메모리에 올려 HintRequest 객체로 만든 뒤에야 실행된다. 그래서
 * @RequestBody를 쓰는 엔드포인트에 아주 큰 바디를 보내면, 검증에서 거부되기 전에 이미
 * 메모리 소모가 끝나버려 대용량 페이로드 반복 요청으로 서버 메모리를 고갈시킬 수 있다.
 *
 * 이 필터는 Jackson이 바디를 읽기 전(Content-Length 기준) 또는 읽는 도중(실제 바이트
 * 수 기준)에 요청을 끊어서, 큰 바디가 애초에 애플리케이션 메모리에 쌓이지 않게 한다.
 *
 * 필터는 DispatcherServlet보다 앞단에서 동작하므로, Content-Length 사전 체크에서
 * 거부하는 경우는 GlobalExceptionHandler(@RestControllerAdvice)를 못 타 직접 JSON을
 * 써야 한다. 반면 실제 읽기 도중 상한을 넘는 경우(CoteaException)는 Jackson 파싱이
 * DispatcherServlet 처리 중에 일어나므로 GlobalExceptionHandler가 정상적으로 받는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_REQUEST_BODY_BYTES = 1024L * 1024; // 1MB
    private static final String ERROR_CODE = "REQUEST_BODY_TOO_LARGE";
    private static final String ERROR_MESSAGE = "요청 본문이 너무 큽니다.";

    private final ObjectMapper objectMapper;

    public RequestBodySizeLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > MAX_REQUEST_BODY_BYTES) {
            writeTooLargeResponse(response);
            return;
        }

        // Content-Length가 없거나(청크 전송 인코딩) 실제보다 작게 선언된 경우까지 대비해,
        // 실제로 읽히는 바이트 수 자체에도 상한을 건다.
        filterChain.doFilter(new SizeLimitingRequestWrapper(request, MAX_REQUEST_BODY_BYTES), response);
    }

    private void writeTooLargeResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(ERROR_CODE, ERROR_MESSAGE)));
    }

    private static final class SizeLimitingRequestWrapper extends HttpServletRequestWrapper {

        private final long maxBytes;

        private SizeLimitingRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream original = super.getInputStream();
            return new SizeLimitingServletInputStream(original, maxBytes);
        }
    }

    private static final class SizeLimitingServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead = 0;

        private SizeLimitingServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                checkLimit(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                checkLimit(n);
            }
            return n;
        }

        private void checkLimit(int n) {
            bytesRead += n;
            if (bytesRead > maxBytes) {
                // CoteaException(RuntimeException)이라 read()의 throws IOException 선언과
                // 무관하게 던질 수 있고, DispatcherServlet 처리 중이라 GlobalExceptionHandler가 받는다.
                throw new CoteaException(ERROR_CODE, ERROR_MESSAGE, 413);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
