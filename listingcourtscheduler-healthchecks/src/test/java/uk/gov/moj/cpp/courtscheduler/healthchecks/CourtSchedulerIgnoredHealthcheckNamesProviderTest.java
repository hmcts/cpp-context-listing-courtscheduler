package uk.gov.moj.cpp.courtscheduler.healthchecks;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.healthcheck.healthchecks.EventStoreHealthcheck.EVENT_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.FileStoreHealthcheck.FILE_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.JobStoreHealthcheck.JOB_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.SystemDatabaseHealthcheck.SYSTEM_DATABASE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.ViewStoreHealthcheck.VIEW_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.artemis.ArtemisHealthcheck.ARTEMIS_HEALTHCHECK_NAME;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
public class CourtSchedulerIgnoredHealthcheckNamesProviderTest {


    @InjectMocks
    private CourtSchedulerIgnoredHealthcheckNamesProvider systemIdMapperIgnoredHealthcheckNamesProvider;

    @Test
    public void shouldIgnoreFileStoreAndJobStoreHealthchecks() {

        final List<String> namesOfIgnoredHealthChecks = systemIdMapperIgnoredHealthcheckNamesProvider.getNamesOfIgnoredHealthChecks();

        assertThat(namesOfIgnoredHealthChecks.size(), is(6));
        assertThat(namesOfIgnoredHealthChecks, hasItems(EVENT_STORE_HEALTHCHECK_NAME));
        assertThat(namesOfIgnoredHealthChecks, hasItems(FILE_STORE_HEALTHCHECK_NAME));
        assertThat(namesOfIgnoredHealthChecks, hasItems(JOB_STORE_HEALTHCHECK_NAME));
        assertThat(namesOfIgnoredHealthChecks, hasItems(SYSTEM_DATABASE_HEALTHCHECK_NAME));
        assertThat(namesOfIgnoredHealthChecks, hasItems(VIEW_STORE_HEALTHCHECK_NAME));
        assertThat(namesOfIgnoredHealthChecks, hasItems(ARTEMIS_HEALTHCHECK_NAME));

    }
}