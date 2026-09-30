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

import java.security.SecureRandom;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;


/**
 * Utility class for handling date and time operations.
 * This class is designed to store all dates in UTC format.
 * Timezone conversions should be handled by the UI.
 */
public class DateUtils {
    public static final String ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    protected static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ofPattern(ISO_8601_PATTERN).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    public static final String DEFAULT_MORNING_START_TIME = "10:00";
    public static final String DEFAULT_MORNING_END_TIME = "13:00";
    public static final String DEFAULT_AFTERNOON_START_TIME = "14:00";
    public static final String DEFAULT_AFTERNOON_END_TIME = "17:00";
    public static final String DEFAULT_ALL_DAY_START_TIME = "10:00";
    public static final String DEFAULT_ALL_DAY_END_TIME = "17:00";
    public static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern(ISO_8601_PATTERN);
    private static final DateTimeFormatter LEGACY_MINUTE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'", Locale.UK).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter LEGACY_EXTENDED_FORMATTER =
            DateTimeFormatter.ofPattern(ISO_8601_PATTERN, Locale.UK).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter HOUR_MINUTE_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm", Locale.UK).withZone(ZoneId.systemDefault());
    private static final int ISO_DATE_LENGTH = 10;


    private DateUtils() {
    }

    public static Timestamp toRoundedTimestamp(final String isoDate) {
        final OffsetDateTime offsetDateTime = toOffsetDateTime(isoDate);
        if (offsetDateTime == null) {
            return null;
        }

        return Timestamp.valueOf(offsetDateTime
                .withMinute(0)
                .withSecond(0)
                .withNano(0).toLocalDateTime());
    }

    public static Timestamp toExactTimestamp(final String isoDate) {
        final OffsetDateTime offsetDateTime = toOffsetDateTime(isoDate);
        if (offsetDateTime == null) {
            return null;
        }

        return Timestamp.valueOf(offsetDateTime.toLocalDateTime());
    }

    public static OffsetDateTime toOffsetDateTime(final String isoDate) {
        if (isBlank(isoDate)) {
            return null;
        }
        try {
            return LocalDateTime.parse(isoDate, ISO_8601_FORMATTER).atOffset(ZoneOffset.UTC);
        } catch (final java.time.format.DateTimeParseException e) {
            // Callers legitimately send other ISO-8601 zoned forms — e.g. listing's crown fallback
            // sends ZonedDateTime.toString() output such as "2026-08-06T09:00Z[UTC]" (zone-id
            // suffix, seconds omitted when zero) — which the strict millisecond-Z pattern rejects.
            // Normalise any parseable zoned/offset form to UTC; genuinely unparseable input still
            // throws DateTimeParseException as before.
            return ZonedDateTime.parse(isoDate).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime();
        }
    }

    public static String toIsoString(final LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.format(ISO_8601_FORMATTER);
    }

    public static ZonedDateTime toZonedDateTime(final String isoDate) {
        if (isBlank(isoDate)) {
            return null;
        }

        final OffsetDateTime tmp = toOffsetDateTime(isoDate);
        if (tmp == null) {
            return null;
        }
        return tmp.toZonedDateTime();
    }

    public static String toIsoString(final OffsetDateTime dateTimeOffset) {
        if (dateTimeOffset == null) {
            return null;
        }
        return dateTimeOffset.format(ISO_8601_FORMATTER);
    }

    /**
     * Formats an instant the way the legacy {@code java.sql.Timestamp}-based {@code toIsoString}
     * did: {@code timestamp.toLocalDateTime()} reads the wall-clock fields in the JVM's default
     * timezone, which are then labelled as UTC. Preserved here exactly (bug-for-bug) via the JVM
     * default zone so behaviour for existing callers is unchanged.
     */
    public static String toIsoString(final Instant instant) {
        if (instant == null) {
            return null;
        }
        return LEGACY_EXTENDED_FORMATTER.format(instant);
    }

    /**
     * Minute-precision variant used by the MI reporting DTOs, replicating the legacy
     * {@code SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")} (no explicit timezone, i.e. JVM default)
     * behaviour exactly.
     */
    public static String toIsoStringMinutes(final Instant instant) {
        if (instant == null) {
            return null;
        }
        return LEGACY_MINUTE_FORMATTER.format(instant);
    }

