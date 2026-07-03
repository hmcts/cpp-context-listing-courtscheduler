package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for parsing dates from rota file data.
 */
@Service
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
        if (sessionDateStr == null || sessionDateStr.isEmpty()) {
            return null;
        }

        try {
            final LocalDate parsedDate = LocalDate.parse(sessionDateStr, DATE_FORMATTER);
            // Validate that the parsed date matches the input string exactly
            // This ensures that invalid dates like "2023-02-29" are rejected
            // (LocalDate.parse would adjust it to 2023-02-28, so we check the formatted output)
            final String formattedDate = parsedDate.format(DATE_FORMATTER);
            if (!formattedDate.equals(sessionDateStr)) {
                logger.warn("Date string does not match parsed date: {} != {}", sessionDateStr, formattedDate);
                return null;
            }
            logger.debug("Successfully parsed session date: {} to {}", sessionDateStr, parsedDate);
            return parsedDate;
        } catch (final Exception ex) {
            logger.warn("Failed to parse session date: {}", sessionDateStr, ex);
            return null;
        }
    }
}

