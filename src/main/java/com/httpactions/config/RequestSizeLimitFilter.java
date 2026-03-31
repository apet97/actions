package com.httpactions.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final long MAX_REQUEST_BYTES = 1_048_576L;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return true;
        }

        String path = request.getRequestURI();
        if ("/api/health".equals(path)) {
            return true;
        }

        return !path.startsWith("/api/")
                && !path.startsWith("/webhook/")
                && !path.startsWith("/lifecycle/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_REQUEST_BYTES) {
            reject(response);
            return;
        }

        try {
            filterChain.doFilter(new RequestSizeLimitedWrapper(request), response);
        } catch (RequestTooLargeException ex) {
            reject(response);
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Request body exceeds maximum allowed size\"}");
    }

    private static final class RequestSizeLimitedWrapper extends HttpServletRequestWrapper {

        private ServletInputStream inputStream;
        private BufferedReader reader;

        private RequestSizeLimitedWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (reader != null) {
                throw new IllegalStateException("getReader() has already been called for this request");
            }
            if (inputStream == null) {
                inputStream = new CountingServletInputStream(super.getInputStream());
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (inputStream != null) {
                throw new IllegalStateException("getInputStream() has already been called for this request");
            }
            if (reader == null) {
                reader = new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
            return reader;
        }
    }

    private static final class CountingServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long bytesRead;

        private CountingServletInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int result = delegate.read();
            if (result != -1) {
                track(1);
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = delegate.read(b, off, len);
            if (result > 0) {
                track(result);
            }
            return result;
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

        private void track(int count) throws RequestTooLargeException {
            bytesRead += count;
            if (bytesRead > MAX_REQUEST_BYTES) {
                throw new RequestTooLargeException();
            }
        }
    }

    private static final class RequestTooLargeException extends IOException {
        private RequestTooLargeException() {
            super("Request body exceeds maximum allowed size");
        }
    }
}