    public static String toIsoStringExtended(final Instant instant) {
        if (instant == null) {
            return null;
        }
        return LEGACY_EXTENDED_FORMATTER.format(instant);
    }

    public static String toResponseDateString(final Instant instant) {
        if (instant == null) {
            return null;
        }
        // Convert to response json format
        return ISO_8601_FORMATTER.format(instant);
    }

    public static String toResponseDateStringISO(final Instant instant) {
        if (instant == null) {
            return null;
        }
        // Convert to response json format
        return ISO_8601_FORMATTER.format(instant);
    }

    public static String toResponseDateStringWithoutMillis(final String isoDate) {
        if (isoDate == null) {
            return null;
        }
        final OffsetDateTime dateTime = OffsetDateTime.parse(isoDate);
        final DateTimeFormatter formatterWithoutMillis = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

        return dateTime.format(formatterWithoutMillis);
    }

    public static String toLocalDateTimeString(final LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }

        return DTF.format(localDateTime);//"yyyy-MM-dd'T'HH:mm:ss'Z'"
    }

    public static Date toSqlDate(final String dateString) {
        if (dateString == null) {
            return null;
        }

        final int length = dateString.length();
        final String normalisedDateString = length > ISO_DATE_LENGTH ? dateString.substring(0, ISO_DATE_LENGTH) : dateString;

        return Date.valueOf(normalisedDateString);
    }

    public static Date toSqlDate(final LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return Date.valueOf(localDate);
    }


    public static java.util.Date getDate(final LocalDate localDate) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.UK).parse(localDate.toString());
        } catch (ParseException e) {
            throw new IllegalArgumentException(String.format("Passed localDate:%s cannot be parsed with format:yyyy-MM-dd", localDate));
        }
    }

    public static java.util.Date getDate(final String dateString) {
        try {
            final SimpleDateFormat isoFormat = new SimpleDateFormat(ISO_8601_PATTERN, Locale.UK);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return isoFormat.parse(dateString);
        } catch (ParseException e) {
            throw new IllegalArgumentException(String.format("Passed date string:%s cannot be parsed with format:yyyy-MM-dd'T'HH:mm:ss'Z'", dateString));
        }
    }

    public static java.util.Date localDateToDateWithTime(final LocalDate localDate, final int hour, final int minute) {
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

        final ZonedDateTime localDate = ZonedDateTime.of(year, month, day, time, 0, 0, 0, LONDON_ZONE).withZoneSameInstant(ZoneOffset.UTC);
        return localDate.format(ISO_8601_FORMATTER);
    }

    public static String toMeridian(final String isoDateTime) {
        return getMeridian(Objects.requireNonNull(toZonedDateTime(isoDateTime)));
    }

    /**
     * Builds the refdata listing-session key (e.g. "MONAM", "FRIPM") from a date and session type.
     * Returns null if either argument is missing.
     */
    public static String toListingSession(final LocalDate sessionDate, final String session) {
        if (sessionDate == null || isBlank(session)) {
            return null;
        }
        return sessionDate.getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, Locale.UK)
                .toUpperCase(Locale.UK) + session;
    }

    /**
     * Resolves a session start/end time using the precedence: 1. customTime (e.g. value supplied on
     * the API request) - takes precedence if non-blank 2. refDataTime (e.g. value from reference data,
     * e.g. an organisation-unit's default start time) - used if customTime is blank 3. defaultTime -
     * fallback (typically a hardcoded DEFAULT_*_TIME constant)
     *
     * @param customTime  caller-supplied override (e.g. API request body), may be blank/null
     * @param refDataTime time configured in reference data, may be blank/null
     * @param defaultTime hardcoded default (must not be blank)
     * @return the resolved time in HH:mm format
     */
    public static String resolveSessionTime(final String customTime, final String refDataTime, final String defaultTime) {
        if (!isBlank(customTime)) {
            return customTime;
        }
        if (!isBlank(refDataTime)) {
            return refDataTime;
        }
        return defaultTime;
    }

    /**
     * Normalises a reference-data time value to strict {@code HH:mm} form. Upstream referencedata
     * has been observed returning both {@code HH:mm} and {@code HH:mm:ss} for the same field (e.g.
     * organisation-unit {@code defaultStartTime}) - {@link LocalTime#parse(CharSequence)} (ISO-8601,
     * seconds optional) accepts either. Returns null for a blank or genuinely unparseable value, so
     * callers can fall back to a hardcoded default instead of failing the request.
     */
    public static String normaliseToHourMinute(final String time) {
        if (isBlank(time)) {
            return null;
        }
        try {
            return LocalTime.parse(time.trim()).format(TIME_FORMATTER);
        } catch (final java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    public static java.util.Date combineDateAndTime(final LocalDate date, final String time) {

        if (isBlank(time)) {
            throw new IllegalArgumentException("Time cannot be blank");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        final LocalTime localTime = LocalTime.parse(time, TIME_FORMATTER);

        // Convert LocalDate and LocalTime to ZonedDateTime in London timezone, then convert to UTC
        final ZonedDateTime zonedDateTime = LocalDateTime.of(date, localTime).atZone(LONDON_ZONE).withZoneSameInstant(ZoneOffset.UTC);

        // Re-interpret the UTC wall-clock fields in the JVM default timezone: this replicates the
        // previous Calendar.Builder-based construction exactly, since Calendar.Builder#setDate/
        // #setTimeOfDay build the Calendar in the default timezone rather than UTC.
        return java.util.Date.from(zonedDateTime.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant());
    }

    public static LocalTime toLocalTime(final String time) {
        return LocalTime.parse(time, TIME_FORMATTER);
    }

    public static String sessionTimeFormatter(final Instant instant) {
        return HOUR_MINUTE_FORMATTER.format(instant);
    }

    public static SessionStartAndEndTime getOrElseDefaultSessionStartAndEndTimeIfEmpty(final String sessionType, final String sessionStartTime, final String sessionEndTime) {
        String resolvedStartTime = sessionStartTime;
        if (isEmpty(resolvedStartTime)) {
            switch (sessionType) {
                case AM_SESSION:
                    resolvedStartTime = DEFAULT_MORNING_START_TIME;
                    break;
                case PM_SESSION:
                    resolvedStartTime = DEFAULT_AFTERNOON_START_TIME;
                    break;
                case ALL_DAY:
                    resolvedStartTime = DEFAULT_ALL_DAY_START_TIME;
                    break;
                default:
                    break;
            }
        }
        String resolvedEndTime = sessionEndTime;
        if (isEmpty(resolvedEndTime)) {
            switch (sessionType) {
                case AM_SESSION:
                    resolvedEndTime = DEFAULT_MORNING_END_TIME;
                    break;
                case PM_SESSION:
                    resolvedEndTime = DEFAULT_AFTERNOON_END_TIME;
                    break;
                case ALL_DAY:
                    resolvedEndTime = DEFAULT_ALL_DAY_END_TIME;
                    break;
                default:
                    break;
            }
        }
        return new SessionStartAndEndTime(resolvedStartTime, resolvedEndTime);
    }

    public record SessionStartAndEndTime(String sessionStartTime, String sessionEndTime) {
    }

    /**
     * Returns a random LocalDate between today and one year from today (inclusive).
     *
     * @return LocalDate in the future within next 365 days
     */
    public static LocalDate getRandomFutureDateWithinNextYear() {
        final LocalDate today = LocalDate.now();
        final LocalDate nextYear = today.plusYears(1);

        final long startEpochDay = today.toEpochDay();
        final long endEpochDay = nextYear.toEpochDay();

        final SecureRandom secureRandom = new SecureRandom();
        LocalDate randomDate;

        do {
            final long randomDay = startEpochDay + secureRandom.nextLong(endEpochDay - startEpochDay + 1);
            randomDate = LocalDate.ofEpochDay(randomDay);
        } while (randomDate.getDayOfWeek() == DayOfWeek.SATURDAY
                || randomDate.getDayOfWeek() == DayOfWeek.SUNDAY);

        return randomDate;
    }
}
