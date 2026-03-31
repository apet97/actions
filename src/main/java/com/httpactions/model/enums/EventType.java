package com.httpactions.model.enums;

public enum EventType {
    NEW_TIME_ENTRY("new-time-entry", "New Time Entry"),
    TIME_ENTRY_UPDATED("time-entry-updated", "Time Entry Updated"),
    NEW_TIMER_STARTED("new-timer-started", "New Timer Started"),
    TIMER_STOPPED("timer-stopped", "Timer Stopped"),
    NEW_PROJECT("new-project", "New Project"),
    NEW_CLIENT("new-client", "New Client"),
    NEW_INVOICE("new-invoice", "New Invoice"),
    USER_JOINED_WORKSPACE("user-joined", "User Joined Workspace"),
    NEW_TASK("new-task", "New Task"),
    TIME_ENTRY_DELETED("time-entry-deleted", "Time Entry Deleted");

    private final String slug;
    private final String label;

    EventType(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public String getSlug() {
        return slug;
    }

    public String getLabel() {
        return label;
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
