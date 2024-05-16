package uk.gov.moj.cpp.courtscheduler.healthchecks;

import org.slf4j.Logger;
import uk.gov.justice.services.healthcheck.api.Healthcheck;
import uk.gov.justice.services.healthcheck.api.HealthcheckResult;
import uk.gov.justice.services.healthcheck.utils.database.TableChecker;
import uk.gov.moj.cpp.systemidmapper.persistence.repository.CourtSchedulerDataSourceProvider;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static java.lang.String.format;
import static java.util.List.of;
import static uk.gov.justice.services.healthcheck.api.HealthcheckResult.failure;

public class CourtSchedulerDatabaseHealthcheck implements Healthcheck {

    public static final String COURT_SCHEDULER_DATABASE_HEALTHCHECK_NAME = "courtscheduler-database-healthcheck";

    protected static final List<String> TABLE_NAMES = of("business_type");

    @Inject
    private CourtSchedulerDataSourceProvider courtSchedulerDataSourceProvider;

    @Inject
    private TableChecker tableChecker;

    @Inject
    @SuppressWarnings("squid:S1312")
    private Logger logger;

    @Override
    public String getHealthcheckName() {
        return COURT_SCHEDULER_DATABASE_HEALTHCHECK_NAME;
    }

    @Override
    public String healthcheckDescription() {
        return "Checks connectivity to the courtscheduler database and that all its tables are available";
    }

    @Override
    public HealthcheckResult runHealthcheck() {

        final DataSource jobStoreDataSource = courtSchedulerDataSourceProvider.getDataSource();

        try {
            return tableChecker.checkTables(TABLE_NAMES, jobStoreDataSource);

        } catch (final SQLException e) {
            logger.error("Healthcheck for courtscheduler database failed.", e);
            return failure(format("Exception thrown accessing courtscheduler database. %s: %s", e.getClass().getName(), e.getMessage()));
        }
    }
}
