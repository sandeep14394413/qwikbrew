package com.qwikbrew.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @EnableDiscoveryClient removed — gateway uses direct K8s DNS (http://service-name:port).
// No Eureka server runs in the cluster. Excluding all discovery autoconfiguration
// at the Java level (not just YAML) to prevent WeightCalculatorWebFilter crash
// that occurs when reactive discovery client is on the classpath.
@SpringBootApplication(exclude = {
    // Eureka client
    org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration.class,
    org.springframework.cloud.netflix.eureka.EurekaDiscoveryClientAutoConfiguration.class,
    // Reactive Eureka (Spring Cloud Gateway is reactive — this is the one that causes
    // WeightCalculatorWebFilter to crash at finishRefresh via RouteRefreshListener)
    org.springframework.cloud.netflix.eureka.reactive.EurekaReactiveDiscoveryClientAutoConfiguration.class,
    // Composite reactive discovery (used by WeightCalculatorWebFilter to list all lb:// targets)
    org.springframework.cloud.client.discovery.composite.reactive.ReactiveCompositeDiscoveryClientAutoConfiguration.class,
    // LoadBalancer (no lb:// URIs in routes — all routes use direct http://service:port)
    org.springframework.cloud.loadbalancer.config.LoadBalancerAutoConfiguration.class,
    org.springframework.cloud.loadbalancer.config.BlockingLoadBalancerClientAutoConfiguration.class
})
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
