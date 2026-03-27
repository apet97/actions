package com.httpactions.model.dto;

/**
 * Aggregated statistics for the widget dashboard.
 */
public class WidgetStats {

    private long activeActionCount;
    private long totalExecutions24h;
    private long failedExecutions24h;
    private double successRate24h;

    public WidgetStats() {}

    public WidgetStats(long activeActionCount, long totalExecutions24h,
                       long failedExecutions24h, double successRate24h) {
        this.activeActionCount = activeActionCount;
        this.totalExecutions24h = totalExecutions24h;
        this.failedExecutions24h = failedExecutions24h;
        this.successRate24h = successRate24h;
    }

    public long getActiveActionCount() { return activeActionCount; }
    public void setActiveActionCount(long activeActionCount) { this.activeActionCount = activeActionCount; }

    public long getTotalExecutions24h() { return totalExecutions24h; }
    public void setTotalExecutions24h(long totalExecutions24h) { this.totalExecutions24h = totalExecutions24h; }

    public long getFailedExecutions24h() { return failedExecutions24h; }
    public void setFailedExecutions24h(long failedExecutions24h) { this.failedExecutions24h = failedExecutions24h; }

    public double getSuccessRate24h() { return successRate24h; }
    public void setSuccessRate24h(double successRate24h) { this.successRate24h = successRate24h; }
}
