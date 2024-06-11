package uk.gov.moj.cpp.courtscheduler.healthchecks;

import uk.gov.justice.services.healthcheck.api.DefaultIgnoredHealthcheckNamesProvider;

import javax.enterprise.inject.Specializes;
import java.util.List;

import static java.util.Arrays.asList;
import static uk.gov.justice.services.healthcheck.healthchecks.EventStoreHealthcheck.EVENT_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.FileStoreHealthcheck.FILE_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.JobStoreHealthcheck.JOB_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.SystemDatabaseHealthcheck.SYSTEM_DATABASE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.ViewStoreHealthcheck.VIEW_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.artemis.ArtemisHealthcheck.ARTEMIS_HEALTHCHECK_NAME;

@Specializes
public class CourtSchedulerIgnoredHealthcheckNamesProvider extends DefaultIgnoredHealthcheckNamesProvider {

    public CourtSchedulerIgnoredHealthcheckNamesProvider() {
        // This constructor is required by CDI.
    }

    @Override
    public List<String> getNamesOfIgnoredHealthChecks() {
        return asList(
                EVENT_STORE_HEALTHCHECK_NAME,
                FILE_STORE_HEALTHCHECK_NAME,
                JOB_STORE_HEALTHCHECK_NAME,
                SYSTEM_DATABASE_HEALTHCHECK_NAME,
                VIEW_STORE_HEALTHCHECK_NAME,
                ARTEMIS_HEALTHCHECK_NAME
        );
    }

}