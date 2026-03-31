package com.httpactions.model.dto;

public record WidgetStats(
        long activeActionCount,
        long totalExecutions24h,
        long failedExecutions24h,
        double successRate24h
) {
    public long getActiveActionCount() { return activeActionCount; }
    public long getTotalExecutions24h() { return totalExecutions24h; }
    public long getFailedExecutions24h() { return failedExecutions24h; }
    public double getSuccessRate24h() { return successRate24h; }
}
