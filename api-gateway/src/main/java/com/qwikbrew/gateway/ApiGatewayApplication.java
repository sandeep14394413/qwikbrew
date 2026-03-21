package com.qwikbrew.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Exclude SimpleDiscoveryClientAutoConfiguration — it is part of spring-cloud-commons
// (bundled with spring-cloud-starter-gateway) and creates a ReactiveDiscoveryClient bean
// even without any service registry. WeightCalculatorWebFilter.onApplicationEvent()
// calls discoveryClientProvider.ifAvailable() → triggers blockLast() → crashes.
// All routes use direct http://service-name:port K8s DNS — no discovery client needed.
@SpringBootApplication(exclude = {
    org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration.class,
    org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration.class
})
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
