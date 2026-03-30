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
 * Fixes WeightCalculatorWebFilter crash — present in Spring Cloud Gateway 4.1.x.
 *
 * The bug: onApplicationEvent() calls Flux.blockLast() on a ReactiveDiscoveryClient.
 * This crashes at context finishRefresh() regardless of Spring Cloud version in 4.1.x.
 *
 * Fix: provide a bean named exactly "weightCalculatorWebFilter" that:
 *   1. Implements WebFilter       → satisfies routeDefinitionRouteLocator dependency
 *   2. Implements ApplicationListener → satisfies Spring event multicaster type check
 *   3. onApplicationEvent() is a no-op → blockLast() is NEVER called
 *
 * allow-bean-definition-overriding=true is set in SpringApplicationBuilder
 * (in ApiGatewayApplication.main) so this bean WINS over the auto-configured one.
 */
@Configuration
public class GatewayConfig {

    @Bean("weightCalculatorWebFilter")
    public CombinedFilter weightCalculatorWebFilter() {
        return new CombinedFilter();
    }

    public static class CombinedFilter
            implements WebFilter, ApplicationListener<ApplicationEvent> {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            return chain.filter(exchange);
        }

        @Override
        public void onApplicationEvent(ApplicationEvent event) {
            // No-op: prevents blockLast() crash in WeightCalculatorWebFilter
        }
    }
}
