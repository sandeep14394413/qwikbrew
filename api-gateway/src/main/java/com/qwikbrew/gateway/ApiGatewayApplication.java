package com.qwikbrew.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Eureka dependency removed from pom.xml — gateway uses direct K8s DNS (http://service-name:port).
// No service discovery needed: each route targets a K8s ClusterIP service by name.
@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
