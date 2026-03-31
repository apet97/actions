package com.httpactions.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestSizeLimitFilterTest {

    private RequestSizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestSizeLimitFilter();
    }

    @Test
    void oversizedContentLength_returns413() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/actions/import") {
            @Override
            public long getContentLengthLong() {
                return RequestSizeLimitFilter.MAX_REQUEST_BYTES + 1;
            }
        };
        request.setRequestURI("/api/actions/import");
        request.addHeader("Content-Type", "application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertNull(chain.getRequest());
    }

    @Test
    void oversizedStreamWithoutContentLength_returns413() throws ServletException, IOException {
        byte[] body = "a".repeat((int) RequestSizeLimitFilter.MAX_REQUEST_BYTES + 1).getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/new-time-entry") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setRequestURI("/webhook/new-time-entry");
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                FileCopyUtils.copyToByteArray(request.getInputStream());
            }
        };

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
    }

    @Test
    void lifecyclePath_isProtectedBySizeLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/lifecycle/installed") {
            @Override
            public long getContentLengthLong() {
                return RequestSizeLimitFilter.MAX_REQUEST_BYTES + 1;
            }
        };
        request.setRequestURI("/lifecycle/installed");
        request.addHeader("Content-Type", "application/json");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(413, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void healthPath_bypassesFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/health");
        request.setRequestURI("/api/health");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }
}
