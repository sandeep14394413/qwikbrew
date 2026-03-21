package com.qwikbrew.gateway.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Permanently fixes WeightCalculatorWebFilter crash in Spring Cloud Gateway 4.1.0.
 *
 * The filter registers itself as an ApplicationListener and calls Flux.blockLast()
 * on a ReactiveDiscoveryClient during finishRefresh(). No YAML property or
 * @SpringBootApplication(exclude) can prevent this — the filter is always
 * registered by GatewayAutoConfiguration regardless of discovery settings.
 *
 * Fix: remove the bean definition from the BeanDefinitionRegistry BEFORE the bean
 * is instantiated. BeanDefinitionRegistryPostProcessor runs before any beans are
 * created, so the filter never exists in the context and never registers as a listener.
 *
 * All routes use direct http://service-name:port K8s DNS — weight-based routing
 * is not used and this filter is not needed.
 */
@Configuration
public class GatewayConfig {

    @Component
    public static class RemoveWeightCalculatorFilter
            implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
                throws BeansException {
            String beanName = "weightCalculatorWebFilter";
            if (registry.containsBeanDefinition(beanName)) {
                registry.removeBeanDefinition(beanName);
            }
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
                throws BeansException {
            // nothing needed here
        }
    }
}
