package uk.gov.moj.cpp.courtscheduler.persistence.repository;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.jdbc.persistence.JdbcDataSourceProvider;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.systemidmapper.persistence.repository.CourtSchedulerDataSourceProvider;


@ExtendWith(MockitoExtension.class)
public class CourtSchedulerDataSourceProviderTest {

    @Mock
    private JdbcDataSourceProvider jdbcDataSourceProvider;

    @InjectMocks
    private CourtSchedulerDataSourceProvider courtSchedulerDataSourceProvider;

    @Test
    public void shouldGetTheSystemIdMapperDataSourceUsingTheCorrectJndiName() {

        final DataSource dataSource = mock(DataSource.class);

        when(jdbcDataSourceProvider.getDataSource("java:/DS.courtscheduler")).thenReturn(dataSource);

        assertThat(courtSchedulerDataSourceProvider.getDataSource(), is(dataSource));
    }
}