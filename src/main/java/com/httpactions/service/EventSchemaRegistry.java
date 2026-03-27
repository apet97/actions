package com.httpactions.service;

import com.httpactions.model.enums.EventType;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EventSchemaRegistry {

    private final Map<EventType, List<EventVariable>> schemas;

    public EventSchemaRegistry() {
        Map<EventType, List<EventVariable>> map = new EnumMap<>(EventType.class);

        // NEW_TIME_ENTRY
        map.put(EventType.NEW_TIME_ENTRY, List.of(
                var("event.id", "string", "Time entry ID"),
                var("event.description", "string", "Time entry description"),
                var("event.userId", "string", "User who created the entry"),
                var("event.projectId", "string", "Associated project ID"),
                var("event.timeInterval.start", "string", "Start time (ISO 8601)"),
                var("event.timeInterval.end", "string", "End time (ISO 8601)"),
                var("event.timeInterval.duration", "string", "Duration (ISO 8601)"),
                var("event.billable", "boolean", "Whether entry is billable"),
                var("event.tags[0].id", "string", "First tag ID"),
                var("event.tags[0].name", "string", "First tag name")
        ));

        // TIME_ENTRY_UPDATED — same schema as NEW_TIME_ENTRY
        map.put(EventType.TIME_ENTRY_UPDATED, List.of(
                var("event.id", "string", "Time entry ID"),
                var("event.description", "string", "Time entry description"),
                var("event.userId", "string", "User who created the entry"),
                var("event.projectId", "string", "Associated project ID"),
                var("event.timeInterval.start", "string", "Start time (ISO 8601)"),
                var("event.timeInterval.end", "string", "End time (ISO 8601)"),
                var("event.timeInterval.duration", "string", "Duration (ISO 8601)"),
                var("event.billable", "boolean", "Whether entry is billable"),
                var("event.tags[0].id", "string", "First tag ID"),
                var("event.tags[0].name", "string", "First tag name")
        ));

        // TIME_ENTRY_DELETED
        map.put(EventType.TIME_ENTRY_DELETED, List.of(
                var("event.id", "string", "Deleted time entry ID"),
                var("event.userId", "string", "User who owned the entry"),
                var("event.projectId", "string", "Associated project ID")
        ));

        // NEW_TIMER_STARTED
        map.put(EventType.NEW_TIMER_STARTED, List.of(
                var("event.id", "string", "Time entry ID"),
                var("event.description", "string", "Timer description"),
                var("event.userId", "string", "User who started the timer"),
                var("event.timeInterval.start", "string", "Start time (ISO 8601)"),
                var("event.projectId", "string", "Associated project ID")
        ));

        // TIMER_STOPPED
        map.put(EventType.TIMER_STOPPED, List.of(
                var("event.id", "string", "Time entry ID"),
                var("event.description", "string", "Timer description"),
                var("event.userId", "string", "User who stopped the timer"),
                var("event.timeInterval.start", "string", "Start time (ISO 8601)"),
                var("event.timeInterval.end", "string", "End time (ISO 8601)"),
                var("event.timeInterval.duration", "string", "Duration (ISO 8601)"),
                var("event.projectId", "string", "Associated project ID")
        ));

        // NEW_PROJECT
        map.put(EventType.NEW_PROJECT, List.of(
                var("event.id", "string", "Project ID"),
                var("event.name", "string", "Project name"),
                var("event.clientId", "string", "Associated client ID"),
                var("event.workspaceId", "string", "Workspace ID"),
                var("event.color", "string", "Project color hex code"),
                var("event.billable", "boolean", "Whether project is billable")
        ));

        // NEW_CLIENT
        map.put(EventType.NEW_CLIENT, List.of(
                var("event.id", "string", "Client ID"),
                var("event.name", "string", "Client name"),
                var("event.workspaceId", "string", "Workspace ID"),
                var("event.email", "string", "Client email address")
        ));

        // NEW_INVOICE
        map.put(EventType.NEW_INVOICE, List.of(
                var("event.id", "string", "Invoice ID"),
                var("event.number", "string", "Invoice number"),
                var("event.clientId", "string", "Associated client ID"),
                var("event.status", "string", "Invoice status"),
                var("event.total", "number", "Invoice total in cents")
        ));

        // USER_JOINED_WORKSPACE
        map.put(EventType.USER_JOINED_WORKSPACE, List.of(
                var("event.id", "string", "User ID"),
                var("event.email", "string", "User email address"),
                var("event.name", "string", "User display name"),
                var("event.status", "string", "User status")
        ));

        // NEW_TASK
        map.put(EventType.NEW_TASK, List.of(
                var("event.id", "string", "Task ID"),
                var("event.name", "string", "Task name"),
                var("event.projectId", "string", "Parent project ID"),
                var("event.status", "string", "Task status"),
                var("event.estimate", "string", "Task estimate (ISO 8601 duration)")
        ));

        this.schemas = Collections.unmodifiableMap(map);
    }

    /**
     * Returns all event schemas keyed by EventType.
     */
    public Map<EventType, List<EventVariable>> getSchemas() {
        return schemas;
    }

    /**
     * Returns the variable list for one event type, including meta and special variables.
     */
    public List<EventVariable> getSchema(EventType eventType) {
        List<EventVariable> eventVars = schemas.getOrDefault(eventType, List.of());
        List<EventVariable> result = new ArrayList<>(eventVars.size() + 5);
        result.addAll(eventVars);
        // Meta variables common to all events
        result.add(var("meta.workspaceId", "string", "Workspace ID from webhook context"));
        result.add(var("meta.eventType", "string", "Event type identifier"));
        result.add(var("meta.receivedAt", "string", "Timestamp when event was received (ISO 8601)"));
        result.add(var("meta.addonId", "string", "Addon ID from JWT claims"));
        // Special: raw event access
        result.add(var("event.raw", "object", "Complete raw event payload as JSON"));
        return Collections.unmodifiableList(result);
    }

    private static EventVariable var(String path, String type, String description) {
        return new EventVariable(path, type, description);
    }

    /**
     * Describes a single variable available for template interpolation.
     */
    public static class EventVariable {
        private final String path;
        private final String type;
        private final String description;

        public EventVariable(String path, String type, String description) {
            this.path = path;
            this.type = type;
            this.description = description;
        }

        public String getPath() { return path; }
        public String getType() { return type; }
        public String getDescription() { return description; }
    }
}
