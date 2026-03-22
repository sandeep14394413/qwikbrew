package com.qwikbrew.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// All routes use direct K8s DNS (http://service-name:port) — no service discovery needed.
// WeightCalculatorWebFilter crash is fixed via GatewayConfig which provides a no-op
// ReactiveDiscoveryClient bean so blockLast() completes on Flux.empty().
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
