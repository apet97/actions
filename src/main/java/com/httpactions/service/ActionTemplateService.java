package com.httpactions.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Provides pre-built action templates for common integrations.
 * Templates give users a quick start for common HTTP action patterns.
 */
@Service
public class ActionTemplateService {

    private final List<ActionTemplate> templates;

    public ActionTemplateService() {
        this.templates = buildTemplates();
    }

    public List<ActionTemplate> getTemplates() {
        return Collections.unmodifiableList(templates);
    }

    private List<ActionTemplate> buildTemplates() {
        List<ActionTemplate> list = new ArrayList<>();

        // Slack Incoming Webhook
        list.add(new ActionTemplate(
                "slack-webhook",
                "Slack Incoming Webhook",
                "Post a message to a Slack channel when a time entry is created.",
                "Messaging",
                "NEW_TIME_ENTRY",
                "POST",
                "https://hooks.slack.com/services/YOUR/WEBHOOK/URL",
                """
                {"text": "New time entry by {{event.userId}}: {{event.description}} ({{event.timeInterval.duration}})"}""",
                Map.of("Content-Type", "application/json")
        ));

        // Discord Webhook
        list.add(new ActionTemplate(
                "discord-webhook",
                "Discord Webhook",
                "Post a message to a Discord channel when a time entry is created.",
                "Messaging",
                "NEW_TIME_ENTRY",
                "POST",
                "https://discord.com/api/webhooks/YOUR/WEBHOOK/URL",
                """
                {"content": "New time entry by {{event.userId}}: {{event.description}} ({{event.timeInterval.duration}})"}""",
                Map.of("Content-Type", "application/json")
        ));

        // Generic REST API POST
        list.add(new ActionTemplate(
                "generic-rest-post",
                "Generic REST API POST",
                "Forward the full event payload as a JSON POST to any REST endpoint.",
                "General",
                "NEW_TIME_ENTRY",
                "POST",
                "https://api.example.com/webhook",
                "{{event.raw}}",
                Map.of("Content-Type", "application/json")
        ));

        // Generic REST API GET
        list.add(new ActionTemplate(
                "generic-rest-get",
                "Generic REST API GET",
                "Call a REST endpoint with event data as URL parameters.",
                "General",
                "NEW_TIME_ENTRY",
                "GET",
                "https://api.example.com/events?userId={{event.userId}}&description={{event.description}}&projectId={{event.projectId}}",
                null,
                Map.of("Accept", "application/json")
        ));

        // Google Sheets Append
        list.add(new ActionTemplate(
                "google-sheets-append",
                "Google Sheets API — Append Row",
                "Append a row to a Google Sheet when a time entry is created. Requires a Google API key or OAuth token.",
                "Productivity",
                "NEW_TIME_ENTRY",
                "POST",
                "https://sheets.googleapis.com/v4/spreadsheets/YOUR_SPREADSHEET_ID/values/Sheet1!A1:append?valueInputOption=USER_ENTERED",
                """
                {"values": [["{{event.userId}}", "{{event.description}}", "{{event.timeInterval.start}}", "{{event.timeInterval.end}}", "{{event.timeInterval.duration}}"]]}""",
                Map.of("Content-Type", "application/json", "Authorization", "Bearer YOUR_ACCESS_TOKEN")
        ));

        // L4: templates for non-time-entry events
        list.add(new ActionTemplate(
                "project-created-webhook",
                "New Project Notification",
                "Send a webhook notification when a new project is created.",
                "General",
                "NEW_PROJECT",
                "POST",
                "https://api.example.com/webhooks/project-created",
                """
                {"projectId": "{{event.id}}", "name": "{{event.name}}", "billable": {{event.billable}}, "workspace": "{{meta.workspaceId}}"}""",
                Map.of("Content-Type", "application/json")
        ));

        list.add(new ActionTemplate(
                "invoice-created-slack",
                "Invoice Created — Slack Alert",
                "Post to Slack when a new invoice is created.",
                "Messaging",
                "NEW_INVOICE",
                "POST",
                "https://hooks.slack.com/services/YOUR/WEBHOOK/URL",
                """
                {"text": "New invoice {{event.number}} for client {{event.clientId}} — total: {{event.total}} cents"}""",
                Map.of("Content-Type", "application/json")
        ));

        list.add(new ActionTemplate(
                "user-joined-webhook",
                "User Joined Workspace",
                "Notify an external system when a new user joins the workspace.",
                "General",
                "USER_JOINED_WORKSPACE",
                "POST",
                "https://api.example.com/webhooks/user-joined",
                """
                {"userId": "{{event.id}}", "email": "{{event.email}}", "name": "{{event.name}}", "workspace": "{{meta.workspaceId}}"}""",
                Map.of("Content-Type", "application/json")
        ));

        return list;
    }

    /**
     * Pre-built action template with sample configuration.
     */
    public static class ActionTemplate {

        private final String id;
        private final String name;
        private final String description;
        private final String category;
        private final String eventType;
        private final String httpMethod;
        private final String urlTemplate;
        private final String bodyTemplate;
        private final Map<String, String> sampleHeaders;

        public ActionTemplate(String id, String name, String description, String category,
                              String eventType, String httpMethod, String urlTemplate,
                              String bodyTemplate, Map<String, String> sampleHeaders) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.category = category;
            this.eventType = eventType;
            this.httpMethod = httpMethod;
            this.urlTemplate = urlTemplate;
            this.bodyTemplate = bodyTemplate;
            this.sampleHeaders = sampleHeaders != null ? Map.copyOf(sampleHeaders) : Map.of();
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getCategory() { return category; }
        public String getEventType() { return eventType; }
        public String getHttpMethod() { return httpMethod; }
        public String getUrlTemplate() { return urlTemplate; }
        public String getBodyTemplate() { return bodyTemplate; }
        public Map<String, String> getSampleHeaders() { return sampleHeaders; }
    }
}
