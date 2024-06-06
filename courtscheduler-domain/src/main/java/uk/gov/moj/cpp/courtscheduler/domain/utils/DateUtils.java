package uk.gov.moj.cpp.courtscheduler.domain.utils;

import uk.gov.moj.cpp.courtscheduler.domain.SessionTimeEnum;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.domain.SessionTimeEnum.fromName;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.MeridianHelper.getMeridian;


public class DateUtils {
    protected static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private DateUtils() {
    }

    public static final Timestamp toRoundedTimestamp(final String isoDate) {
        final OffsetDateTime offsetDateTime = toOffsetDateTime(isoDate);
        if (offsetDateTime == null) {
            return null;
        }

        return Timestamp.valueOf(offsetDateTime
                .withMinute(0)
                .withSecond(0)
                .withNano(0).toLocalDateTime());
    }

    public static final OffsetDateTime toOffsetDateTime(final String isoDate) {
        if (isBlank(isoDate)) {
            return null;
        }
        return LocalDateTime.parse(isoDate, ISO_8601_FORMATTER).atOffset(ZoneOffset.UTC);
    }

    public static final ZonedDateTime toZonedDateTime(final String isoDate) {
        if (isBlank(isoDate)) {
            return null;
        }

        final OffsetDateTime tmp = toOffsetDateTime(isoDate);
        if (tmp == null) {
            return null;
        }
        return tmp.toZonedDateTime();
    }

    public static final String toIsoString(final OffsetDateTime dateTimeOffset) {
        if (dateTimeOffset == null) {
            return null;
        }
        return dateTimeOffset.format(ISO_8601_FORMATTER);
    }

    public static final String toIsoString(final Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().atOffset(ZoneOffset.UTC).format(ISO_8601_FORMATTER);
    }

    public static final Date toSqlDate(String dateString) {
        if (dateString == null) {
            return null;
        }

        final int length = dateString.length();
        if (length > 10) {
            dateString = dateString.substring(0, 10);
        }

        return Date.valueOf(dateString);
    }

    public static final Date toSqlDate(final LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return Date.valueOf(localDate);
    }


    public static final java.util.Date getDate(LocalDate localDate) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(localDate.toString());
        } catch (ParseException e) {
            throw new IllegalArgumentException(String.format("Passed localDate:%s cannot be parsed with format:yyyy-MM-dd", localDate));
        }
    }

    public static String createDefaultHearingStartTime(final String session, final String sessionDate) {
        if (isBlank(sessionDate) || isBlank(session)) {
            return null;
        }

        final SessionTimeEnum sessionEnum = fromName(session);
        final int time = sessionEnum.getDefaultStartTime();
        final String[] dateParts = sessionDate.split("T")[0].split("-");

        final int year = Integer.parseInt(dateParts[0]);
        final int month = Integer.parseInt(dateParts[1]);
        final int day = Integer.parseInt(dateParts[2]);

        final ZonedDateTime localDate = ZonedDateTime.of(year, month, day, time, 0, 0, 0, ZoneId.of("Europe/London")).withZoneSameInstant(ZoneOffset.UTC);
        return localDate.format(ISO_8601_FORMATTER);
    }

    public static String toMeridian(final String isoDateTime) {
        return getMeridian(toZonedDateTime(isoDateTime));
    }
}
