package com.qwikbrew.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * Route all microservice traffic through the gateway.
     * Each route applies JWT authentication via a custom filter.
     */
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()

            // ── User Service ──────────────────────────────────────────────
            .route("user-service-auth", r -> r
                .path("/api/v1/users/login", "/api/v1/users/register", "/api/v1/users/refresh-token")
                .uri("lb://user-service"))                  // no auth required

            .route("user-service", r -> r
                .path("/api/v1/users/**")
                .filters(f -> f.filter(new JwtAuthFilter()))
                .uri("lb://user-service"))

            // ── Menu Service (public read, auth for admin write) ──────────
            .route("menu-service-public", r -> r
                .path("/api/v1/menu/**")
                .and().method(HttpMethod.GET)
                .uri("lb://menu-service"))

            .route("menu-service-admin", r -> r
                .path("/api/v1/menu/**")
                .filters(f -> f.filter(new JwtAuthFilter()).filter(new RoleCheckFilter("ADMIN", "CAFE_STAFF")))
                .uri("lb://menu-service"))

            // ── Order Service ─────────────────────────────────────────────
            .route("order-service", r -> r
                .path("/api/v1/orders/**")
                .filters(f -> f
                    .filter(new JwtAuthFilter())
                    .retry(retryConfig -> retryConfig.setRetries(3).setMethods(HttpMethod.GET))
                    .circuitBreaker(cb -> cb.setName("orderCircuitBreaker").setFallbackUri("forward:/fallback/orders")))
                .uri("lb://order-service"))

            // ── Payment Service ───────────────────────────────────────────
            .route("payment-service", r -> r
                .path("/api/v1/payments/**")
                .filters(f -> f.filter(new JwtAuthFilter()))
                .uri("lb://payment-service"))

            // ── Notification Service ──────────────────────────────────────
            .route("notification-service", r -> r
                .path("/api/v1/notifications/**")
                .filters(f -> f.filter(new JwtAuthFilter()))
                .uri("lb://notification-service"))

            .build();
    }

    /** CORS — allow the React/mobile frontend to call the gateway */
    @Bean
    public CorsWebFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
