package com.httpactions.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            return ResponseEntity.ok(Map.of("status", "UP", "database", "UP"));
        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of("status", "DOWN", "database", "UNAVAILABLE"));
        }
    }
}
