package com.qwikbrew.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Fixes WeightCalculatorWebFilter crash — Spring Cloud Gateway 4.1.0 known issue.
 *
 * WeightCalculatorWebFilter.onApplicationEvent() calls Flux.blockLast() on a
 * ReactiveDiscoveryClient during context finishRefresh(), crashing the gateway.
 *
 * Fix: declare a WebFilter bean with the SAME bean name ("weightCalculatorWebFilter").
 * Spring's bean definition override mechanism replaces the auto-configured
 * WeightCalculatorWebFilter with this no-op pass-through filter.
 * Since no routes use lb:// URIs, weight-based routing is not needed.
 */
@Configuration
public class GatewayConfig {

    @Bean("weightCalculatorWebFilter")
    @Order(10000)
    public WebFilter weightCalculatorWebFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> chain.filter(exchange);
    }
}
