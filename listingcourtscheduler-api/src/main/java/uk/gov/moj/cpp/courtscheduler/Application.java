package uk.gov.moj.cpp.courtscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @EnableAsync} enables Spring's {@code @Async} stereotype used in place
 * of the legacy EJB {@code @Asynchronous} for fire-and-forget operations
 * (notably rota file processing in {@code RotaFileCaptureAndProcessTriggerService}).
 *
 * <p>{@code @EntityScan} is explicit rather than left to the default same-package behaviour:
 * uk.gov.hmcts.cp:task-manager-service's own auto-configuration registers an
 * {@code EntityScanPackages} bean for its {@code Job} entity's package, and pulling that
 * dependency in leaves this app's own entities (under
 * {@code uk.gov.moj.cpp.courtscheduler.persist.entity}) unscanned unless both packages are
 * listed here explicitly.</p>
 */
@SpringBootApplication
@EnableAsync
@EntityScan(basePackages = {
        "uk.gov.moj.cpp.courtscheduler.persist.entity",
        "uk.gov.hmcts.cp.taskmanager.persistence.entity"
})
@SuppressWarnings("HideUtilityClassConstructor")
public class Application {

    public static void main(final String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
