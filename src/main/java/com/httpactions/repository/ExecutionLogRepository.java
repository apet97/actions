package com.httpactions.repository;

import com.httpactions.model.dto.WidgetStatsRow;
import com.httpactions.model.entity.ExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {

    Page<ExecutionLog> findByActionIdOrderByExecutedAtDesc(UUID actionId, Pageable pageable);

    Page<ExecutionLog> findByWorkspaceIdOrderByExecutedAtDesc(String workspaceId, Pageable pageable);

    @Modifying
    @Query(value = "DELETE FROM execution_logs WHERE id IN " +
            "(SELECT id FROM execution_logs WHERE executed_at < :cutoff LIMIT :batchSize)",
            nativeQuery = true)
    int deleteByExecutedAtBeforeBatch(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    @Query("""
            SELECT new com.httpactions.model.dto.WidgetStatsRow(
                COUNT(log),
                SUM(CASE WHEN log.success = false THEN 1 ELSE 0 END)
            )
            FROM ExecutionLog log
            WHERE log.workspaceId = :workspaceId AND log.executedAt > :since
            """)
    WidgetStatsRow countWidgetStats(@Param("workspaceId") String workspaceId, @Param("since") Instant since);
}
