package uk.gov.moj.cpp.courtscheduler.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 contributes both {@code requestMappingHandlerMapping} (the MVC
 * dispatcher) and {@code controllerEndpointHandlerMapping} (the actuator
 * controller-endpoints dispatcher) as beans of type
 * {@code RequestMappingHandlerMapping}.
 *
 * <p>{@code cp-auth-rules-filter:2.0.0}'s
 * {@code AuthzAutoConfiguration#httpAuthzFilterRegistration(...)} injects an
 * {@code ObjectProvider<RequestMappingHandlerMapping>} and calls
 * {@code getIfAvailable()}, which throws when multiple beans are registered
 * because neither is marked primary. Marking the MVC dispatcher mapping as the
 * primary candidate fixes the ambiguity without having to disable any actuator
 * features.</p>
 */
@Configuration
public class PrimaryHandlerMappingConfig {

    @Bean
    public static BeanFactoryPostProcessor primaryRequestMappingHandlerMapping() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("requestMappingHandlerMapping")) {
                beanFactory.getBeanDefinition("requestMappingHandlerMapping").setPrimary(true);
            }
        };
    }
}
