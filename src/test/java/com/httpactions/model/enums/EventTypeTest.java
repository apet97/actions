package com.httpactions.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class EventTypeTest {

    // ---------------------------------------------------------------
    // fromSlug
    // ---------------------------------------------------------------

    @Test
    void fromSlug_validSlugs_returnsCorrectEventType() {
        assertEquals(EventType.NEW_TIME_ENTRY, EventType.fromSlug("new-time-entry"));
        assertEquals(EventType.TIME_ENTRY_UPDATED, EventType.fromSlug("time-entry-updated"));
        assertEquals(EventType.NEW_TIMER_STARTED, EventType.fromSlug("new-timer-started"));
        assertEquals(EventType.TIMER_STOPPED, EventType.fromSlug("timer-stopped"));
        assertEquals(EventType.NEW_PROJECT, EventType.fromSlug("new-project"));
        assertEquals(EventType.NEW_CLIENT, EventType.fromSlug("new-client"));
        assertEquals(EventType.NEW_INVOICE, EventType.fromSlug("new-invoice"));
        assertEquals(EventType.USER_JOINED_WORKSPACE, EventType.fromSlug("user-joined"));
        assertEquals(EventType.NEW_TASK, EventType.fromSlug("new-task"));
        assertEquals(EventType.TIME_ENTRY_DELETED, EventType.fromSlug("time-entry-deleted"));
    }

    @Test
    void fromSlug_invalidSlug_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> EventType.fromSlug("nonexistent-slug"));
        assertThrows(IllegalArgumentException.class, () -> EventType.fromSlug(""));
        assertThrows(IllegalArgumentException.class, () -> EventType.fromSlug("NEW_TIME_ENTRY"));
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
    void fromSlug_roundTrip_returnsOriginal(EventType eventType) {
        assertEquals(eventType, EventType.fromSlug(eventType.getSlug()));
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    void fromWebhookHeader_roundTrip_returnsOriginal(EventType eventType) {
        assertEquals(eventType, EventType.fromWebhookHeader(eventType.name()));
    }
}
