package com.qwikbrew.gateway.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fixes WeightCalculatorWebFilter crash — Spring Cloud Gateway 4.1.0 known issue.
 *
 * WeightCalculatorWebFilter.onApplicationEvent() calls Flux.blockLast() on whatever
 * ReactiveDiscoveryClient it finds. This crashes at finishRefresh() even when the
 * flux is empty. The fix: subclass the filter and override onApplicationEvent()
 * with a no-op. Since no routes use lb:// URIs, weight-based routing is unused.
 */
@Configuration
public class GatewayConfig {

    @Bean
    @Primary
    @Order(-1)
    public WeightCalculatorWebFilter weightCalculatorWebFilter() {
        // Build ObjectProvider wrappers for the constructor
        ReactiveDiscoveryClient noOp = new ReactiveDiscoveryClient() {
            @Override public String description() { return "no-op"; }
            @Override public Flux<ServiceInstance> getInstances(String serviceId) { return Flux.empty(); }
            @Override public Flux<String> getServices() { return Flux.empty(); }
        };

        ObjectProvider<ReactiveDiscoveryClient> discoveryProvider =
            new ObjectProvider<ReactiveDiscoveryClient>() {
                @Override public ReactiveDiscoveryClient getObject() { return noOp; }
                @Override public ReactiveDiscoveryClient getObject(Object... args) { return noOp; }
                @Override public ReactiveDiscoveryClient getIfAvailable() { return noOp; }
                @Override public ReactiveDiscoveryClient getIfUnique() { return noOp; }
            };

        return new WeightCalculatorWebFilter(discoveryProvider, null) {
            @Override
            public void onApplicationEvent(ApplicationEvent event) {
                // No-op: suppress the blockLast() call that crashes at context finishRefresh.
                // Weight routing is unused — all routes use direct http://service-name:port.
            }

            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                return chain.filter(exchange);
            }
        };
    }
}
