package com.qwikbrew.gateway;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

// Keep critical gateway bootstrap flags in code so they are applied before
// Spring Cloud Gateway listener registration during context startup.
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(ApiGatewayApplication.class)
            .properties(
                "spring.main.allow-bean-definition-overriding=true",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.discovery.reactive.enabled=false",
                "spring.cloud.gateway.server.webflux.route-refresh-listener.enabled=false",
                "spring.cloud.gateway.server.webflux.predicate.weight.enabled=false"
            )
            .run(args);
    }
}
