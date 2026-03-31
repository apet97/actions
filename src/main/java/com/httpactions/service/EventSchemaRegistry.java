package com.httpactions.service;

import com.httpactions.model.enums.EventType;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EventSchemaRegistry {

    private final Map<EventType, List<EventVariable>> schemas;
    private final Map<EventType, List<EventVariable>> schemasWithMeta;

    public EventSchemaRegistry() {
        Map<EventType, List<EventVariable>> map = new EnumMap<>(EventType.class);

        // NEW_TIME_ENTRY
        map.put(EventType.NEW_TIME_ENTRY, timeEntryVars());

        // TIME_ENTRY_UPDATED — same schema as NEW_TIME_ENTRY
        map.put(EventType.TIME_ENTRY_UPDATED, timeEntryVars());

        // TIME_ENTRY_DELETED — full time entry object, not just IDs
        map.put(EventType.TIME_ENTRY_DELETED, timeEntryVars());

        // NEW_TIMER_STARTED
        map.put(EventType.NEW_TIMER_STARTED, timeEntryVars());

        // TIMER_STOPPED
        map.put(EventType.TIMER_STOPPED, timeEntryVars());

        // NEW_PROJECT
        map.put(EventType.NEW_PROJECT, List.of(
                var("event.id", "string", "Project ID"),
                var("event.name", "string", "Project name"),
                var("event.clientId", "string", "Associated client ID"),
                var("event.clientName", "string", "Client name"),
                var("event.workspaceId", "string", "Workspace ID"),
                var("event.color", "string", "Project color hex code"),
                var("event.billable", "boolean", "Whether project is billable"),
                var("event.archived", "boolean", "Whether project is archived"),
                var("event.public", "boolean", "Whether project is public"),
                var("event.template", "boolean", "Whether project is a template"),
                var("event.note", "string", "Project notes"),
                var("event.duration", "string", "Project duration (ISO 8601)"),
                var("event.hourlyRate.amount", "number", "Project hourly rate in cents"),
                var("event.estimate.estimate", "string", "Project estimate value"),
                var("event.estimate.type", "string", "Estimate type (AUTO/MANUAL)"),
                var("event.client.id", "string", "Full client object ID"),
                var("event.client.name", "string", "Full client object name"),
                var("event.tasks[0].id", "string", "First task ID"),
                var("event.tasks[0].name", "string", "First task name")
        ));

        // NEW_CLIENT
        map.put(EventType.NEW_CLIENT, List.of(
                var("event.id", "string", "Client ID"),
                var("event.name", "string", "Client name"),
                var("event.workspaceId", "string", "Workspace ID"),
                var("event.email", "string", "Client email address"),
                var("event.archived", "boolean", "Whether client is archived")
        ));

        // NEW_INVOICE
        map.put(EventType.NEW_INVOICE, List.of(
                var("event.id", "string", "Invoice ID"),
                var("event.number", "string", "Invoice number"),
                var("event.clientId", "string", "Associated client ID"),
                var("event.clientName", "string", "Client name"),
                var("event.clientAddress", "string", "Client address"),
                var("event.status", "string", "Invoice status (UNSENT/SENT/PAID/VOID)"),
                var("event.total", "number", "Invoice total in cents"),
                var("event.subtotal", "number", "Invoice subtotal in cents"),
                var("event.amount", "number", "Invoice amount"),
                var("event.currency", "string", "Currency code (e.g. USD)"),
                var("event.issuedDate", "string", "Date invoice was issued"),
                var("event.dueDate", "string", "Payment due date"),
                var("event.subject", "string", "Invoice subject line"),
                var("event.note", "string", "Invoice notes"),
                var("event.discount", "number", "Discount percentage"),
                var("event.discountAmount", "number", "Discount amount in cents"),
                var("event.tax", "number", "Tax percentage"),
                var("event.taxAmount", "number", "Tax amount in cents"),
                var("event.tax2", "number", "Second tax percentage"),
                var("event.tax2Amount", "number", "Second tax amount in cents"),
                var("event.userId", "string", "Invoice creator user ID"),
                var("event.items[0].description", "string", "First line item description"),
                var("event.items[0].quantity", "number", "First line item quantity"),
                var("event.items[0].unitPrice", "number", "First line item unit price")
        ));

        // USER_JOINED_WORKSPACE
        map.put(EventType.USER_JOINED_WORKSPACE, List.of(
                var("event.id", "string", "User ID"),
                var("event.email", "string", "User email address"),
                var("event.name", "string", "User display name"),
                var("event.status", "string", "User status"),
                var("event.profilePicture", "string", "User avatar URL"),
                var("event.settings.timeZone", "string", "User timezone"),
                var("event.settings.dateFormat", "string", "User date format preference"),
                var("event.settings.timeFormat", "string", "User time format (12/24h)"),
                var("event.settings.weekStart", "string", "Week start day")
        ));

        // NEW_TASK
        map.put(EventType.NEW_TASK, List.of(
                var("event.id", "string", "Task ID"),
                var("event.name", "string", "Task name"),
                var("event.projectId", "string", "Parent project ID"),
                var("event.status", "string", "Task status (ACTIVE/DONE)"),
                var("event.estimate", "string", "Task estimate (ISO 8601 duration)"),
                var("event.duration", "string", "Task duration (ISO 8601)"),
                var("event.assigneeId", "string", "Assigned user ID"),
                var("event.assigneeIds[0]", "string", "First assignee ID"),
                var("event.userGroupIds[0]", "string", "First user group ID")
        ));

        this.schemas = Collections.unmodifiableMap(map);

        // H3: pre-compute schemas with meta variables to avoid per-call allocation
        Map<EventType, List<EventVariable>> withMeta = new EnumMap<>(EventType.class);
        for (Map.Entry<EventType, List<EventVariable>> entry : map.entrySet()) {
            List<EventVariable> full = new ArrayList<>(entry.getValue().size() + 7);
            full.addAll(entry.getValue());
            full.add(var("meta.workspaceId", "string", "Workspace ID from webhook context"));
            full.add(var("meta.eventType", "string", "Event type identifier"));
            full.add(var("meta.receivedAt", "string", "Timestamp when event was received (ISO 8601)"));
            full.add(var("meta.addonId", "string", "Addon ID from JWT claims"));
            full.add(var("meta.backendUrl", "string", "Clockify API base URL (e.g. https://api.clockify.me/api)"));
            full.add(var("meta.installationToken", "string", "Installation token for Clockify API calls (admin-only)"));
            full.add(var("event.raw", "object", "Complete raw event payload as JSON"));
            withMeta.put(entry.getKey(), Collections.unmodifiableList(full));
        }
        this.schemasWithMeta = Collections.unmodifiableMap(withMeta);
    }

    private static List<EventVariable> timeEntryVars() {
        return List.of(
                var("event.id", "string", "Time entry ID"),
                var("event.description", "string", "Time entry description"),
                var("event.userId", "string", "User who created the entry"),
                var("event.projectId", "string", "Associated project ID"),
                var("event.taskId", "string", "Associated task ID"),
                var("event.workspaceId", "string", "Workspace ID"),
                var("event.billable", "boolean", "Whether entry is billable"),
                var("event.isLocked", "boolean", "Whether entry is locked"),
                var("event.timeInterval.start", "string", "Start time (ISO 8601)"),
                var("event.timeInterval.end", "string", "End time (ISO 8601)"),
                var("event.timeInterval.duration", "string", "Duration (ISO 8601)"),
                var("event.tagIds[0]", "string", "First tag ID"),
                var("event.tags[0].id", "string", "First tag object ID"),
                var("event.tags[0].name", "string", "First tag name"),
                var("event.project.id", "string", "Project object ID"),
                var("event.project.name", "string", "Project name"),
                var("event.project.clientId", "string", "Project's client ID"),
                var("event.project.clientName", "string", "Project's client name"),
                var("event.project.color", "string", "Project color hex"),
                var("event.project.billable", "boolean", "Project billable flag"),
                var("event.task.id", "string", "Task object ID"),
                var("event.task.name", "string", "Task name"),
                var("event.user.id", "string", "User object ID"),
                var("event.user.name", "string", "User display name"),
                var("event.hourlyRate.amount", "number", "Hourly rate in cents"),
                var("event.costRate.amount", "number", "Cost rate in cents"),
                var("event.customFieldValues[0].customFieldId", "string", "First custom field ID"),
                var("event.customFieldValues[0].value", "string", "First custom field value")
        );
    }

    public Map<EventType, List<EventVariable>> getSchemas() {
        return schemas;
    }

    public List<EventVariable> getSchema(EventType eventType) {
        return schemasWithMeta.getOrDefault(eventType, List.of());
    }

    private static EventVariable var(String path, String type, String description) {
        return new EventVariable(path, type, description);
    }

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
