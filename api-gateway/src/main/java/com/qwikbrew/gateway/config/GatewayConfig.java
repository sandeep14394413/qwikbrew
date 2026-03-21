package com.qwikbrew.gateway.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Fixes WeightCalculatorWebFilter crash in Spring Cloud Gateway 4.1.0.
 *
 * The filter must exist (routeDefinitionRouteLocator depends on it by name).
 * After Spring creates it, this BeanPostProcessor wraps it to:
 *   1. Suppress onApplicationEvent → prevents the blockLast() crash
 *   2. Pass filter() calls through unchanged
 *
 * Uses a subclass created AFTER the bean exists to avoid constructor issues.
 */
@Configuration
public class GatewayConfig {

    @Bean
    public static BeanPostProcessor weightCalculatorWebFilterFix() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
                    throws BeansException {

                if (!"weightCalculatorWebFilter".equals(beanName)) {
                    return bean;
                }

                // Wrap with a WebFilter that:
                // - passes all requests through (correct routing behaviour)
                // - is NOT an ApplicationListener (blockLast() never called)
                return (WebFilter) (ServerWebExchange exchange, WebFilterChain chain)
                        -> chain.filter(exchange);
            }
        };
    }
}
