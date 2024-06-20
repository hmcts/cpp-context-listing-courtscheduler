package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static java.lang.String.valueOf;
import static java.util.EnumSet.range;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.Meridian.FIVE_PM;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.Meridian.ONE_PM;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.Meridian.TWELVE_AM;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.Meridian.TWELVE_PM;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;

//To be deleted in GPE-15175
public class MeridianHelper {
    private static final EnumSet<Meridian> amMeridian = range(TWELVE_AM, TWELVE_PM);
    private static final EnumSet<Meridian> pmMeridian = range(ONE_PM, FIVE_PM);

    private MeridianHelper() {
    }

    public static String getMeridian(final ZonedDateTime hearingDaySittingDay) {

        final String pattern = "HH";

        final String hour = hearingDaySittingDay.format(DateTimeFormatter.ofPattern(pattern));

        final boolean isAmMeridian = amMeridian.stream().anyMatch(meridian -> checkMeridian(meridian.getValue(), hour));
        final boolean isPmMeridian = pmMeridian.stream().anyMatch(meridian -> checkMeridian(meridian.getValue(), hour));

        if (isAmMeridian) {
            return "AM";
        }

        if (isPmMeridian) {
            return "PM";
        }

        return "AD";
    }

    private static boolean checkMeridian(final String value, final String hour) {
        return valueOf(value).equals(hour);
    }

    public static String getMeridian(final String isoDateTime) {
        return getMeridian(DateUtils.toZonedDateTime(isoDateTime));
    }
}
