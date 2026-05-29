package uk.gov.moj.cpp.courtscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @EnableAsync} enables Spring's {@code @Async} stereotype used in place
 * of the legacy EJB {@code @Asynchronous} for fire-and-forget operations
 * (notably rota file processing in {@code RotaFileCaptureAndProcessTriggerService}).
 */
@SpringBootApplication
@EnableAsync
@SuppressWarnings("HideUtilityClassConstructor")
public class Application {

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
