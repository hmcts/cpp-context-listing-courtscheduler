package uk.gov.moj.cpp.courtscheduler.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Explicit primary {@link ObjectMapper} bean. The legacy
 * {@code uk.gov.moj.cpp.courtscheduler.common.converter} package and
 * cp-auth-rules-filter both inject {@code ObjectMapper} by type; declaring it as
 * {@code @Primary} avoids ambiguity with any framework-internal mappers and
 * guarantees there is exactly one primary candidate.
 */
@Configuration
public class JacksonObjectMapperConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        // findAndRegisterModules() picks up jackson-datatype-jsr310 (LocalDate, LocalDateTime, ...)
        // so domain types like IdResponse#hearingDate serialize cleanly through the legacy
        // ObjectToJsonObjectConverter path.
        return new ObjectMapper()
                .findAndRegisterModules()
                // JavaTimeModule defaults to WRITE_DATES_AS_TIMESTAMPS=true, which serializes
                // java.time.LocalDate as [2025,12,15] instead of "2025-12-15". Legacy IT
                // assertions (e.g. HearingIdIT) read these fields with .getString(), so
                // the string form is required.
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(new JakartaJsonModule());
    }
}
