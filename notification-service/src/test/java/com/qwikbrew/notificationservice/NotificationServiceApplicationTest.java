package com.qwikbrew.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring context loads correctly.
 * Guarantees mvn test always produces surefire XML output in CI.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceApplicationTest {

    @Test
    void contextLoads() {
        // Spring context loads without error = test passes.
        // Add service-specific tests in separate test classes.
    }
}
