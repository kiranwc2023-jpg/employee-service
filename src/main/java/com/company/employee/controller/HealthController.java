package com.company.employee.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * HealthController — provides custom application info endpoints.
 *
 * Spring Actuator already provides /actuator/health for Kubernetes probes.
 * This controller provides business-level health info at a friendlier URL.
 *
 * GET /hello  → simple greeting, useful for smoke tests
 * GET /health → custom health summary with version and uptime
 */
@RestController
public class HealthController {

    // @Value("${...}") injects a value from application.properties (or environment variables).
    // "${app.version:1.0.0}" means:
    //   - Read the property "app.version" from application.properties
    //   - If not found, use the default "1.0.0" (the part after ':')
    //
    // The CI pipeline will override app.version at build time via:
    //   -Dapp.version=1.2.3 or via application.properties substitution
    //
    // Without @Value, you'd hardcode the version string — it would never update automatically.
    @Value("${app.version:1.0.0}")
    private String appVersion;

    // @Value("${spring.application.name:employee-service}")
    // Injects the application name defined in application.properties.
    // The value comes from: spring.application.name=employee-service
    @Value("${spring.application.name:employee-service}")
    private String appName;

    // Records the JVM startup time. Used to compute uptime below.
    // Instant.now() at class initialization time = the moment Spring created this bean.
    private final Instant startTime = Instant.now();

    // ─────────────────────────────────────────────────────────────────
    // GET /hello
    // The simplest possible endpoint — used by QA, developers, and
    // smoke tests to verify the service is responding at all.
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        return ResponseEntity.ok(Map.of(
            "message", "Hello from Kiran" + appName + "!",
            "version", appVersion,
            // Instant.now().toString() produces ISO-8601 timestamp: 2025-01-15T10:30:00Z
            "timestamp", Instant.now().toString()
        ));
    }

    // ─────────────────────────────────────────────────────────────────
    // GET /health
    // Custom health endpoint. Returns application metadata.
    // Note: This is SEPARATE from /actuator/health.
    // /actuator/health is used by Kubernetes — do NOT modify it.
    // /health is a developer-friendly status page.
    // ─────────────────────────────────────────────────────────────────
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        // Compute uptime in seconds since application start
        long uptimeSeconds = Instant.now().getEpochSecond() - startTime.getEpochSecond();

        // Map.of() is limited to 10 entries in Java.
        // For more entries, use Map.ofEntries() or new HashMap<>()
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", appName,
            "version", appVersion,
            "uptime_seconds", uptimeSeconds,
            "timestamp", Instant.now().toString()
        ));
    }

}
