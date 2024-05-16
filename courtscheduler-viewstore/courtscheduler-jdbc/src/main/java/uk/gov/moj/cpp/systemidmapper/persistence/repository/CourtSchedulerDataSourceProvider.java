package uk.gov.moj.cpp.systemidmapper.persistence.repository;

import uk.gov.justice.services.jdbc.persistence.JdbcDataSourceProvider;

import javax.inject.Inject;
import javax.sql.DataSource;

public class CourtSchedulerDataSourceProvider {

    private static final String DATASOURCE_JNDI_NAME = "java:/DS.courtscheduler";

    @Inject
    private JdbcDataSourceProvider jdbcDataSourceProvider;

    public DataSource getDataSource() {
        return jdbcDataSourceProvider.getDataSource(DATASOURCE_JNDI_NAME);
    }
}
