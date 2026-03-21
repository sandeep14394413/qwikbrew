package com.qwikbrew.gateway.config;

import org.springframework.context.annotation.Configuration;

/**
 * Gateway configuration placeholder.
 * WeightCalculatorWebFilter crash is prevented via:
 *   spring.cloud.discovery.reactive.enabled=false  (in application.yml)
 * This stops all ReactiveDiscoveryClient beans from registering, so
 * WeightCalculatorWebFilter.ifAvailable() never fires and blockLast() is never called.
 */
@Configuration
public class GatewayConfig {
    // Route config is in application.yml
    // No bean overrides needed — discovery is disabled at the property level
}
