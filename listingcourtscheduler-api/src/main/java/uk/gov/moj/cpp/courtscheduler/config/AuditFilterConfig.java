package uk.gov.moj.cpp.courtscheduler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuditFilterConfig {

    @Bean
    public String auditOpenApiRestSpec(
            @Value("${audit.http.openapi-rest-spec:openapi/courtscheduler-api.openapi.yml}")
            final String openApiRestSpec) {
        return openApiRestSpec;
    }

    @Bean
    public Boolean auditHttpEnabled(@Value("${audit.http.enabled:false}") final boolean enabled) {
        return enabled;
    }
}
