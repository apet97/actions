package com.httpactions.repository;

import com.httpactions.model.entity.Action;
import com.httpactions.model.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

@Repository
public interface ActionRepository extends JpaRepository<Action, UUID> {

    List<Action> findByWorkspaceId(String workspaceId);

    List<Action> findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAscIdAsc(String workspaceId, EventType eventType);

    Optional<Action> findByIdAndWorkspaceId(UUID id, String workspaceId);

    long countByWorkspaceId(String workspaceId);

    boolean existsByWorkspaceIdAndName(String workspaceId, String name);

    long countByWorkspaceIdAndEnabledTrue(String workspaceId);

    @Query("""
            SELECT action
            FROM Action action
            WHERE action.enabled = true
              AND action.cronExpression IS NOT NULL
              AND (action.lastScheduledRun IS NULL OR action.lastScheduledRun < :threshold)
            """)
    List<Action> findDueScheduledActions(@Param("threshold") Instant threshold);

    /**
     * Acquire a transaction-scoped advisory lock keyed by workspace hash.
     * Prevents race condition on MAX_ACTIONS_PER_WORKSPACE count-then-insert.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:workspaceId))", nativeQuery = true)
    void acquireWorkspaceLock(@Param("workspaceId") String workspaceId);
}
