package com.httpactions.model.enums;

public enum EventType {
    NEW_TIME_ENTRY("new-time-entry"),
    TIME_ENTRY_UPDATED("time-entry-updated"),
    NEW_TIMER_STARTED("new-timer-started"),
    TIMER_STOPPED("timer-stopped"),
    NEW_PROJECT("new-project"),
    NEW_CLIENT("new-client"),
    NEW_INVOICE("new-invoice"),
    USER_JOINED_WORKSPACE("user-joined"),
    NEW_TASK("new-task"),
    TIME_ENTRY_DELETED("time-entry-deleted");

    private final String slug;

    EventType(String slug) {
        this.slug = slug;
    }

    public String getSlug() {
        return slug;
    }

    public static EventType fromSlug(String slug) {
        for (EventType type : values()) {
            if (type.slug.equals(slug)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown event slug: " + slug);
    }

    public static EventType fromWebhookHeader(String headerValue) {
        for (EventType type : values()) {
            if (type.name().equals(headerValue)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown webhook event type: " + headerValue);
    }
}
