package uk.gov.moj.cpp.courtscheduler.api.service.helper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for parsing dates from rota file data.
 */
@ApplicationScoped
public class DateParsingUtility {

    private static final Logger logger = LoggerFactory.getLogger(DateParsingUtility.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Parses a session date string to a LocalDate.
     *
     * @param sessionDateStr the session date string in format "yyyy-MM-dd"
     * @return the parsed LocalDate, or null if parsing fails
     */
    public LocalDate parseSessionDate(final String sessionDateStr) {
        try {
            return LocalDate.parse(sessionDateStr, DATE_FORMATTER);
        } catch (final Exception ex) {
            logger.warn("Failed to parse session date: {}", sessionDateStr, ex);
            return null;
        }
    }
}

