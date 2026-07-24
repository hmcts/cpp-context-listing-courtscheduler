package uk.gov.moj.cpp.courtscheduler.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class SessionAvailabilityTest {

    @Test
    void hasSufficientAvailabilityShouldReturnTrueWhenOverbookingAllowed() {
        final CourtSchedule session = new CourtSchedule();
        session.setIsOverbookingAllowed(true);
        session.setMaxDuration(0);
        session.setTotalBooked(0);

        assertTrue(SessionAvailability.hasSufficientAvailability(session, 360));
    }

    @Test
    void hasSufficientAvailabilityShouldReturnTrueWhenEffectiveDurationMeetsRequirement() {
        final CourtSchedule session = new CourtSchedule();
        session.setIsOverbookingAllowed(false);
        session.setMaxDuration(360);
        session.setTotalBooked(0);

        assertTrue(SessionAvailability.hasSufficientAvailability(session, 360));
    }

    @Test
    void hasSufficientAvailabilityShouldReturnFalseWhenEffectiveDurationBelowRequirement() {
        final CourtSchedule session = new CourtSchedule();
        session.setIsOverbookingAllowed(false);
        session.setMaxDuration(300);
        session.setTotalBooked(0);

        assertFalse(SessionAvailability.hasSufficientAvailability(session, 360));
    }

    @Test
    void getEffectiveAvailableDurationShouldHandleAllDaySplit() {
        final CourtSchedule split = new CourtSchedule();
        split.setAllDaySplit(true);
        split.setMaxDurationForMorning(180);
        split.setMaxDurationForAfternoon(180);
        split.setTotalBookedForMorning(60);
        split.setTotalBookedForAfternoon(30);

        assertEquals(270, SessionAvailability.getEffectiveAvailableDuration(split));
    }

    @Test
    void getNextBusinessDayShouldSkipSaturdayAndSunday() {
        assertEquals(LocalDate.of(2026, 3, 9), SessionAvailability.getNextBusinessDay(LocalDate.of(2026, 3, 6)));
        assertEquals(LocalDate.of(2026, 3, 9), SessionAvailability.getNextBusinessDay(LocalDate.of(2026, 3, 7)));
        assertEquals(LocalDate.of(2026, 3, 9), SessionAvailability.getNextBusinessDay(LocalDate.of(2026, 3, 8)));
        assertEquals(LocalDate.of(2026, 3, 3), SessionAvailability.getNextBusinessDay(LocalDate.of(2026, 3, 2)));
    }
}
