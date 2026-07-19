package com.eventledger.gateway.controller;

import com.eventledger.gateway.client.AccountServiceHealthProbe;
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
    private final AccountServiceHealthProbe accountServiceHealthProbe;

    public HealthController(DataSource dataSource, AccountServiceHealthProbe accountServiceHealthProbe) {
        this.dataSource = dataSource;
        this.accountServiceHealthProbe = accountServiceHealthProbe;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp = checkDatabase();
        boolean accountServiceUp = accountServiceHealthProbe.isUp();

        String overall = !dbUp ? "DOWN" : (!accountServiceUp ? "DEGRADED" : "UP");

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", dbUp ? "UP" : "DOWN");
        checks.put("accountService", accountServiceUp ? "UP" : "DOWN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overall);
        body.put("service", "event-gateway");
        body.put("checks", checks);

        return ResponseEntity.ok(body); // 200 even when DEGRADED -- the Gateway itself is still functional
    }

    private boolean checkDatabase() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
