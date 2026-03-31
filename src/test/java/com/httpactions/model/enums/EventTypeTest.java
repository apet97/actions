package com.httpactions.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class EventTypeTest {

    // ---------------------------------------------------------------
    // Slugs
    // ---------------------------------------------------------------

    @Test
    void knownEventSlugs_matchExpectedValues() {
        assertEquals("new-time-entry", EventType.NEW_TIME_ENTRY.getSlug());
        assertEquals("time-entry-updated", EventType.TIME_ENTRY_UPDATED.getSlug());
        assertEquals("new-timer-started", EventType.NEW_TIMER_STARTED.getSlug());
        assertEquals("timer-stopped", EventType.TIMER_STOPPED.getSlug());
        assertEquals("new-project", EventType.NEW_PROJECT.getSlug());
        assertEquals("new-client", EventType.NEW_CLIENT.getSlug());
        assertEquals("new-invoice", EventType.NEW_INVOICE.getSlug());
        assertEquals("user-joined", EventType.USER_JOINED_WORKSPACE.getSlug());
        assertEquals("new-task", EventType.NEW_TASK.getSlug());
        assertEquals("time-entry-deleted", EventType.TIME_ENTRY_DELETED.getSlug());
    }

    @Test
    void eventSlugs_areUnique() {
        long uniqueCount = java.util.Arrays.stream(EventType.values())
                .map(EventType::getSlug)
                .distinct()
                .count();
        assertEquals(EventType.values().length, uniqueCount);
    }

    // ---------------------------------------------------------------
    // fromWebhookHeader
    // ---------------------------------------------------------------

    @Test
    void fromWebhookHeader_validHeaders_returnsCorrectEventType() {
        assertEquals(EventType.NEW_TIME_ENTRY, EventType.fromWebhookHeader("NEW_TIME_ENTRY"));
        assertEquals(EventType.TIME_ENTRY_UPDATED, EventType.fromWebhookHeader("TIME_ENTRY_UPDATED"));
        assertEquals(EventType.NEW_TIMER_STARTED, EventType.fromWebhookHeader("NEW_TIMER_STARTED"));
        assertEquals(EventType.TIMER_STOPPED, EventType.fromWebhookHeader("TIMER_STOPPED"));
        assertEquals(EventType.NEW_PROJECT, EventType.fromWebhookHeader("NEW_PROJECT"));
        assertEquals(EventType.NEW_CLIENT, EventType.fromWebhookHeader("NEW_CLIENT"));
        assertEquals(EventType.NEW_INVOICE, EventType.fromWebhookHeader("NEW_INVOICE"));
        assertEquals(EventType.USER_JOINED_WORKSPACE, EventType.fromWebhookHeader("USER_JOINED_WORKSPACE"));
        assertEquals(EventType.NEW_TASK, EventType.fromWebhookHeader("NEW_TASK"));
        assertEquals(EventType.TIME_ENTRY_DELETED, EventType.fromWebhookHeader("TIME_ENTRY_DELETED"));
    }

    @Test
    void fromWebhookHeader_invalidHeader_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> EventType.fromWebhookHeader("UNKNOWN_EVENT"));
        assertThrows(IllegalArgumentException.class, () -> EventType.fromWebhookHeader(""));
        assertThrows(IllegalArgumentException.class, () -> EventType.fromWebhookHeader("new-time-entry"));
    }

    // ---------------------------------------------------------------
    // Slug integrity
    // ---------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(EventType.class)
    void allEnumValues_haveNonNullSlugs(EventType eventType) {
        assertNotNull(eventType.getSlug(), eventType.name() + " has a null slug");
        assertFalse(eventType.getSlug().isEmpty(), eventType.name() + " has an empty slug");
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    void fromWebhookHeader_roundTrip_returnsOriginal(EventType eventType) {
        assertEquals(eventType, EventType.fromWebhookHeader(eventType.name()));
    }
}
