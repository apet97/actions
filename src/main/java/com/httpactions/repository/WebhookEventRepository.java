package com.httpactions.repository;

import com.httpactions.model.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    /**
     * H6: Atomic insert-if-absent using ON CONFLICT. Returns the new row ID if inserted,
     * or null if the event already existed (duplicate).
     */
    @Modifying
    @Query(value = "INSERT INTO webhook_events (workspace_id, event_id, event_type, received_at) " +
            "VALUES (:wsId, :eventId, :eventType, NOW()) ON CONFLICT (workspace_id, event_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("wsId") String wsId, @Param("eventId") String eventId,
                       @Param("eventType") String eventType);

    @Modifying
    @Query("DELETE FROM WebhookEvent w WHERE w.receivedAt < :cutoff")
    int deleteByReceivedAtBefore(Instant cutoff);
}
