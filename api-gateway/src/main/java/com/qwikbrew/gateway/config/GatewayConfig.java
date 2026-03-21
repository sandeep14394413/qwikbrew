package com.qwikbrew.gateway.config;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Fixes WeightCalculatorWebFilter crash — Spring Cloud Gateway 4.1.0.
 *
 * Requirements (from Spring's bean registry):
 *   - Bean name must be "weightCalculatorWebFilter"
 *   - Must implement WebFilter          (used by gateway filter chain)
 *   - Must implement ApplicationListener (registered by Spring event multicaster)
 *
 * Solution: provide a bean that satisfies both interfaces with no-op implementations.
 * No subclassing of WeightCalculatorWebFilter needed — avoids constructor issues entirely.
 * onApplicationEvent() is a no-op so blockLast() is never called.
 */
@Configuration
public class GatewayConfig {

    @Bean("weightCalculatorWebFilter")
    public CombinedWebFilterAndListener weightCalculatorWebFilter() {
        return new CombinedWebFilterAndListener();
    }

    public static class CombinedWebFilterAndListener
            implements WebFilter, ApplicationListener<ApplicationEvent> {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            // Pass all requests through — weight routing not used (no lb:// routes)
            return chain.filter(exchange);
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            // No-op: prevents the blockLast() call that crashes Spring Cloud Gateway 4.1.0
            // when WeightCalculatorWebFilter tries to refresh routes via ReactiveDiscoveryClient
        }
    }
}
