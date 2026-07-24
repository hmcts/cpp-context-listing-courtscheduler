package uk.gov.moj.cpp.courtscheduler.common.utils;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class SessionAvailability {

    public static final int FULL_DAY_DURATION_MINS = 360;

    private SessionAvailability() {
    }

    public static int getEffectiveAvailableDuration(final CourtSchedule cs) {
        if (cs.isAllDaySplit()) {
            return (cs.getMaxDurationForMorning() + cs.getMaxDurationForAfternoon())
                    - (cs.getTotalBookedForMorning() + cs.getTotalBookedForAfternoon());
        }
        return cs.getMaxDuration() - cs.getTotalBooked();
    }

    public static boolean hasSufficientAvailability(final CourtSchedule session, final int requiredMinutes) {
        if (session.isOverbookingAllowed()) {
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
