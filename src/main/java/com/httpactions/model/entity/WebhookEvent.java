package com.httpactions.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "webhook_events", uniqueConstraints = {
    @UniqueConstraint(name = "uq_webhook_events_workspace_event", columnNames = {"workspace_id", "event_id"})
})
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, length = 64)
    private String workspaceId;

    @Column(name = "event_id", nullable = false, length = 256)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    @PrePersist
    public void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WebhookEvent that = (WebhookEvent) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return getClass().hashCode();
    }
}
