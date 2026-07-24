package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static java.lang.String.format;

import java.sql.Connection;
import java.sql.DriverManager;
import org.postgresql.Driver;

/**
 * Migrated in place from {@code uk.gov.justice.services.jdbc.persistence.DataAccessException}
 * to a plain {@link RuntimeException}. The host/port honour the {@code db.host} system property
 * which defaults to {@code localhost:55433} (the dockerised Postgres exposed by
 * {@code docker/docker-compose.integration.yml} in {@code listingcourtscheduler-springboot}).
 */
public class ConnectionProvider {

    public Connection getNewConnection(final String username, final String password, final String databaseName) {
        final String host = System.getProperty("db.host", "localhost:55433");
        final String url = format("jdbc:postgresql://%s/%s", host, databaseName);
        try {
            DriverManager.registerDriver(new Driver());
            return DriverManager.getConnection(url, username, password);
        } catch (Exception e) {
            throw new RuntimeException(format("Failed to get JDBC connection to %s database. url: '%s', username '%s'",
                    databaseName, url, username), e);
        }
    }
}
