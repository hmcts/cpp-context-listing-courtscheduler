package uk.gov.moj.cpp.courtscheduler.integration.utils;

import org.postgresql.Driver;
import uk.gov.justice.services.jdbc.persistence.DataAccessException;

import java.sql.Connection;
import java.sql.DriverManager;

import static java.lang.String.format;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;

public class ConnectionProvider {

    public Connection getNewConnection(final String username, final String password, final String databaseName) {

        final String host = getHost();

        final String url = format("jdbc:postgresql://%s/%s", host, databaseName);

        try {
            DriverManager.registerDriver(new Driver());
            return DriverManager.getConnection(url, username, password);
        } catch (Exception e) {
            final String message = format("Failed to get JDBC connection to %s database. url: '%s', username '%s', password '%s'",
                    databaseName,
                    url,
                    username,
                    password);

            throw new DataAccessException(message, e);
        }
    }
}
