package com.cotea.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cotea.exception.CoteaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestBodySizeLimitFilterTest {

    private final RequestBodySizeLimitFilter filter = new RequestBodySizeLimitFilter(new ObjectMapper());

    @Test
    void rejectsRequestWhoseDeclaredContentLengthExceedsLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/hint");
        request.setContent(new byte[(int) (RequestBodySizeLimitFilter.MAX_REQUEST_BODY_BYTES + 1)]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(chain.getRequest()).isNull(); // 체인까지 못 감 - 바디를 읽기 전에 즉시 거부
        assertThat(response.getContentAsString()).contains("REQUEST_BODY_TOO_LARGE");
    }

    @Test
    void passesThroughRequestUnderLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/hint");
        request.setContent("{\"problemId\":1829}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void stopsReadingWhenActualBytesExceedLimitEvenWithoutDeclaredContentLength() throws Exception {
        // Content-Length를 알 수 없는 경우(-1, 예: 청크 전송 인코딩)까지 대비한 실제 읽기
        // 바이트 상한 검증. 사전 체크(declaredLength)는 통과하지만, 스트림을 실제로 다
        // 읽으면 막혀야 한다.
        byte[] hugeBody = new byte[(int) (RequestBodySizeLimitFilter.MAX_REQUEST_BODY_BYTES + 1)];
        HttpServletRequest request = new HttpServletRequestWrapper(new MockHttpServletRequest("POST", "/api/hint")) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public ServletInputStream getInputStream() {
                return new FakeServletInputStream(new ByteArrayInputStream(hugeBody));
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
        assertThat(wrapped).isNotNull();

        ServletInputStream inputStream = wrapped.getInputStream();
        assertThatThrownBy(() -> {
            byte[] buffer = new byte[8192];
            while (inputStream.read(buffer) != -1) {
                // 끝까지 읽어본다 - 상한을 넘는 순간 CoteaException이 나야 한다
                // (GlobalExceptionHandler가 처리할 수 있도록 RuntimeException으로 던짐)
            }
        }).isInstanceOf(CoteaException.class);
    }

    private static final class FakeServletInputStream extends ServletInputStream {
        private final InputStream delegate;

        private FakeServletInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
            try {
                return delegate.available() == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // 테스트에서는 비동기 읽기를 쓰지 않아 no-op
        }
    }
}
