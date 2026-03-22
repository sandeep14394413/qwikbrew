package com.qwikbrew.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

// WeightCalculatorWebFilter crash fix: allow-bean-definition-overriding MUST be set
// programmatically via SpringApplicationBuilder — setting it in application.yml is
// too late because Spring Cloud Gateway registers the filter before yml is loaded.
// Our GatewayConfig provides a no-op replacement bean named "weightCalculatorWebFilter".
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(ApiGatewayApplication.class)
            .properties(
                "spring.main.allow-bean-definition-overriding=true",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.discovery.reactive.enabled=false"
            )
            .run(args);
    }
}
