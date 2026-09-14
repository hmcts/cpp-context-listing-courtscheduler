package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.moj.cpp.courtscheduler.common.utils.SessionAvailability;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects a valid consecutive run of {@link CourtSchedule} sessions out of a set of raw
 * candidates (dedupe by date preferring a bookable row, length check, consecutive-business-day
 * check). Extracted out of {@link SlotsUpdateService} (SPRDT reserve-a-slot Crown multi-day hold)
 * so both the share-time booking path ({@code SlotsUpdateService}) and the pick-time reservation
 * path ({@link ReservationService}) resolve the SAME run for the SAME anchor — a divergence
 * between the two would mean the reservation holds days the share never uses, and the days the
 * share actually uses were never held, which defeats the point of reserving ahead of share.
 *
 * <p>Purely a selector: it does not book or persist anything. Callers persist separately so a
 * release-then-book (or reserve) sequence can be ordered safely.
 */
public final class ConsecutiveSessionSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsecutiveSessionSelector.class);
    private static final int MINUTES_IN_DAY = 360;

    private ConsecutiveSessionSelector() {
    }

    /**
     * Select a valid consecutive run (dedupe, length, consecutive business days). Does NOT
     * persist — callers persist (or reserve) separately so a release-then-book sequence can be
     * ordered safely.
     *
     * <p>Court-calendar rule (F1): a lack of session capacity must NEVER block the assignment —
     * hearings are placed even when a day is full and {@code is_overbooking_allowed=false}. The
     * per-date dedupe therefore prefers a row that can actually take {@code perDayMinutes}
     * (falling back to an overbooking-allowed row, then to any row), and the old hard
     * availability rejection is replaced by an advisory log of the days being overbooked.
     * Only STRUCTURAL failures (not enough session days, non-consecutive dates) reject the run.</p>
     */
    public static List<CourtSchedule> selectConsecutiveSessions(
            final List<CourtSchedule> rawCandidates, final int daysNeeded, final String hearingId,
            final int perDayMinutes) {
        final List<CourtSchedule> candidates = dedupeByDatePreferringBookable(
                rawCandidates == null ? Collections.emptyList() : rawCandidates, perDayMinutes);
        if (candidates.size() < daysNeeded) {
            return Collections.emptyList();
        }
        final List<CourtSchedule> sessions = candidates.subList(0, daysNeeded);
        if (!areConsecutiveBusinessDays(sessions, hearingId)) {
            return Collections.emptyList();
        }
        logSessionsBookedBeyondCapacity(sessions, hearingId, perDayMinutes);
        return new ArrayList<>(sessions);
    }

    /**
     * Advisory replacement for the old all-or-nothing availability rejection: names each day being
     * booked beyond its remaining capacity (including overbooking-disallowed sessions) so support can
     * trace intentional overbooking, but never blocks the assignment.
     */
    private static void logSessionsBookedBeyondCapacity(
            final List<CourtSchedule> sessions, final String hearingId, final int perDayMinutes) {
        for (final CourtSchedule session : sessions) {
            final int available = getEffectiveAvailableDuration(session);
            if (available < perDayMinutes) {
                LOGGER.info("[CROWN-SAB] Overbooking session {} on {} for hearingId {} — {}mins available, {}mins needed, overbookingAllowed={} (court-calendar always-assign rule)",
                        session.getCourtScheduleId(), session.getSessionDate(), hearingId,
                        available, perDayMinutes, session.isOverbookingAllowed());
            }
        }
    }

    static boolean areConsecutiveBusinessDays(final List<CourtSchedule> sessions, final String hearingId) {
        for (int i = 1; i < sessions.size(); i++) {
            final LocalDate previousDate = sessions.get(i - 1).getSessionDate();
            final LocalDate currentDate = sessions.get(i).getSessionDate();
            final LocalDate expectedNextBusinessDay = SessionAvailability.getNextBusinessDay(previousDate);
            if (!currentDate.equals(expectedNextBusinessDay)) {
                LOGGER.info("[MULTIDAY-SEARCH] hearingId: {}, gap detected between {} ({}) and {} ({}), expected next business day: {}",
                        hearingId,
                        sessions.get(i - 1).getCourtScheduleId(), previousDate,
                        sessions.get(i).getCourtScheduleId(), currentDate,
                        expectedNextBusinessDay);
                return false;
            }
        }
        return true;
    }

    static List<CourtSchedule> dedupeByDatePreferringBookable(
            final List<CourtSchedule> sessions, final int requiredPerDayMinutes) {
        final java.util.LinkedHashMap<LocalDate, CourtSchedule> byDate = new java.util.LinkedHashMap<>();
        for (final CourtSchedule cs : sessions) {
            if (cs.getSessionDate() == null) {
                continue;
            }
            byDate.merge(cs.getSessionDate(), cs, (existing, incoming) ->
                    preferBookable(existing, incoming, requiredPerDayMinutes));
        }
        final List<CourtSchedule> out = new ArrayList<>(byDate.values());
        out.sort(java.util.Comparator.comparing(CourtSchedule::getSessionDate));
        return out;
    }

    private static CourtSchedule preferBookable(
            final CourtSchedule existing, final CourtSchedule incoming, final int requiredPerDayMinutes) {
        final boolean existingFits = getEffectiveAvailableDuration(existing) >= requiredPerDayMinutes;
        final boolean incomingFits = getEffectiveAvailableDuration(incoming) >= requiredPerDayMinutes;
        if (existingFits != incomingFits) {
            return existingFits ? existing : incoming;
        }
        if (existingFits) {
            // both fit: prefer NOT-overbookingAllowed, matching slot-search's preferNonOverbooking
            return existing.isOverbookingAllowed() && !incoming.isOverbookingAllowed() ? incoming : existing;
        }
        // neither fits: prefer the row where overbooking is explicitly allowed
        return !existing.isOverbookingAllowed() && incoming.isOverbookingAllowed() ? incoming : existing;
    }

    static int getEffectiveAvailableDuration(final CourtSchedule cs) {
        return SessionAvailability.getEffectiveAvailableDuration(cs);
    }

    /**
     * Per-day minutes for a booked/reserved session: the request total split across the booked days
     * ({@code total/days}), or one full court day when no total is supplied (the date-range form).
     */
    public static int perDayDuration(final int totalDurationInMinutes, final int daysNeeded) {
        if (totalDurationInMinutes <= 0 || daysNeeded <= 0) {
            return MINUTES_IN_DAY;
        }
        return Math.max(totalDurationInMinutes / daysNeeded, 1);
    }
}
