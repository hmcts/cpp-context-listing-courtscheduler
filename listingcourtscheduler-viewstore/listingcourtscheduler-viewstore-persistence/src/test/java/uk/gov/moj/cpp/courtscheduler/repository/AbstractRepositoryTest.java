package uk.gov.moj.cpp.courtscheduler.repository;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for {@code @DataJpaTest}-driven repository tests in this module.
 *
 * <p>Spins up a Postgres container, runs the production Liquibase changelog against it,
 * and component-scans this module's {@code repository} package so the
 * {@code @Repository}-annotated classes (which are plain {@code EntityManager} wrappers,
 * not Spring Data interfaces) are available for {@code @Autowired}.</p>
 *
 * <p>The original CDI-based tests used DeltaSpike's {@code };
 * this is the Spring Boot equivalent and preserves the original test bodies intact.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class AbstractRepositoryTest {

    /**
     * Random entity generator used by the in-tree repository tests in place of the
     * static {@code EnhancedRandom.random(...)} call. Strings are capped at 10 chars
     * because several columns in the production schema are {@code VARCHAR(10)}
     * ({@code oucode}, {@code rota_business_type}, {@code court_session}, {@code session_type});
     * the unconfigured default produces 30-char strings which trip Postgres on insert.
     */
    protected static final io.github.benas.randombeans.api.EnhancedRandom RANDOM =
            io.github.benas.randombeans.EnhancedRandomBuilder.aNewEnhancedRandomBuilder()
                    .stringLengthRange(5, 10)
                    .build();

    /**
     * Drop-in for the legacy static {@code EnhancedRandom.random(Class)} import — kept
     * with the same name + signature so the original test bodies don't have to change.
     */
    protected static <T> T random(final Class<T> type) {
        return RANDOM.nextObject(type);
    }

    /**
     * The {@code @Autowired} hook the test fixture helpers below reach for. Production
     * code goes through {@code @Inject} on the concrete repository classes; for test
     * fixtures we use the same {@code save} entry point.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CourtScheduleRepository courtScheduleRepositoryForFixtures;

    /**
     * Create + persist a random {@link uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule}
     * and return its id. Used to satisfy the {@code allocated_listings_court_schedule_id_fk}
     * foreign key — added to the schema in changeset 033, after these legacy tests were written.
     */
    protected String persistRandomCourtSchedule() {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule cs = random(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule.class);
        courtScheduleRepositoryForFixtures.save(cs);
        return cs.getCourtScheduleId();
    }

    /**
     * Build a random {@link uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing}
     * whose {@code courtScheduleId} points at a freshly-persisted {@code CourtSchedule}, so
     * inserting it doesn't violate the FK. Replaces bare {@code random(AllocatedListing.class)}
     * in tests written before changeset 033.
     */
    protected uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing newAllocatedListingWithSavedSchedule() {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing al =
                random(uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing.class);
        al.setCourtScheduleId(persistRandomCourtSchedule());
        return al;
    }

    /**
     * Build a random {@link uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary}
     * whose composite key references a freshly-persisted {@code CourtSchedule}, so inserting
     * it doesn't violate the FK on {@code court_schedule_judiciary.court_schedule_id} (added
     * in changeset 033).
     */
    protected uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary newCourtScheduleJudiciaryWithSavedSchedule() {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary csj =
                random(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary.class);
        final String judiciaryId = csj.getId() == null ? java.util.UUID.randomUUID().toString() : csj.getId().getJudiciaryId();
        csj.setId(new uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey(
                persistRandomCourtSchedule(), judiciaryId));
        return csj;
    }

    /**
     * Single Postgres container shared by every {@code @DataJpaTest} class in this JVM.
     * Started in a static initializer (not via {@code @Container}) so the same instance
     * is reused regardless of how many subclasses inherit this base — each
     * {@code @Container}-annotated subclass would otherwise start its own container.
     * Bumping {@code max_connections} avoids acquisition timeouts when many
     * {@code @DataJpaTest} {@code ApplicationContext}s coexist (each carries its own
     * Hikari pool, even with context caching).
     */
    static final PostgreSQLContainer<?> POSTGRES;
    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("courtscheduler")
                .withUsername("courtscheduler")
                .withPassword("courtscheduler")
                .withCommand("postgres", "-c", "max_connections=400");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.liquibase.change-log",
                () -> "classpath:liquibase/courtscheduler-view-store-db-changelog.xml");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
        // Each @DataJpaTest spins up its own ApplicationContext, and Postgres has a
        // per-instance connection cap (~100). With ~10 test classes each holding a
        // default Hikari pool of 10, connection acquisition starts timing out when
        // more than a handful of contexts coexist. Cap each context's pool small.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
    }

    /**
     * Make this module's {@code @Repository} classes (and the JPA model) visible to
     * {@code @DataJpaTest}, which by default only scans Spring Data interfaces. Also
     * acts as the {@code @SpringBootConfiguration} anchor that {@code @DataJpaTest}
     * searches for upwards from each test class — none exists in this test-only
     * module otherwise.
     */
    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    // `service` is included so CourtScheduleRetryService (an @Service the
    // repository implementation @Inject-s via @Lazy) is resolvable inside
    // @DataJpaTest contexts — without it the repository proxy is built fine
    // but the first call into a method that touches the retry service fails
    // with NoSuchBeanDefinitionException.
    @ComponentScan(basePackages = {
            "uk.gov.moj.cpp.courtscheduler.repository",
            "uk.gov.moj.cpp.courtscheduler.service"
    })
    @org.springframework.boot.persistence.autoconfigure.EntityScan(basePackages = "uk.gov.moj.cpp.courtscheduler.persist.entity")
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = "uk.gov.moj.cpp.courtscheduler.repository")
    static class TestRepositoriesConfig {
    }
}
