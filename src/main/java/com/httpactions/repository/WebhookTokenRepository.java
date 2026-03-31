package com.httpactions.repository;

import com.httpactions.model.entity.WebhookToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookTokenRepository extends JpaRepository<WebhookToken, Long> {

    Optional<WebhookToken> findByWorkspaceIdAndWebhookPath(String workspaceId, String webhookPath);

    List<WebhookToken> findAllByWorkspaceId(String workspaceId);
}
