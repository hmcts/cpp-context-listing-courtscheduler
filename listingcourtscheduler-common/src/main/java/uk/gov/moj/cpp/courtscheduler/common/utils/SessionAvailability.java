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
            return cs.getMaxDurationForMorning() + cs.getMaxDurationForAfternoon()
                    - (cs.getTotalBookedForMorning() + cs.getTotalBookedForAfternoon());
        }
        return cs.getMaxDuration() - cs.getTotalBooked();
    }

    public static boolean hasSufficientAvailability(final CourtSchedule session, final int requiredMinutes) {
        if (Boolean.TRUE.equals(session.getOverbookingAllowed())) {
            return true;
        }
        return getEffectiveAvailableDuration(session) >= requiredMinutes;
    }

    /**
     * Which of two sessions on the same date to book a day of a CROWN multiday block into, under the
     * court-calendar always-assign rule (capacity never removes a date): a session with room for
     * {@code requiredMinutes} beats one without; when both fit, the one that does NOT allow
     * overbooking wins (slot-search's preferNonOverbooking); when neither fits, the one that allows
     * overbooking wins. Ties keep {@code existing}. Shared by the fresh / move search and, since
     * SPRDT-1572, the extend tail.
     */
    public static CourtSchedule preferBookable(final CourtSchedule existing, final CourtSchedule incoming,
                                               final int requiredMinutes) {
        final boolean existingFits = getEffectiveAvailableDuration(existing) >= requiredMinutes;
        final boolean incomingFits = getEffectiveAvailableDuration(incoming) >= requiredMinutes;
        if (existingFits != incomingFits) {
            return existingFits ? existing : incoming;
        }
        final boolean existingOverbooks = Boolean.TRUE.equals(existing.getOverbookingAllowed());
        final boolean incomingOverbooks = Boolean.TRUE.equals(incoming.getOverbookingAllowed());
        if (existingFits) {
            return existingOverbooks && !incomingOverbooks ? incoming : existing;
        }
        return !existingOverbooks && incomingOverbooks ? incoming : existing;
    }

    public static LocalDate getNextBusinessDay(final LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }
}
