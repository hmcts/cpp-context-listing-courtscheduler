package uk.gov.moj.cpp.courtscheduler.healthchecks;

import static java.util.Optional.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.healthcheck.api.HealthcheckResult;
import uk.gov.justice.services.healthcheck.utils.database.TableChecker;
import uk.gov.moj.cpp.systemidmapper.persistence.repository.CourtSchedulerDataSourceProvider;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
public class CourtSchedulerDatabaseHealthcheckTest {

    @Mock
    private CourtSchedulerDataSourceProvider courtSchedulerDataSourceProvider;

    @Mock
    private TableChecker tableChecker;

    @Mock
    private Logger logger;

    @InjectMocks
    private CourtSchedulerDatabaseHealthcheck courtSchedulerDatabaseHealthcheck;

    @Test
    public void shouldReturnCorrectHealthcheckName() throws Exception {

        assertThat(courtSchedulerDatabaseHealthcheck.getHealthcheckName(), CoreMatchers.is(CourtSchedulerDatabaseHealthcheck.COURT_SCHEDULER_DATABASE_HEALTHCHECK_NAME));
    }

    @Test
    public void shouldReturnCorrectHealthcheckDescription() throws Exception {

        assertThat(courtSchedulerDatabaseHealthcheck.healthcheckDescription(), is("Checks connectivity to the courtscheduler database and that all its tables are available"));
    }

    @Test
    public void shouldGetListOfExpectedTablesFromEventStoreAsHealthcheck() throws Exception {

        final DataSource systemDataSource = mock(DataSource.class);
        final HealthcheckResult healthcheckResult = mock(HealthcheckResult.class);

        when(courtSchedulerDataSourceProvider.getDataSource()).thenReturn(systemDataSource);
        when(tableChecker.checkTables(CourtSchedulerDatabaseHealthcheck.TABLE_NAMES, systemDataSource)).thenReturn(healthcheckResult);

        assertThat(courtSchedulerDatabaseHealthcheck.runHealthcheck(), is(healthcheckResult));
    }

    @Test
    public void shouldReturnHealthcheckFailureIfAccessingTheEventStoreThrowsSqlException() throws Exception {

        final SQLException sqlException = new SQLException("Oops");
        final DataSource systemDataSource = mock(DataSource.class);

        when(courtSchedulerDataSourceProvider.getDataSource()).thenReturn(systemDataSource);
        when(tableChecker.checkTables(CourtSchedulerDatabaseHealthcheck.TABLE_NAMES, systemDataSource)).thenThrow(sqlException);

        final HealthcheckResult healthcheckResult = courtSchedulerDatabaseHealthcheck.runHealthcheck();

        assertThat(healthcheckResult.isPassed(), is(false));
        assertThat(healthcheckResult.getErrorMessage().isPresent(), is(true));
        assertThat(healthcheckResult.getErrorMessage(), is(of("Exception thrown accessing courtscheduler database. java.sql.SQLException: Oops")));

        verify(logger).error("Healthcheck for courtscheduler database failed.", sqlException);
    }

}