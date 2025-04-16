package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.SessionTimeEnum.fromName;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.MeridianHelper.getMeridian;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.LONDON_ZONE;

import uk.gov.moj.cpp.courtscheduler.domain.SessionTimeEnum;

import java.sql.Date;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;


/**
 * Utility class for handling date and time operations.
 * This class is designed to store all dates in UTC format.
 * Timezone conversions should be handled by the UI.
 */
public class DateUtils {
    protected static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    public static final String DEFAULT_MORNING_START_TIME = "10:00";
    public static final String DEFAULT_MORNING_END_TIME = "13:00";
    public static final String DEFAULT_AFTERNOON_START_TIME = "14:00";
    public static final String DEFAULT_AFTERNOON_END_TIME = "17:00";
    public static final String DEFAULT_ALL_DAY_START_TIME = "10:00";
    public static final String DEFAULT_ALL_DAY_END_TIME = "17:00";

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

    public static final String toIsoString(final LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.format(ISO_8601_FORMATTER);
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

    public static final String toIsoString(final java.util.Date date) {
        if (date == null) {
            return null;
        }

        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'").format(date);
    }

    public static final String toResponseDateString(final java.util.Date date) {
        if (date == null) {
            return null;
        }
        // Convert to response json format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
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

    public static final java.util.Date getDate(String dateString) {
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return isoFormat.parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException(String.format("Passed date string:%s cannot be parsed with format:yyyy-MM-dd'T'HH:mm:ss'Z'", dateString));
        }
    }

    public static final java.util.Date localDateToDateWithTime(final LocalDate localDate, final int hour, final int minute) {
        final ZonedDateTime zonedDateTime = localDate.atTime(hour, minute).atZone(LONDON_ZONE);
        return java.util.Date.from(zonedDateTime.toInstant());
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

    public static java.util.Date combineDateAndTime(final LocalDate date, final String time) {
        LocalTime localTime = LocalTime.parse(time, TIME_FORMATTER);
        LocalDateTime localDateTime = LocalDateTime.of(date, localTime);
        ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.of("Europe/London")).withZoneSameInstant(ZoneOffset.UTC);
        return java.util.Date.from(zonedDateTime.toInstant());
    }

    public static LocalTime toLocalTime(final String time) {
        return LocalTime.parse(time, TIME_FORMATTER);
    }

    public static sessionStartAndEndTime getOrElseDefaultSessionStartAndEndTimeIfEmpty(final String sessionType, String sessionStartTime, String sessionEndTime) {
        if (isEmpty(sessionStartTime)) {
            switch (sessionType) {
                case AM_SESSION:
                    sessionStartTime = DEFAULT_MORNING_START_TIME;
                    break;
                case PM_SESSION:
                    sessionStartTime = DEFAULT_AFTERNOON_START_TIME;
                    break;
                case ALL_DAY:
                    sessionStartTime = DEFAULT_ALL_DAY_START_TIME;
                    break;
                default:
                    break;
            }
        }
        if (isEmpty(sessionEndTime)) {
            switch (sessionType) {
                case AM_SESSION:
                    sessionEndTime = DEFAULT_MORNING_END_TIME;
                    break;
                case PM_SESSION:
                    sessionEndTime = DEFAULT_AFTERNOON_END_TIME;
                    break;
                case ALL_DAY:
                    sessionEndTime = DEFAULT_ALL_DAY_END_TIME;
                    break;
                default:
                    break;
            }
        }
        return new sessionStartAndEndTime(sessionStartTime, sessionEndTime);
    }

    public record sessionStartAndEndTime(String sessionStartTime, String sessionEndTime) {
    }
}
