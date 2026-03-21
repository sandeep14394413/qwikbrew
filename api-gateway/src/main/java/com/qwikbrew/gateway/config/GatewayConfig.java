package com.qwikbrew.gateway.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

/**
 * Fixes WeightCalculatorWebFilter crash in Spring Cloud Gateway 4.1.0.
 *
 * Root cause: WeightCalculatorWebFilter.onApplicationEvent() calls
 *   discoveryClientProvider.ifAvailable(client -> Flux.blockLast())
 * If any ReactiveDiscoveryClient bean is present it invokes blockLast() which
 * fails when the reactive chain errors or when the stream is non-empty.
 *
 * Fix: provide a no-op ReactiveDiscoveryClient that returns Flux.empty() for
 * all service queries. blockLast() on Flux.empty() completes immediately with
 * null — no error, no block.
 *
 * All routing uses direct K8s DNS (http://service-name:port) so this client
 * is never actually used for routing.
 */
@Configuration
public class GatewayConfig {

    @Bean
    @Primary
    public ReactiveDiscoveryClient noOpReactiveDiscoveryClient() {
        return new ReactiveDiscoveryClient() {
            @Override
            public String description() {
                return "No-op discovery client — all routes use direct K8s DNS";
            }

            @Override
            public Flux<ServiceInstance> getInstances(String serviceId) {
                return Flux.empty();
            }

            @Override
            public Flux<String> getServices() {
                return Flux.empty();
            }
        };
    }
}
