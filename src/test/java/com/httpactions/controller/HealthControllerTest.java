package com.httpactions.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    private DataSource dataSource;
    private Connection connection;
    private Statement statement;
    private HealthController controller;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        controller = new HealthController(dataSource);
    }

    @Test
    void health_whenDatabaseUp_returns200() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        ResponseEntity<Map<String, String>> response = controller.health();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("status", "UP", "database", "UP"), response.getBody());
    }

    @Test
    void health_whenDatabaseDown_returns503() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("db down"));

        ResponseEntity<Map<String, String>> response = controller.health();

        assertEquals(503, response.getStatusCode().value());
        assertEquals(Map.of("status", "DOWN", "database", "UNAVAILABLE"), response.getBody());
    }
}
