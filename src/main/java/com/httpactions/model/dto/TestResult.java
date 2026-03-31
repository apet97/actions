package com.httpactions.model.dto;

import java.util.Map;

public class TestResult {

    private Integer responseStatus;
    private Integer responseTimeMs;
    private String responseBody;
    private Map<String, String> responseHeaders;
    private boolean success;
    private String errorMessage;

    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }

    public Integer getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(Integer responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public Map<String, String> getResponseHeaders() { return responseHeaders; }
    public void setResponseHeaders(Map<String, String> responseHeaders) { this.responseHeaders = responseHeaders; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
