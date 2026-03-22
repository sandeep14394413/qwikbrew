package com.qwikbrew.gateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * Gateway configuration.
 * Routes are defined in application.yml.
 * WeightCalculatorWebFilter crash was fixed by upgrading Spring Cloud to 2023.0.3
 * which ships Spring Cloud Gateway 4.1.3 with the blockLast() bug resolved.
 */
@Configuration
public class GatewayConfig {
    // No workarounds needed — Spring Cloud Gateway 4.1.3 starts cleanly
}
