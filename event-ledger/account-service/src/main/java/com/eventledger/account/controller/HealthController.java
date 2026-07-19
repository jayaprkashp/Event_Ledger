package com.eventledger.account.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp;
        try (Connection c = dataSource.getConnection()) {
            dbUp = c.isValid(2);
        } catch (Exception e) {
            dbUp = false;
        }

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", dbUp ? "UP" : "DOWN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("service", "account-service");
        body.put("checks", checks);

        return ResponseEntity.status(dbUp ? 200 : 503).body(body);
    }
}
