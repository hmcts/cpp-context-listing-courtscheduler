package uk.gov.moj.cpp.courtscheduler.common.utils;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class SessionAvailability {

    public static final int FULL_DAY_DURATION_MINS = 360;

    private SessionAvailability() {
    }

    public static int getEffectiveAvailableDuration(final CourtSchedule cs) {
        if (Boolean.TRUE.equals(cs.getAllDaySplit())) {
            return (orZero(cs.getMaxDurationForMorning()) + orZero(cs.getMaxDurationForAfternoon()))
                    - (orZero(cs.getTotalBookedForMorning()) + orZero(cs.getTotalBookedForAfternoon()));
        }
        return orZero(cs.getMaxDuration()) - orZero(cs.getTotalBooked());
    }

    // Old hand-written CourtSchedule (pre-migration) defaulted these Integer fields to 0 via its
    // builder even when a caller never set them explicitly; the generated model leaves them null
    // instead. Treating null as 0 here preserves that implicit default rather than NPE-ing.
    private static int orZero(final Integer value) {
        return value == null ? 0 : value;
    }

    public static boolean hasSufficientAvailability(final CourtSchedule session, final int requiredMinutes) {
        if (Boolean.TRUE.equals(session.getOverbookingAllowed())) {
            return true;
        }
        return getEffectiveAvailableDuration(session) >= requiredMinutes;
    }

    public static LocalDate getNextBusinessDay(final LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }
}
