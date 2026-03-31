package com.httpactions.service;

public final class WidgetPresentation {

    public static final double OK_RATE_THRESHOLD = 95.0;
    public static final double WARN_RATE_THRESHOLD = 80.0;

    private WidgetPresentation() {
    }

    public static String rateClass(double rate) {
        if (rate >= OK_RATE_THRESHOLD) {
            return "green";
        }
        if (rate >= WARN_RATE_THRESHOLD) {
            return "yellow";
        }
        return "red";
    }

    public static String rateBadgePrefix(double rate) {
        if (rate >= OK_RATE_THRESHOLD) {
            return "OK";
        }
        if (rate >= WARN_RATE_THRESHOLD) {
            return "WARN";
        }
        return "ALERT";
    }
}
