package com.httpactions.repository;

import com.httpactions.model.entity.ExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {

    Page<ExecutionLog> findByActionIdOrderByExecutedAtDesc(UUID actionId, Pageable pageable);

    Page<ExecutionLog> findByWorkspaceIdOrderByExecutedAtDesc(String workspaceId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM ExecutionLog e WHERE e.executedAt < :cutoff")
    int deleteByExecutedAtBefore(Instant cutoff);

    long countByWorkspaceIdAndExecutedAtAfter(String workspaceId, Instant since);

    long countByWorkspaceIdAndSuccessAndExecutedAtAfter(String workspaceId, boolean success, Instant since);
}
