package com.qwikbrew.gateway.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fixes WeightCalculatorWebFilter crash in Spring Cloud Gateway 4.1.0.
 *
 * The bean MUST exist (routeDefinitionRouteLocator depends on it by name).
 * But onApplicationEvent() must NOT call blockLast().
 *
 * Solution: override the bean with a subclass that no-ops onApplicationEvent.
 * Constructor takes ObjectProvider<ReactiveDiscoveryClient> and ObjectProvider<RouteLocator>.
 */
@Configuration
public class GatewayConfig {

    @Bean("weightCalculatorWebFilter")
    public WeightCalculatorWebFilter weightCalculatorWebFilter() {

        // No-op ReactiveDiscoveryClient — returns empty for all calls
        ReactiveDiscoveryClient noOpClient = new ReactiveDiscoveryClient() {
            @Override public String description() { return "no-op"; }
            @Override public Flux<ServiceInstance> getInstances(String s) { return Flux.empty(); }
            @Override public Flux<String> getServices() { return Flux.empty(); }
        };

        // ObjectProvider<ReactiveDiscoveryClient>
        ObjectProvider<ReactiveDiscoveryClient> discoveryProvider =
            new ObjectProvider<ReactiveDiscoveryClient>() {
                @Override public ReactiveDiscoveryClient getObject() { return noOpClient; }
                @Override public ReactiveDiscoveryClient getObject(Object... args) { return noOpClient; }
                @Override public ReactiveDiscoveryClient getIfAvailable() { return null; } // null = ifAvailable() callback never fires
                @Override public ReactiveDiscoveryClient getIfUnique() { return null; }
            };

        // ObjectProvider<RouteLocator>
        ObjectProvider<RouteLocator> routeLocatorProvider =
            new ObjectProvider<RouteLocator>() {
                @Override public RouteLocator getObject() { return null; }
                @Override public RouteLocator getObject(Object... args) { return null; }
                @Override public RouteLocator getIfAvailable() { return null; }
                @Override public RouteLocator getIfUnique() { return null; }
            };

        return new WeightCalculatorWebFilter(discoveryProvider, routeLocatorProvider) {
            @Override
            public void onApplicationEvent(ApplicationEvent event) {
                // No-op: prevent blockLast() call that crashes at finishRefresh()
            }

            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                return chain.filter(exchange);
            }
        };
    }
}
