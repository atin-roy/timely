package com.atinroy.timely.health;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final DataSource dataSource;

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok("OK");
            }
        } catch (SQLException ignored) {
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Database unavailable");
    }
}
