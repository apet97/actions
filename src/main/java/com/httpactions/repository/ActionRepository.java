package com.httpactions.repository;

import com.httpactions.model.entity.Action;
import com.httpactions.model.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActionRepository extends JpaRepository<Action, UUID> {

    List<Action> findByWorkspaceId(String workspaceId);

    List<Action> findByWorkspaceIdAndEventTypeAndEnabledTrueOrderByChainOrderAsc(String workspaceId, EventType eventType);

    Optional<Action> findByIdAndWorkspaceId(UUID id, String workspaceId);

    long countByWorkspaceId(String workspaceId);

    boolean existsByWorkspaceIdAndName(String workspaceId, String name);

    long countByWorkspaceIdAndEnabledTrue(String workspaceId);

    List<Action> findByEnabledTrueAndCronExpressionIsNotNull();
}
