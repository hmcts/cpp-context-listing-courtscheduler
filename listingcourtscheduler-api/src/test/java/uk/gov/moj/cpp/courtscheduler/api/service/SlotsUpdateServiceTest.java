package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.ChangeCourtRoomForMultidayHearingRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ChangeCourtRoomForMultidayHearingResponse;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackRequest;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackResponse;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackSearchResult;
import uk.gov.moj.cpp.courtscheduler.domain.CrownSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.CrownSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Hearing;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MagsSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MagsSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateResponse;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingInfo;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedDay;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.ReserveUnconfirmedHearingRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ReserveUnconfirmedHearingResponse;
import uk.gov.moj.cpp.courtscheduler.exception.ConfirmedBookingExistsException;
import uk.gov.moj.cpp.courtscheduler.exception.CrownFallbackInvalidRequestException;
import uk.gov.moj.cpp.courtscheduler.exception.CrownFallbackNoSessionException;
import uk.gov.moj.cpp.courtscheduler.exception.NoAllocationOnDateException;
import uk.gov.moj.cpp.courtscheduler.exception.NoSessionAvailableException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotsUpdateServiceTest {

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private ProvisionalBookingRepository provisionalBookingRepository;

    @Mock
    private uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository allocatedListingRepository;

    @Mock
    private Logger logger;

    @Mock
    private uk.gov.moj.cpp.courtscheduler.common.service.ExtendMultidayHearingService extendMultidayHearingService;

    @InjectMocks
    private SlotsUpdateService service;

    @BeforeEach
    void setup() {
        setField(service, "courtScheduleRepository", courtScheduleRepository);
        setField(service, "provisionalBookingRepository", provisionalBookingRepository);
        setField(service, "allocatedListingRepository", allocatedListingRepository);
        setField(service, "extendMultidayHearingService", extendMultidayHearingService);
    }

    @Test
    void shouldUpdateAllocatedSlots() {

        final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots.json");
        final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();
        when(courtScheduleRepository.saveBookedSlots(any(), anyBoolean(), anyBoolean())).thenReturn(new Result("", true));
        service.update(allocatedSlots);

        verify(courtScheduleRepository).saveBookedSlots(allocatedSlots, false, false);
    }

    @Test
    void shouldUpdateAllocatedSlotsWithBookingId() throws JsonProcessingException {

        final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots-with-bookingid.json");
        final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();

        final String provisionalBookingPayload = fileToString("/test-data/courtscheduler.get.provisional.hearing.slots.json");
        final List<ProvisionalBookingInfo> provisionalBookingInfos = new ObjectMapper().readValue(provisionalBookingPayload, new TypeReference<>() {
        });

        final Map<String, Date> courtScheduleInfo = provisionalBookingInfos
                .stream()
                .collect(Collectors.toMap(ProvisionalBookingInfo::getCourtScheduleId, ProvisionalBookingInfo::getHearingStartTime));

        when(provisionalBookingRepository.getCourtScheduleInfo(any())).thenReturn(courtScheduleInfo);
        when(courtScheduleRepository.saveBookedSlots(any(), anyBoolean(), anyBoolean())).thenReturn(new Result("", true));
        service.update(allocatedSlots);

        verify(courtScheduleRepository, atLeastOnce()).saveBookedSlots(any(), eq(true), eq(false));
    }

    @Test
    void shouldThrowProvisionalSlotNotFoundExceptionWhenUpdateAllocatedSlotsWithBookingId() {

        Assertions.assertThrows(uk.gov.moj.cpp.courtscheduler.exception.ProvisionalSlotNotFoundException.class, () -> {
            final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots-with-bookingid.json");
            final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();

            when(provisionalBookingRepository.getCourtScheduleInfo(any())).thenReturn(Collections.emptyMap());

            service.update(allocatedSlots);
        });
    }

    @Test
    void shouldThrowCourtScheduleIdNotMatchingExceptionWhenUpdateAllocatedSlotsWithBookingId() {

        Assertions.assertThrows(uk.gov.moj.cpp.courtscheduler.exception.CourtScheduleIdNotMatchingException.class, () -> {
            final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots-with-bookingid-wrong-csId.json");
            final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();

            final String provisionalBookingPayload = fileToString("/test-data/courtscheduler.get.provisional.hearing.slots.json");
            final List<ProvisionalBookingInfo> provisionalBookingInfos = new ObjectMapper().readValue(provisionalBookingPayload, new TypeReference<List<ProvisionalBookingInfo>>() {
            });

            final Map<String, Date> courtScheduleInfo = provisionalBookingInfos
                    .stream()
                    .collect(Collectors.toMap(ProvisionalBookingInfo::getCourtScheduleId, ProvisionalBookingInfo::getHearingStartTime));


            when(provisionalBookingRepository.getCourtScheduleInfo(any())).thenReturn(courtScheduleInfo);

            service.update(allocatedSlots);
        });
    }

    @Test
    void shouldListHearingSlotsAndReturnResponse() {
        final RequestedSlots wrapper = new RequestedSlots();

        final Hearing hearing1 = new Hearing();
        final String hearingId1 = UUID.randomUUID().toString();
        hearing1.setHearingId(hearingId1);
        final Hearing hearing2 = new Hearing();
        final String hearingId2 = UUID.randomUUID().toString();
        hearing2.setHearingId(hearingId2);
        final List<Hearing> hearings = List.of(hearing1, hearing2);

        when(courtScheduleRepository.updateListHearingSlots(wrapper)).thenReturn(hearings);

        ListHearingSlotsResponse response = service.listHearingSlots(wrapper);

        assertNotNull(response);
        assertEquals(2, response.getHearings().size());
        assertEquals(hearingId1, response.getHearings().get(0).getHearingId());
        verify(courtScheduleRepository).updateListHearingSlots(wrapper);
        verifyNoMoreInteractions(courtScheduleRepository);
    }

    @Nested
    class AreConsecutiveBusinessDays {

        @Test
        void shouldReturnTrueForConsecutiveWeekdays() {
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = buildConsecutiveSessions(
                    LocalDate.of(2026, 3, 2), 3); // Mon, Tue, Wed
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueForFridayToMonday() {
            // Fri→Mon: consecutive business days (weekend skipped)
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 6)),  // Friday
                    buildSession(LocalDate.of(2026, 3, 9))); // Monday
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnFalseForFridayToTuesday() {
            // Fri→Tue: NOT consecutive business days (Monday is missing)
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 6)),   // Friday
                    buildSession(LocalDate.of(2026, 3, 10))); // Tuesday
            Assertions.assertFalse(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueForThursdayToFriday() {
            // Thu→Fri is consecutive
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 5)),  // Thursday
                    buildSession(LocalDate.of(2026, 3, 6))); // Friday
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnFalseForGapInWeekdays() {
            // Mon→Wed: Tuesday is missing
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 2)),  // Monday
                    buildSession(LocalDate.of(2026, 3, 4))); // Wednesday
            Assertions.assertFalse(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueForSingleSession() {
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 2)));
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueForThursdayFridayMonday() {
            // Thu→Fri→Mon: consecutive business days spanning a weekend
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 5)),  // Thursday
                    buildSession(LocalDate.of(2026, 3, 6)),  // Friday
                    buildSession(LocalDate.of(2026, 3, 9))); // Monday
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnFalseForThursdayFridayTuesday() {
            // Thu→Fri→Tue: Monday is missing after the weekend
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 5)),   // Thursday
                    buildSession(LocalDate.of(2026, 3, 6)),   // Friday
                    buildSession(LocalDate.of(2026, 3, 10))); // Tuesday
            Assertions.assertFalse(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueForFullWeekWithWeekendInMiddle() {
            // Wed→Thu→Fri→Mon→Tue: 5 consecutive business days spanning one weekend
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 4)),   // Wednesday
                    buildSession(LocalDate.of(2026, 3, 5)),   // Thursday
                    buildSession(LocalDate.of(2026, 3, 6)),   // Friday
                    buildSession(LocalDate.of(2026, 3, 9)),   // Monday
                    buildSession(LocalDate.of(2026, 3, 10))); // Tuesday
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnFalseForFiveDaysWithMissingMondayAfterWeekend() {
            // Wed→Thu→Fri→Tue→Wed: Monday missing after weekend breaks the chain
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 4)),   // Wednesday
                    buildSession(LocalDate.of(2026, 3, 5)),   // Thursday
                    buildSession(LocalDate.of(2026, 3, 6)),   // Friday
                    buildSession(LocalDate.of(2026, 3, 10)),  // Tuesday (Monday missing!)
                    buildSession(LocalDate.of(2026, 3, 11))); // Wednesday
            Assertions.assertFalse(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueForFridayThroughNextThursday() {
            // Fri→Mon→Tue→Wed→Thu: 5 business days starting Friday
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 6)),   // Friday
                    buildSession(LocalDate.of(2026, 3, 9)),   // Monday
                    buildSession(LocalDate.of(2026, 3, 10)),  // Tuesday
                    buildSession(LocalDate.of(2026, 3, 11)),  // Wednesday
                    buildSession(LocalDate.of(2026, 3, 12))); // Thursday
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueAcrossUkDstSpringForwardBoundary() {
            // BST starts on Sun 2026-03-29. Fri→Mon must remain "consecutive business days" across the clock change.
            // LocalDate arithmetic is zone-agnostic, so DST must not affect the predicate.
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 27)),  // Friday (GMT)
                    buildSession(LocalDate.of(2026, 3, 30))); // Monday (BST)
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueAcrossUkDstFallBackBoundary() {
            // GMT resumes on Sun 2026-10-25. Fri→Mon across the fall-back must remain consecutive.
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 10, 23)),  // Friday (BST)
                    buildSession(LocalDate.of(2026, 10, 26))); // Monday (GMT)
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueAcrossLeapDayWeekdayTransition() {
            // 2024 is a leap year. Feb 28 (Wed) → Feb 29 (Thu) → Mar 1 (Fri) are consecutive weekdays.
            // Locks in that plusDays(1) handles leap years correctly for the business-day predicate.
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2024, 2, 28)),  // Wednesday
                    buildSession(LocalDate.of(2024, 2, 29)),  // Thursday (leap day)
                    buildSession(LocalDate.of(2024, 3, 1)));  // Friday
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueAcrossYearBoundary() {
            // Fri 2024-12-27 → Mon 2024-12-30 → Tue 2024-12-31 → Wed 2025-01-01 → Thu 2025-01-02
            // Tests that the predicate works across a calendar-year flip.
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2024, 12, 27)),  // Friday
                    buildSession(LocalDate.of(2024, 12, 30)),  // Monday
                    buildSession(LocalDate.of(2024, 12, 31)),  // Tuesday
                    buildSession(LocalDate.of(2025, 1, 1)),    // Wednesday
                    buildSession(LocalDate.of(2025, 1, 2)));   // Thursday
            assertTrue(SlotsUpdateService.areConsecutiveBusinessDays(sessions, "test-hearing"));
        }
    }

    private static List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> buildConsecutiveSessions(
            final LocalDate startDate, final int count) {
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = new ArrayList<>();
        LocalDate date = startDate;
        for (int i = 0; i < count; i++) {
            sessions.add(buildSession(date));
            date = date.plusDays(1);
        }
        return sessions;
    }

    private static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule buildSession(final LocalDate date) {
        return buildSession(date, 360, 0, false);
    }

    /** Like {@link #buildSession(LocalDate)}, but with an explicit courtScheduleId rather than a random UUID. */
    private static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule buildSessionWithId(
            final LocalDate date, final String courtScheduleId) {
        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs = buildSession(date);
        cs.setCourtScheduleId(courtScheduleId);
        return cs;
    }

    /** Like {@link #buildSession(LocalDate, int, int, boolean)}, but with an explicit courtScheduleId. */
    private static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule buildSessionOn(
            final LocalDate date, final int maxDuration, final int totalBooked,
            final boolean overbookingAllowed, final String courtScheduleId) {
        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs =
                buildSession(date, maxDuration, totalBooked, overbookingAllowed);
        cs.setCourtScheduleId(courtScheduleId);
        return cs;
    }

    private static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule buildSession(
            final LocalDate date, final int maxDuration, final int totalBooked, final boolean overbookingAllowed) {
        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs = new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
        cs.setCourtScheduleId(UUID.randomUUID().toString());
        cs.setSessionDate(date);
        cs.setOuCode("OU123");
        cs.setCourtRoomId(UUID.randomUUID().toString());
        cs.setCourtRoomName("Court Room 1");
        cs.setBusinessType("TRFL");
        cs.setMaxDuration(maxDuration);
        cs.setTotalBooked(totalBooked);
        cs.setAvailableDuration(maxDuration - totalBooked);
        cs.setIsOverbookingAllowed(overbookingAllowed);
        return cs;
    }

    @Nested
    class GetEffectiveAvailableDuration {

        @Test
        void shouldCalculateForRegularSession() {
            uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs =
                    buildSession(LocalDate.of(2026, 3, 2), 360, 50, false);
            assertEquals(310, SlotsUpdateService.getEffectiveAvailableDuration(cs));
        }

        @Test
        void shouldCalculateForAllDaySplitSession() {
            uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs = new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
            cs.setAllDaySplit(true);
            cs.setMaxDurationForMorning(200);
            cs.setMaxDurationForAfternoon(200);
            cs.setTotalBookedForMorning(20);
            cs.setTotalBookedForAfternoon(30);
            assertEquals(350, SlotsUpdateService.getEffectiveAvailableDuration(cs));
        }
    }

    // ─── F1: bookable-preferring per-date dedupe (court-calendar always-assign rule) ──────────

    @Nested
    class DedupeByDatePreferringBookable {

        private static final int REQUIRED = 360;

        @Test
        void shouldPreferRowWithCapacityOverFullRowRegardlessOfOrder() {
            final LocalDate date = LocalDate.of(2026, 3, 2);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule full = buildSession(date, 360, 360, false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule free = buildSession(date, 360, 0, false);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> fullFirst =
                    SlotsUpdateService.dedupeByDatePreferringBookable(List.of(full, free), REQUIRED);
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> freeFirst =
                    SlotsUpdateService.dedupeByDatePreferringBookable(List.of(free, full), REQUIRED);

            assertEquals(1, fullFirst.size());
            assertEquals(free.getCourtScheduleId(), fullFirst.get(0).getCourtScheduleId());
            assertEquals(free.getCourtScheduleId(), freeFirst.get(0).getCourtScheduleId());
        }

        @Test
        void shouldPreferNonOverbookableWhenBothFit() {
            // Parity with slot-search's preferNonOverbooking: when both rows can take the booking,
            // the strictly-managed session wins so search advertising and booking agree.
            final LocalDate date = LocalDate.of(2026, 3, 2);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule overbookable = buildSession(date, 360, 0, true);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule strict = buildSession(date, 360, 0, false);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    SlotsUpdateService.dedupeByDatePreferringBookable(List.of(overbookable, strict), REQUIRED);

            assertEquals(strict.getCourtScheduleId(), result.get(0).getCourtScheduleId());
        }

        @Test
        void shouldPreferOverbookableWhenNeitherFits() {
            // The RC-3 defect inverted: when every row on the date is full, the explicitly
            // overbookable one must represent the date rather than the full strict sibling.
            final LocalDate date = LocalDate.of(2026, 3, 2);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule fullStrict = buildSession(date, 360, 360, false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule fullOverbookable = buildSession(date, 360, 360, true);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    SlotsUpdateService.dedupeByDatePreferringBookable(List.of(fullStrict, fullOverbookable), REQUIRED);

            assertEquals(fullOverbookable.getCourtScheduleId(), result.get(0).getCourtScheduleId());
        }

        @Test
        void shouldSortByDateAndSkipNullDates() {
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule later = buildSession(LocalDate.of(2026, 3, 3), 360, 0, false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule earlier = buildSession(LocalDate.of(2026, 3, 2), 360, 0, false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule dateless = buildSession(LocalDate.of(2026, 3, 4), 360, 0, false);
            dateless.setSessionDate(null);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    SlotsUpdateService.dedupeByDatePreferringBookable(List.of(later, dateless, earlier), REQUIRED);

            assertEquals(2, result.size());
            assertEquals(earlier.getCourtScheduleId(), result.get(0).getCourtScheduleId());
            assertEquals(later.getCourtScheduleId(), result.get(1).getCourtScheduleId());
        }
    }

    private static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule buildAllDaySplitSession(
            final LocalDate date, final int maxAm, final int maxPm, final int bookedAm, final int bookedPm) {
        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs = new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
        cs.setCourtScheduleId(UUID.randomUUID().toString());
        cs.setSessionDate(date);
        cs.setOuCode("OU123");
        cs.setCourtRoomId(UUID.randomUUID().toString());
        cs.setCourtRoomName("Court Room 1");
        cs.setBusinessType("TRFL");
        cs.setAllDaySplit(true);
        cs.setMaxDurationForMorning(maxAm);
        cs.setMaxDurationForAfternoon(maxPm);
        cs.setTotalBookedForMorning(bookedAm);
        cs.setTotalBookedForAfternoon(bookedPm);
        cs.setIsOverbookingAllowed(false);
        return cs;
    }

    @Nested
    class CrownFallbackSearchAndBook {

        @Test
        void shouldReturnExistingAllocationOnIdempotentReplay() {
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId);

            final AllocatedListing existing = new AllocatedListing();
            existing.setId(UUID.randomUUID().toString());
            existing.setHearingId(hearingId);
            existing.setCourtScheduleId(UUID.randomUUID().toString());
            existing.setCourtRoomId(731816);
            existing.setDuration(10);
            existing.setRotaBusinessType("CR");
            existing.setSource("CROWN_FB_LIST");
            existing.setHearingStartTime(java.sql.Timestamp.valueOf("2026-04-21 09:00:00"));

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.of(existing));
            // SPRDT-1274: the replay resolves the room UUID from the allocated session — the
            // allocated_listings row only carries the legacy Integer room number.
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule allocatedSession =
                    new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
            allocatedSession.setCourtScheduleId(existing.getCourtScheduleId());
            allocatedSession.setCourtRoomId("731816c1-5ee4-373a-9bda-840e13a5bcb0");
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(existing.getCourtScheduleId())))
                    .thenReturn(List.of(allocatedSession));

            final CrownFallbackResponse response = service.crownFallbackSearchAndBook(request);

            assertEquals(hearingId, response.hearingId());
            assertEquals(existing.getCourtScheduleId(), response.courtScheduleId());
            assertEquals("731816c1-5ee4-373a-9bda-840e13a5bcb0", response.courtRoomId());
            assertEquals(10, response.durationInMinutes());
            assertEquals("CROWN_FB_LIST", response.source());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .searchCrownFallbackSlots(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void shouldBookAndReturnSuccessWhenFallbackFindsNonDraftSession() {
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId);
            final String scheduleId = UUID.randomUUID().toString();

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.empty());

            final CourtSchedule session = buildSession(scheduleId, LocalDate.parse("2026-04-21"), false, "CR");
            when(courtScheduleRepository.searchCrownFallbackSlots(
                    eq(request.getCourtCentreId()), eq(request.getHearingDate()),
                    eq(10), eq(request.getCourtRoomId()), eq(request.getEarliestHearingTime())))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(session, false)));

            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownFallbackResponse response = service.crownFallbackSearchAndBook(request);

            assertEquals(scheduleId, response.courtScheduleId());
            assertEquals(false, response.isDraft());
            assertEquals(false, response.overbooked());
            assertEquals("CR", response.businessType());
            assertEquals("CROWN_FB_LIST", response.source());
        }

        @Test
        void shouldMarkResponseOverbookedWhenOverbookingFallbackUsed() {
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId);
            final String scheduleId = UUID.randomUUID().toString();

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.empty());

            final CourtSchedule session = buildSession(scheduleId, LocalDate.parse("2026-04-21"), false, "CR");
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(session, true)));

            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownFallbackResponse response = service.crownFallbackSearchAndBook(request);

            assertEquals(true, response.overbooked());
        }

        @Test
        void shouldAutoCreateSessionAndBookWhenSearchReturnsEmpty() {
            // SPRDT-1159: no bookable session on the requested date/room -> courtscheduler creates one
            // on the fly from the request parameters and the hearing books onto it in the same response.
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId);
            final String createdScheduleId = UUID.randomUUID().toString();

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.empty());
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.empty());

            final CourtSchedule created = buildSession(createdScheduleId, request.getHearingDate(), false, "CR");
            when(courtScheduleRepository.createCrownFallbackSession(eq(request)))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(created, false)));
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            when(courtScheduleRepository.saveBookedSlots(slotCaptor.capture(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownFallbackResponse response = service.crownFallbackSearchAndBook(request);

            assertEquals(createdScheduleId, response.courtScheduleId());
            assertEquals(false, response.isDraft());
            assertEquals(false, response.overbooked());
            // SPRDT-1283: the full request travels to the repository so a never-seeded centre can
            // build the session from the request's own metadata (ouCode/centre/room names).
            verify(courtScheduleRepository).createCrownFallbackSession(eq(request));
            // Allocation via on-the-fly session creation is stamped AUTO_CREATE_SAB on the DB row.
            assertEquals("AUTO_CREATE_SAB", slotCaptor.getValue().get(0).getSource());
            verify(allocatedListingRepository).updateSourceByHearingId(hearingId, "AUTO_CREATE_SAB");
        }

        @Test
        void shouldThrowNoSessionExceptionWhenSearchEmptyAndAutoCreateHasNoTemplateSession() {
            // SPRDT-1283: creation is refused only when the centre has no session to copy metadata
            // from AND the request carries no ouCode to build one from scratch.
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId);

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.empty());
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.empty());
            when(courtScheduleRepository.createCrownFallbackSession(any(CrownFallbackRequest.class)))
                    .thenReturn(Optional.empty());

            Assertions.assertThrows(CrownFallbackNoSessionException.class,
                    () -> service.crownFallbackSearchAndBook(request));
            verify(courtScheduleRepository, org.mockito.Mockito.never()).saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void shouldClampAllocatedListingStartTimeToSessionStartWhenRequestedTimeIsOutsideSessionWindow() {
            // SPRDT-1283 part 2: allocated_listings reflects the SESSION. A requested time outside
            // the booked session's window is clamped to the session start (the listing viewstore
            // keeps the user time — SPRDT-1274 — so the two stores deliberately diverge here).
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId)
                    .setEarliestHearingTime("2026-04-21T17:00:00Z");

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.empty());
            final CourtSchedule session = buildSession(UUID.randomUUID().toString(), LocalDate.parse("2026-04-21"), false, "CR");
            session.setSessionStartTime(java.sql.Timestamp.from(java.time.Instant.parse("2026-04-21T10:00:00Z")));
            session.setSessionEndTime(java.sql.Timestamp.from(java.time.Instant.parse("2026-04-21T16:00:00Z")));
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(session, false)));
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            when(courtScheduleRepository.saveBookedSlots(slotCaptor.capture(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            service.crownFallbackSearchAndBook(request);

            final String persistedStartTime = slotCaptor.getValue().get(0).getHearingStartTime();
            assertEquals(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toIsoString(
                            new java.sql.Timestamp(session.getSessionStartTime().getTime())),
                    persistedStartTime);
        }

        @Test
        void shouldKeepRequestedStartTimeOnAllocatedListingWhenInsideSessionWindow() {
            // Inside the session window the requested time is accurate w.r.t. the session and is
            // kept — the same rule getAdjustedHearingStartTime applies on the other booking paths.
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId)
                    .setEarliestHearingTime("2026-04-21T11:00:00Z");

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.empty());
            final CourtSchedule session = buildSession(UUID.randomUUID().toString(), LocalDate.parse("2026-04-21"), false, "CR");
            session.setSessionStartTime(java.sql.Timestamp.from(java.time.Instant.parse("2026-04-21T10:00:00Z")));
            session.setSessionEndTime(java.sql.Timestamp.from(java.time.Instant.parse("2026-04-21T16:00:00Z")));
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(session, false)));
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            when(courtScheduleRepository.saveBookedSlots(slotCaptor.capture(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            service.crownFallbackSearchAndBook(request);

            assertEquals("2026-04-21T11:00:00Z", slotCaptor.getValue().get(0).getHearingStartTime());
        }

        @Test
        void shouldThrowNoSessionExceptionWhenPersistFails() {
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId);

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.empty());
            final CourtSchedule session = buildSession(UUID.randomUUID().toString(), LocalDate.parse("2026-04-21"), false, "CR");
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(session, false)));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(Result.FAILED("db failure"));

            Assertions.assertThrows(CrownFallbackNoSessionException.class,
                    () -> service.crownFallbackSearchAndBook(request));
        }

        @Test
        void shouldThrowInvalidRequestWhenDurationExceedsSingleDayCap() {
            final CrownFallbackRequest request = validRequest(UUID.randomUUID().toString())
                    .setDurationInMinutes(400);

            Assertions.assertThrows(CrownFallbackInvalidRequestException.class,
                    () -> service.crownFallbackSearchAndBook(request));
        }

        @Test
        void shouldThrowInvalidRequestWhenDurationIsZero() {
            final CrownFallbackRequest request = validRequest(UUID.randomUUID().toString())
                    .setDurationInMinutes(0);

            Assertions.assertThrows(CrownFallbackInvalidRequestException.class,
                    () -> service.crownFallbackSearchAndBook(request));
        }

        @Test
        void shouldThrowInvalidRequestWhenRequiredFieldsMissing() {
            final CrownFallbackRequest request = new CrownFallbackRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setDurationInMinutes(10);
            // missing courtCentreId, hearingDate, source

            Assertions.assertThrows(CrownFallbackInvalidRequestException.class,
                    () -> service.crownFallbackSearchAndBook(request));
        }

        @Test
        void shouldStampExemptSabSourceOnAllocatedSlot() {
            // SPRDT-1159: allocated_listings.source records HOW the allocation was made — EXEMPT_SAB
            // for search-and-book onto an existing session. The caller's CROWN_FB_* label stays on
            // the response only (asserted elsewhere).
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId).setSource("CROWN_FB_ADJOURN");

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.empty());
            final CourtSchedule session = buildSession(UUID.randomUUID().toString(), LocalDate.parse("2026-04-21"), false, "CR");
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(session, false)));

            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            when(courtScheduleRepository.saveBookedSlots(slotCaptor.capture(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownFallbackResponse response = service.crownFallbackSearchAndBook(request);

            final List<AllocatedSlot> persisted = slotCaptor.getValue();
            assertEquals(1, persisted.size());
            assertEquals("EXEMPT_SAB", persisted.get(0).getSource());
            verify(allocatedListingRepository).updateSourceByHearingId(hearingId, "EXEMPT_SAB");
            // response still echoes the caller's label
            assertEquals("CROWN_FB_ADJOURN", response.source());
        }

        private CrownFallbackRequest validRequest(final String hearingId) {
            return new CrownFallbackRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setCourtRoomId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.parse("2026-04-21"))
                    .setEarliestHearingTime("2026-04-21T09:00:00Z")
                    .setDurationInMinutes(10)
                    .setSource("CROWN_FB_LIST");
        }

        private CourtSchedule buildSession(final String id, final LocalDate date, final boolean isDraft, final String businessType) {
            final CourtSchedule cs = new CourtSchedule();
            cs.setCourtScheduleId(id);
            cs.setSessionDate(date);
            cs.setOuCode("C01CY00");
            cs.setCourtRoomId("731816");
            cs.setBusinessType(businessType);
            cs.setIsDraft(isDraft);
            return cs;
        }
    }

    // ─── AC1: CROWN single-day strict + fallback + idempotency ────────────────

    @Nested
    class CrownSearchAndBook {

        @Test
        void should_rejectSingleDayWithoutSource_when_crownSingleDayHasNoSource() {
            // AC1 — single-day CROWN search-and-book delegates to the fallback engine, which requires a
            // source. A single-day request with no source is rejected (a known single session is a
            // list.hearings-in-sessions case, not search-and-book).
            final String hearingId = UUID.randomUUID().toString();
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(360);

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());

            org.junit.jupiter.api.Assertions.assertThrows(
                    uk.gov.moj.cpp.courtscheduler.exception.CrownFallbackInvalidRequestException.class,
                    () -> service.crownSearchAndBook(request));
        }

        @Test
        void should_bookViaFallback_when_crownSingleDayWithSource() {
            // AC1 — single-day with a source books via the existing fallback engine; the response carries
            // the booked courtScheduleId and the CROWN_FB_* source.
            final String hearingId = UUID.randomUUID().toString();
            final String scheduleId = UUID.randomUUID().toString();
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(300)
                    .setSource("CROWN_FB_LIST");

            final CourtSchedule session = new CourtSchedule();
            session.setCourtScheduleId(scheduleId);
            session.setSessionDate(LocalDate.of(2026, 9, 1));
            session.setOuCode("C01CY00");
            session.setCourtRoomId("731816");

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(),
                    org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(session, false)));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            assertEquals(hearingId, response.hearingId());
            assertEquals(scheduleId, response.courtScheduleId());
            assertEquals("CROWN_FB_LIST", response.source());
            // flat fallback fields surfaced for single-day (SPRDT-1089)
            assertEquals("731816", response.courtRoomId());
            assertEquals("2026-09-01", response.sessionDate());
            assertEquals(Boolean.FALSE, response.isDraft());
            assertEquals(Boolean.FALSE, response.overbooked());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void should_returnExistingAllocation_when_crownHearingIdAlreadyBooked() {
            // AC1 — idempotency: hearingId already allocated => existing allocation returned, no new booking
            final String hearingId = UUID.randomUUID().toString();
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(180);

            final AllocatedListing existing = new AllocatedListing();
            existing.setHearingId(hearingId);
            existing.setCourtScheduleId("cs-existing");

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.of(existing));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);
            assertEquals("cs-existing", response.courtScheduleId());

            // Idempotency contract: no new save when existing allocation found
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void should_bookFreshMultiDay_when_everyDayIsFullAndOverbookingNotAllowed() {
            // F1 (court-calendar always-assign rule) on the LIVE path: listing's multiday update
            // arrives here via POST /hearings/{id} crown.search.and.book. Both candidate days are
            // fully booked with is_overbooking_allowed=false — the booking must proceed anyway
            // (overbooked days are advisorily logged), never return empty.
            final String anchorId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSessionOn(LocalDate.of(2026, 9, 7), 360, 360, false, anchorId),
                    buildSession(LocalDate.of(2026, 9, 8), 360, 360, false));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(720);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(List.of());
            when(courtScheduleRepository.findConsecutiveSessions(anchorId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            assertEquals(2, response.sessions().size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void should_bookMultiDayMove_when_targetDaysAreFullAndOverbookingNotAllowed() {
            // F1 on the MOVE leg: the hearing already holds an allocation elsewhere and is moved to
            // an anchor whose run is fully booked by OTHER hearings, overbooking disallowed. The
            // move must still book (release-then-book), not leave the hearing on its old days.
            final String anchorId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();
            final String oldScheduleId = UUID.randomUUID().toString();

            final AllocatedListing existing = new AllocatedListing();
            existing.setHearingId(hearingId);
            existing.setCourtScheduleId(oldScheduleId);
            existing.setDuration(360);

            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule oldSession =
                    buildSessionOn(LocalDate.of(2026, 9, 1), 360, 360, false, oldScheduleId);
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSessionOn(LocalDate.of(2026, 9, 7), 360, 360, false, anchorId),
                    buildSession(LocalDate.of(2026, 9, 8), 360, 360, false));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(720);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(List.of(existing));
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(oldScheduleId)))
                    .thenReturn(List.of(oldSession));
            when(courtScheduleRepository.findConsecutiveSessions(anchorId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            assertEquals(2, response.sessions().size());
            assertEquals("MOVE", response.source());
            verify(courtScheduleRepository).releaseOldAllocatedListings(hearingId);
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void should_bookConsecutiveWeekdaysFromAnchor_when_crownMultiDayWithCourtScheduleId() {
            // AC2 — multi-day with anchor: durationInMinutes > 360, courtScheduleId present
            // daysNeeded = ceil(1080/360) = 3; expects findConsecutiveSessions called with anchorId, 3
            final String anchorId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates =
                    buildConsecutiveSessions(LocalDate.of(2026, 9, 7), 3);

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(1080);

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());
            lenient().when(courtScheduleRepository.findConsecutiveSessions(anchorId, 3))
                    .thenReturn(candidates);
            lenient().when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);
            verify(courtScheduleRepository).findConsecutiveSessions(anchorId, 3);
            assertEquals(3, response.sessions().size());
        }

        @Test
        void should_throwSlotsBookException_when_crownPersistFails() {
            // A persist failure must surface (not be swallowed as an empty/"no slot" result).
            final String anchorId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates =
                    buildConsecutiveSessions(LocalDate.of(2026, 9, 7), 3);

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(1080);

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());
            lenient().when(courtScheduleRepository.findConsecutiveSessions(anchorId, 3))
                    .thenReturn(candidates);
            lenient().when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("db down", false));

            org.junit.jupiter.api.Assertions.assertThrows(
                    uk.gov.moj.cpp.courtscheduler.exception.SlotsBookException.class,
                    () -> service.crownSearchAndBook(request));
        }

        @Test
        void should_searchCourtCentreForConsecutiveRoom_when_crownMultiDayNoAnchor() {
            // AC3 — multi-day no anchor: durationInMinutes > 360, no courtScheduleId
            // Expects new repository method: findConsecutiveSessionsForCentre(courtCentreId, hearingDate, daysNeeded)
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates =
                    buildConsecutiveSessions(LocalDate.of(2026, 9, 7), 2);

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setDurationInMinutes(720); // daysNeeded = ceil(720/360) = 2

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());
            lenient().when(courtScheduleRepository.findConsecutiveSessionsForCentre(courtCentreId, LocalDate.of(2026, 9, 7), 2))
                    .thenReturn(candidates);
            lenient().when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);
            verify(courtScheduleRepository).findConsecutiveSessionsForCentre(courtCentreId, LocalDate.of(2026, 9, 7), 2);
            assertEquals(2, response.sessions().size());
        }

        @Test
        void should_computeDaysNeededAsCeil_when_duration361() {
            // AC6 — threshold: 361 mins => ceil(361/360) = 2 days => multi-day path
            // daysNeeded must equal 2, not 1 (no floor/truncation)
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates =
                    buildConsecutiveSessions(LocalDate.of(2026, 9, 8), 2);

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setHearingDate(LocalDate.of(2026, 9, 8))
                    .setDurationInMinutes(361);

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());
            lenient().when(courtScheduleRepository.findConsecutiveSessionsForCentre(courtCentreId, LocalDate.of(2026, 9, 8), 2))
                    .thenReturn(candidates);
            lenient().when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            service.crownSearchAndBook(request);
            verify(courtScheduleRepository).findConsecutiveSessionsForCentre(courtCentreId, LocalDate.of(2026, 9, 8), 2);
        }

        @Test
        void should_treatAs_singleDay_when_duration360() {
            // AC6 — exactly 360 mins with no date range => single-day path (delegates to fallback), NOT multi-day.
            // Neither consecutive search may be called.
            final String hearingId = UUID.randomUUID().toString();
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 8))
                    .setDurationInMinutes(360)
                    .setSource("CROWN_FB_LIST");

            final CourtSchedule session = new CourtSchedule();
            session.setCourtScheduleId(UUID.randomUUID().toString());
            session.setSessionDate(LocalDate.of(2026, 9, 8));
            session.setOuCode("C01CY00");
            session.setCourtRoomId("731816");

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(),
                    org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(session, false)));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            service.crownSearchAndBook(request);

            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .findConsecutiveSessions(any(), org.mockito.Mockito.anyInt());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .findConsecutiveSessionsForCentre(any(), any(), org.mockito.Mockito.anyInt());
        }

        @Test
        void should_treatAsMultiDay_when_dateRangeOnlyEndDateAfterStart() {
            // AC6 — date-range form: endDate > hearingDate, durationInMinutes absent => multi-day
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setHearingDate(LocalDate.of(2026, 9, 8))
                    .setEndDate(LocalDate.of(2026, 9, 10)); // no durationInMinutes

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());

            service.crownSearchAndBook(request);

            // date range 8..10 Sep (inclusive) => 3 days => no-anchor multi-day centre search
            verify(courtScheduleRepository).findConsecutiveSessionsForCentre(
                    eq(courtCentreId), eq(LocalDate.of(2026, 9, 8)), eq(3));
        }

        // ─── STE bug: multi-day MOVE via existing allocated_listings rows ────

        @Test
        void should_moveHearing_when_multiDayAnchorNotAmongExistingAllocations() {
            // Live STE bug (ns-ste-ccm-34): a CROWN multi-day hearing already has allocated_listings
            // rows (created UNALLOCATED, one per day) and is re-searched with a NEW anchor that is
            // NOT one of those rows (e.g. update-hearing-for-listing picked a different FINAL
            // session). Must release ALL existing rows, book the new consecutive run, and tag the
            // new rows source=MOVE — NOT short-circuit with emptyList sessions.
            final String hearingId = UUID.randomUUID().toString();
            final String anchorId = UUID.randomUUID().toString();
            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocation(hearingId, "cs-old-day1"),
                    existingAllocation(hearingId, "cs-old-day2"),
                    existingAllocation(hearingId, "cs-old-day3"));
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> newCandidates =
                    buildConsecutiveSessions(LocalDate.of(2026, 9, 7), 3); // Mon, Tue, Wed

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(1080);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            // Anchor is unrelated to any existing row, so its (unsorted) date doesn't matter here.
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs-old-day1", "cs-old-day2", "cs-old-day3")))
                    .thenReturn(buildConsecutiveSessions(LocalDate.of(2026, 6, 1), 3));
            when(courtScheduleRepository.findConsecutiveSessions(anchorId, 3)).thenReturn(newCandidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository).releaseOldAllocatedListings(hearingId);
            @SuppressWarnings("unchecked")
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotsCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).saveBookedSlots(slotsCaptor.capture(), eq(false), eq(false));
            assertTrue(slotsCaptor.getValue().stream().allMatch(s -> "MOVE".equals(s.getSource())));
            verify(allocatedListingRepository, org.mockito.Mockito.never()).updateSourceByHearingId(any(), any());
            assertEquals(3, response.sessions().size());
            assertEquals("MOVE", response.source());
        }

        @Test
        void should_moveHearing_when_multiDayNoAnchorButExistingAllocationsPresent() {
            // Same bug, no-anchor form: update-hearing-for-listing re-searches the whole centre
            // (no anchor) for a hearing that already has allocated_listings rows. Absence of an
            // anchor can never be a "same allocation" retry, so this is also a MOVE.
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocation(hearingId, "cs-old-day1"),
                    existingAllocation(hearingId, "cs-old-day2"));
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> newCandidates =
                    buildConsecutiveSessions(LocalDate.of(2026, 9, 7), 2); // Mon, Tue

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setDurationInMinutes(720);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.findConsecutiveSessionsForCentre(courtCentreId, LocalDate.of(2026, 9, 7), 2))
                    .thenReturn(newCandidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository).releaseOldAllocatedListings(hearingId);
            @SuppressWarnings("unchecked")
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotsCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).saveBookedSlots(slotsCaptor.capture(), eq(false), eq(false));
            assertTrue(slotsCaptor.getValue().stream().allMatch(s -> "MOVE".equals(s.getSource())));
            verify(allocatedListingRepository, org.mockito.Mockito.never()).updateSourceByHearingId(any(), any());
            assertEquals(2, response.sessions().size());
            assertEquals("MOVE", response.source());
        }

        @Test
        void should_moveHearing_when_multiDayAnchorIsLaterDayInExistingBlock() {
            // Live STE bug (hearing 00b7b8bd/072b7512): hearing booked [07-03,07-06,07-07]; a
            // legitimate MOVE re-anchors at 07-06 — a CONTINUATION day of the existing block, not
            // its first (earliest) day. The retry guard must be anchor-strict: idempotent ONLY when
            // the anchor equals the block's FIRST session. Anchoring on a later day of the SAME
            // block is still a genuine move — release the old block and book a fresh consecutive
            // run starting at the anchor's date.
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate day1 = LocalDate.of(2026, 7, 3); // Friday
            final LocalDate day2 = LocalDate.of(2026, 7, 6); // Monday (next business day)
            final LocalDate day3 = LocalDate.of(2026, 7, 7); // Tuesday
            final String csDay1 = "cs-old-day1";
            final String csDay2 = "cs-old-day2";
            final String csDay3 = "cs-old-day3";

            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocation(hearingId, csDay1),
                    existingAllocation(hearingId, csDay2),
                    existingAllocation(hearingId, csDay3));
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> existingSessions = List.of(
                    buildSessionWithId(day1, csDay1),
                    buildSessionWithId(day2, csDay2),
                    buildSessionWithId(day3, csDay3));
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> newCandidates =
                    buildConsecutiveSessions(day2, 3); // [07-06, 07-07, 07-08]

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(day2)
                    .setCourtScheduleId(csDay2)
                    .setDurationInMinutes(1080);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(csDay1, csDay2, csDay3)))
                    .thenReturn(existingSessions);
            when(courtScheduleRepository.findConsecutiveSessions(csDay2, 3)).thenReturn(newCandidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository).releaseOldAllocatedListings(hearingId);
            @SuppressWarnings("unchecked")
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotsCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).saveBookedSlots(slotsCaptor.capture(), eq(false), eq(false));
            assertTrue(slotsCaptor.getValue().stream().allMatch(s -> "MOVE".equals(s.getSource())));
            verify(allocatedListingRepository, org.mockito.Mockito.never()).updateSourceByHearingId(any(), any());
            assertEquals(3, response.sessions().size());
            assertEquals("MOVE", response.source());
            assertEquals(day2, response.sessions().get(0).getSessionDate());
        }

        @Test
        void should_returnExistingBookedSessions_when_multiDayAnchorAlreadyAllocated() {
            // Genuine retry: the requested anchor IS already one of the hearing's booked days.
            // Must stay idempotent (no release, no re-book) but return the EXISTING booked
            // sessions, not emptyList, so the caller's enrichment doesn't collapse.
            final String hearingId = UUID.randomUUID().toString();
            final String anchorId = "cs-existing-day1";
            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocation(hearingId, anchorId),
                    existingAllocation(hearingId, "cs-existing-day2"),
                    existingAllocation(hearingId, "cs-existing-day3"));
            // Anchor must equal the block's FIRST (earliest) session under the anchor-strict check,
            // so give the mocked sessions matching ids/dates rather than unrelated random ids.
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> existingSessions = List.of(
                    buildSessionWithId(LocalDate.of(2026, 9, 7), anchorId),
                    buildSessionWithId(LocalDate.of(2026, 9, 8), "cs-existing-day2"),
                    buildSessionWithId(LocalDate.of(2026, 9, 9), "cs-existing-day3"));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(1080);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(
                    List.of(anchorId, "cs-existing-day2", "cs-existing-day3")))
                    .thenReturn(existingSessions);
            // SPRDT-1273: same start date → resize family, delegated to the extend/shrink service,
            // which no-ops here (requested 1080 mins = 3 business days = the existing block).
            when(extendMultidayHearingService.extend(
                    eq(hearingId), eq(LocalDate.of(2026, 9, 7)), eq(LocalDate.of(2026, 9, 9)),
                    eq(1080), isNull(), isNull(), eq(360)))
                    .thenReturn(new java.util.ArrayList<>(existingSessions));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository, org.mockito.Mockito.never()).releaseOldAllocatedListings(any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
            assertEquals(anchorId, response.courtScheduleId());
            assertEquals(3, response.sessions().size());
        }

        @Test
        void should_leaveExistingAllocationIntact_when_multiDayMoveFindsNoQualifyingRun() {
            // A MOVE search that finds nothing must NOT release the prior allocation — a search
            // miss can never orphan the hearing (mirrors moveHearingToPastDate's search-before-release
            // ordering).
            final String hearingId = UUID.randomUUID().toString();
            final String anchorId = UUID.randomUUID().toString();
            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocation(hearingId, "cs-old-day1"),
                    existingAllocation(hearingId, "cs-old-day2"),
                    existingAllocation(hearingId, "cs-old-day3"));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(1080);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            // Anchor is unrelated to any existing row, so its (unsorted) date doesn't matter here.
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs-old-day1", "cs-old-day2", "cs-old-day3")))
                    .thenReturn(buildConsecutiveSessions(LocalDate.of(2026, 6, 1), 3));
            when(courtScheduleRepository.findConsecutiveSessions(anchorId, 3))
                    .thenReturn(buildConsecutiveSessions(LocalDate.of(2026, 9, 7), 1)); // only 1 of 3 needed

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository, org.mockito.Mockito.never()).releaseOldAllocatedListings(any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
            verify(allocatedListingRepository, org.mockito.Mockito.never())
                    .updateSourceByHearingId(any(), any());
            assertTrue(response.sessions().isEmpty());
        }

        @Test
        void should_moveHearing_when_newRunOverlapsHearingsOwnCurrentlyOccupiedDays() {
            // Realistic version of the above scenario: totalBooked is a live SUM(duration) over
            // allocated_listings, so until the OLD rows are actually released, a re-searched candidate
            // that happens to be one of the hearing's own current days comes back fully consumed
            // (totalBooked == maxDuration) by that same not-yet-released booking. The availability
            // check must not reject the day for that reason alone — reclaimHearingsOwnCapacity nets
            // out the hearing's own not-yet-released contribution before the check runs.
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate day1 = LocalDate.of(2026, 7, 3);
            final LocalDate day2 = LocalDate.of(2026, 7, 6);
            final LocalDate day3 = LocalDate.of(2026, 7, 7);
            final LocalDate day4 = LocalDate.of(2026, 7, 8);
            final String csDay1 = "cs-old-day1";
            final String csDay2 = "cs-old-day2";
            final String csDay3 = "cs-old-day3";
            final String csDay4 = "cs-new-day4";

            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocationWithDuration(hearingId, csDay1, 360),
                    existingAllocationWithDuration(hearingId, csDay2, 360),
                    existingAllocationWithDuration(hearingId, csDay3, 360));
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> existingSessions = List.of(
                    buildSessionWithId(day1, csDay1),
                    buildSessionWithId(day2, csDay2),
                    buildSessionWithId(day3, csDay3));

            // Re-search finds day2/day3 STILL showing fully booked (their own not-yet-released 360
            // mins), plus a fresh day4 with full availability.
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule fullyOccupiedDay2 =
                    buildSession(day2, 360, 360, false);
            fullyOccupiedDay2.setCourtScheduleId(csDay2);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule fullyOccupiedDay3 =
                    buildSession(day3, 360, 360, false);
            fullyOccupiedDay3.setCourtScheduleId(csDay3);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule freshDay4 = buildSessionWithId(day4, csDay4);
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> newCandidates =
                    List.of(fullyOccupiedDay2, fullyOccupiedDay3, freshDay4);

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(day2)
                    .setCourtScheduleId(csDay2)
                    .setDurationInMinutes(1080);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(csDay1, csDay2, csDay3)))
                    .thenReturn(existingSessions);
            when(courtScheduleRepository.findConsecutiveSessions(csDay2, 3)).thenReturn(newCandidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository).releaseOldAllocatedListings(hearingId);
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
            assertEquals(3, response.sessions().size());
            assertEquals("MOVE", response.source());
        }

        // ─── SPRDT-1273: with existing rows the decision is hearing-state-driven. Same start
        // date = RESIZE, delegated to ExtendMultidayHearingService: EXTEND books only the tail
        // days (existing rows and their rooms untouched), SHRINK releases only the tail rows,
        // and an unchanged end date is a no-op that returns the current block. A different
        // start date is a MOVE (release + reclaim + re-book). ───

        @Test
        void should_extendSingleDayHearingToMultiDay_when_sameStartRequestsMoreDays() {
            // Hearing holds ONE allocated_listings row (its single-day booking on 2026-07-20);
            // update-hearing-for-listing requests 720 mins (2 days) from the same start date.
            // RESIZE → extend: the existing row is untouched and only the tail day is booked.
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate day1 = LocalDate.of(2026, 7, 20); // Monday
            final LocalDate day2 = LocalDate.of(2026, 7, 21); // Tuesday
            final String anchorId = "cs-own-day1";

            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocationWithDuration(hearingId, anchorId, 360));
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> existingSessions = List.of(
                    buildSessionWithId(day1, anchorId));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(day1)
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(720);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(anchorId)))
                    .thenReturn(existingSessions);
            when(extendMultidayHearingService.extend(eq(hearingId), eq(day1), eq(day2), eq(720), isNull(), isNull(), eq(360)))
                    .thenReturn(new java.util.ArrayList<>(List.of(
                            buildSessionWithId(day1, anchorId),
                            buildSessionWithId(day2, "cs-new-day2"))));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            // The resize never releases the block wholesale — that was the SPRDT-1273 bug.
            verify(courtScheduleRepository, org.mockito.Mockito.never()).releaseOldAllocatedListings(any());
            verify(extendMultidayHearingService).extend(eq(hearingId), eq(day1), eq(day2), eq(720), isNull(), isNull(), eq(360));
            assertEquals(2, response.sessions().size());
            assertEquals(anchorId, response.sessions().get(0).getCourtScheduleId());
            assertEquals(day1, response.sessions().get(0).getSessionDate());
            assertEquals(day2, response.sessions().get(1).getSessionDate());
        }

        @Test
        void should_moveNotResize_when_sameStartButAnchorOutsideExistingBlock() {
            // The unallocated→allocated flow: the hearing holds DRAFT sessions and the update
            // anchors on a NEW (final) session for the SAME start date. The caller is choosing
            // different sessions — a MOVE. A resize here would no-op (same size) and silently
            // swallow the allocation.
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate day1 = LocalDate.of(2026, 9, 7);
            final LocalDate day2 = LocalDate.of(2026, 9, 8);
            final String draftDay1 = "cs-draft-day1";
            final String draftDay2 = "cs-draft-day2";
            final String newFinalAnchor = "cs-final-day1";

            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocationWithDuration(hearingId, draftDay1, 360),
                    existingAllocationWithDuration(hearingId, draftDay2, 360));
            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(draftDay1, draftDay2)))
                    .thenReturn(List.of(buildSessionWithId(day1, draftDay1), buildSessionWithId(day2, draftDay2)));
            when(courtScheduleRepository.findConsecutiveSessions(newFinalAnchor, 2))
                    .thenReturn(List.of(
                            buildSessionWithId(day1, newFinalAnchor),
                            buildSessionWithId(day2, "cs-final-day2")));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(day1)
                    .setCourtScheduleId(newFinalAnchor)
                    .setDurationInMinutes(720);

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(extendMultidayHearingService, org.mockito.Mockito.never())
                    .extend(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
            verify(courtScheduleRepository).releaseOldAllocatedListings(hearingId);
            assertEquals(2, response.sessions().size());
            assertEquals("MOVE", response.source());
        }

        @Test
        void should_passRequestedCourtRoom_when_resizeCarriesMainCourtRoomId() {
            // SPRDT-1273: the submitted main courtroom travels to the extend service so the tail
            // days are booked into it (not into the anchor session's room).
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate day1 = LocalDate.of(2026, 7, 20);
            final LocalDate day2 = LocalDate.of(2026, 7, 21);
            final String anchorId = "cs-own-day1";
            final String mainRoom = "731816c1-5ee4-373a-9bda-840e13a5bcb0";

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(List.of(
                    existingAllocationWithDuration(hearingId, anchorId, 360)));
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(anchorId)))
                    .thenReturn(List.of(buildSessionWithId(day1, anchorId)));
            when(extendMultidayHearingService.extend(eq(hearingId), eq(day1), eq(day2), eq(720), eq(mainRoom), isNull(), eq(360)))
                    .thenReturn(new java.util.ArrayList<>(List.of(
                            buildSessionWithId(day1, anchorId),
                            buildSessionWithId(day2, "cs-new-day2"))));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(day1)
                    .setCourtScheduleId(anchorId)
                    .setCourtRoomId(mainRoom)
                    .setDurationInMinutes(720);

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(extendMultidayHearingService).extend(eq(hearingId), eq(day1), eq(day2), eq(720), eq(mainRoom), isNull(), eq(360));
            assertEquals(2, response.sessions().size());
        }

        @Test
        void should_honourRequestedEndDate_when_resizeSuppliesEndDate() {
            // endDate on the request wins over the duration-derived business-day expansion.
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate day1 = LocalDate.of(2026, 7, 20);
            final LocalDate requestedEnd = LocalDate.of(2026, 7, 24);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(List.of(
                    existingAllocationWithDuration(hearingId, "cs-own-day1", 360)));
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs-own-day1")))
                    .thenReturn(List.of(buildSessionWithId(day1, "cs-own-day1")));
            when(extendMultidayHearingService.extend(eq(hearingId), eq(day1), eq(requestedEnd), eq(720), isNull(), isNull(), eq(144)))
                    .thenReturn(new java.util.ArrayList<>(List.of(buildSessionWithId(day1, "cs-own-day1"))));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(day1)
                    .setEndDate(requestedEnd)
                    .setCourtScheduleId("cs-own-day1")
                    .setDurationInMinutes(720);

            service.crownSearchAndBook(request);

            verify(extendMultidayHearingService).extend(eq(hearingId), eq(day1), eq(requestedEnd), eq(720), isNull(), isNull(), eq(144));
        }

        @Test
        void should_shrinkBlock_when_requestedFewerDaysThanExistingBlock() {
            // Resize in the other direction: a 3-day block re-requested for 720 mins (2 days) from
            // the same start date. Delegated to the extend/shrink service, which releases ONLY the
            // tail row — never the whole block.
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate day1 = LocalDate.of(2026, 9, 7);  // Monday
            final LocalDate day2 = LocalDate.of(2026, 9, 8);  // Tuesday
            final LocalDate day3 = LocalDate.of(2026, 9, 9);  // Wednesday
            final String csDay1 = "cs-own-day1";
            final String csDay2 = "cs-own-day2";
            final String csDay3 = "cs-own-day3";

            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocationWithDuration(hearingId, csDay1, 360),
                    existingAllocationWithDuration(hearingId, csDay2, 360),
                    existingAllocationWithDuration(hearingId, csDay3, 360));
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> existingSessions = List.of(
                    buildSessionWithId(day1, csDay1),
                    buildSessionWithId(day2, csDay2),
                    buildSessionWithId(day3, csDay3));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(day1)
                    .setCourtScheduleId(csDay1)
                    .setDurationInMinutes(720);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(csDay1, csDay2, csDay3)))
                    .thenReturn(existingSessions);
            when(extendMultidayHearingService.extend(eq(hearingId), eq(day1), eq(day2), eq(720), isNull(), isNull(), eq(360)))
                    .thenReturn(new java.util.ArrayList<>(List.of(
                            buildSessionWithId(day1, csDay1),
                            buildSessionWithId(day2, csDay2))));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository, org.mockito.Mockito.never()).releaseOldAllocatedListings(any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
            verify(extendMultidayHearingService).extend(eq(hearingId), eq(day1), eq(day2), eq(720), isNull(), isNull(), eq(360));
            assertEquals(2, response.sessions().size());
        }

        @Test
        void should_stayIdempotent_when_retryMatchesExistingBlockSizeAndAnchor() {
            // Genuine retry of a 2-day booking: same anchor (block's first day) AND same day count
            // (720 mins → 2 days == 2 existing rows). Must short-circuit — no release, no re-book —
            // and return the existing block.
            final String hearingId = UUID.randomUUID().toString();
            final String anchorId = "cs-existing-day1";
            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocation(hearingId, anchorId),
                    existingAllocation(hearingId, "cs-existing-day2"));
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> existingSessions = List.of(
                    buildSessionWithId(LocalDate.of(2026, 9, 7), anchorId),
                    buildSessionWithId(LocalDate.of(2026, 9, 8), "cs-existing-day2"));

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 7))
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(720);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(anchorId, "cs-existing-day2")))
                    .thenReturn(existingSessions);
            // Same start + same day count → the extend/shrink service no-ops and hands back the block.
            when(extendMultidayHearingService.extend(
                    eq(hearingId), eq(LocalDate.of(2026, 9, 7)), eq(LocalDate.of(2026, 9, 8)),
                    eq(720), isNull(), isNull(), eq(360)))
                    .thenReturn(new java.util.ArrayList<>(existingSessions));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository, org.mockito.Mockito.never()).releaseOldAllocatedListings(any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
            assertEquals(anchorId, response.courtScheduleId());
            assertEquals(2, response.sessions().size());
        }

        private AllocatedListing existingAllocation(final String hearingId, final String courtScheduleId) {
            final AllocatedListing allocation = new AllocatedListing();
            allocation.setHearingId(hearingId);
            allocation.setCourtScheduleId(courtScheduleId);
            allocation.setSource("NONPOLICE");
            return allocation;
        }

        private AllocatedListing existingAllocationWithDuration(
                final String hearingId, final String courtScheduleId, final int duration) {
            final AllocatedListing allocation = existingAllocation(hearingId, courtScheduleId);
            allocation.setDuration(duration);
            return allocation;
        }
    }

    // ─── AC4 + AC5: MAGS single-day and multi-day sparse ─────────────────────

    @Nested
    class MagsSearchAndBook {

        @Test
        void should_bookSingleDayViaCascade_when_magsSingleDay() {
            // MAGS single-day: team/ccsph2 cascade via searchBookHearingSlots, keyed on the courtCentreId
            // UUID (court_house_id). No sparse search; no isPolice->isSearchUpdate re-search.
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(180)
                    .setIsPolice(true);

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());
            when(courtScheduleRepository.searchBookHearingSlots(any())).thenAnswer(inv -> {
                final List<AllocatedSlot> slots = inv.getArgument(0);
                slots.get(0).setCourtScheduleId("cs-single");
                return true;
            });
            lenient().when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs-single")))
                    .thenReturn(List.of(buildSession(LocalDate.of(2026, 9, 1))));

            final MagsSearchAndBookResponse response = service.magsSearchAndBook(request);

            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> captor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).searchBookHearingSlots(captor.capture());
            final AllocatedSlot booked = captor.getValue().get(0);
            assertEquals(courtCentreId, booked.getCourtCentreId());
            assertTrue(booked.isPolice());
            assertEquals(1, response.sessions().size());
        }

        @Test
        void should_bookMultiDayConsecutive_when_magsDurationOver360() {
            // MAGS multi-day: durationInMinutes > 360 -> CONSECUTIVE business days (no sparse), via
            // findConsecutiveSessionsForCentre, booked by courtScheduleId.
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> consecutive = List.of(
                    buildSession(LocalDate.of(2026, 9, 1)),
                    buildSession(LocalDate.of(2026, 9, 2)));

            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(720)
                    .setIsPolice(false);

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());
            lenient().when(courtScheduleRepository.findConsecutiveSessionsForCentre(courtCentreId, LocalDate.of(2026, 9, 1), 2))
                    .thenReturn(consecutive);
            lenient().when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final MagsSearchAndBookResponse response = service.magsSearchAndBook(request);
            verify(courtScheduleRepository).findConsecutiveSessionsForCentre(courtCentreId, LocalDate.of(2026, 9, 1), 2);
            assertEquals(2, response.sessions().size());
        }

        @Test
        void should_bookMultiDayConsecutive_when_magsEndDateAfterStartDate() {
            // MAGS multi-day via date-range (endDate > hearingDate): daysNeeded from the inclusive range
            // (1..3 Sep = 3 days) drives the CONSECUTIVE centre search.
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setEndDate(LocalDate.of(2026, 9, 3))
                    .setIsPolice(true);

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());

            service.magsSearchAndBook(request);
            verify(courtScheduleRepository).findConsecutiveSessionsForCentre(
                    eq(courtCentreId), eq(LocalDate.of(2026, 9, 1)), eq(3));
        }

        @Test
        void should_bookByCourtScheduleId_notReSearch_when_magsMultiDayIsPolice() {
            // Regression guard (STE bug): a police multi-day must book by courtScheduleId via the consecutive
            // path, NOT route isPolice into the court_house_id=ouCode re-search. saveBookedSlots' isSearchUpdate
            // flag must be false, never isPolice.
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> consecutive = List.of(
                    buildSession(LocalDate.of(2026, 9, 1)),
                    buildSession(LocalDate.of(2026, 9, 2)));

            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(720)
                    .setIsPolice(true);

            lenient().when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId))
                    .thenReturn(Optional.empty());
            lenient().when(courtScheduleRepository.findConsecutiveSessionsForCentre(courtCentreId, LocalDate.of(2026, 9, 1), 2))
                    .thenReturn(consecutive);
            lenient().when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            service.magsSearchAndBook(request);

            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
            verify(courtScheduleRepository, org.mockito.Mockito.never()).saveBookedSlots(any(), eq(false), eq(true));
        }
    }

    // ─── AC7: move-hearing-to-past-date ───────────────────────────────────────

    @Nested
    class MoveHearingToPastDate {

        @Test
        void should_throwNoSession_when_noPastSessionAvailable() {
            // AC7 — valid past date, no session found => NoSessionAvailableException (422 at the API layer)
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setJurisdiction("CROWN")
                    .setStartDate(LocalDate.of(2025, 1, 10)); // past, single date, centre search finds nothing

            org.junit.jupiter.api.Assertions.assertThrows(NoSessionAvailableException.class,
                    () -> service.moveHearingToPastDate(request));
        }

        @Test
        void should_bookConsecutivePastWeekdays_when_crownMoveHearingToPastDate() {
            // AC7 — CROWN past span (no anchor): books consecutive past weekdays via the centre search,
            // source=MOVE_TO_PAST_DATE.
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> pastSessions =
                    buildConsecutiveSessions(LocalDate.of(2025, 3, 3), 2); // Mon + Tue

            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setJurisdiction("CROWN")
                    .setStartDate(LocalDate.of(2025, 3, 3))
                    .setDurationInMinutes(720);

            lenient().when(courtScheduleRepository.findConsecutiveSessionsForCentre(
                    eq(courtCentreId), eq(LocalDate.of(2025, 3, 3)), eq(2)))
                    .thenReturn(pastSessions);
            lenient().when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final MoveHearingToPastDateResponse response = service.moveHearingToPastDate(request);
            assertEquals("MOVE_TO_PAST_DATE", response.source());
            assertEquals(2, response.sessions().size());
            @SuppressWarnings("unchecked")
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotsCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).saveBookedSlots(slotsCaptor.capture(), eq(false), eq(false));
            assertTrue(slotsCaptor.getValue().stream().allMatch(s -> "MOVE_TO_PAST_DATE".equals(s.getSource())));
        }

        @Test
        void should_bookConsecutivePastSessions_when_magsMoveHearingToPastDate() {
            // AC7 — MAGS past span: CONSECUTIVE past sessions booked (no sparse), mirroring the CROWN no-anchor path.
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> consecutivePast = List.of(
                    buildSession(LocalDate.of(2025, 3, 3)),
                    buildSession(LocalDate.of(2025, 3, 4))); // consecutive weekdays

            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(courtCentreId)
                    .setJurisdiction("MAGISTRATES")
                    .setStartDate(LocalDate.of(2025, 3, 3))
                    .setDurationInMinutes(720);

            lenient().when(courtScheduleRepository.findConsecutiveSessionsForCentre(eq(courtCentreId), eq(LocalDate.of(2025, 3, 3)), eq(2)))
                    .thenReturn(consecutivePast);
            lenient().when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            service.moveHearingToPastDate(request);
            verify(courtScheduleRepository).findConsecutiveSessionsForCentre(eq(courtCentreId), eq(LocalDate.of(2025, 3, 3)), eq(2));
        }

        @Test
        void should_treatAsMultiDay_when_moveHearingEndDateAfterStartDate() {
            // AC7 — endDate > startDate triggers the multi-day path; daysNeeded comes from the inclusive
            // date range (3..5 Mar = 3 days) and drives the centre search.
            final String courtCentreId = UUID.randomUUID().toString();
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(courtCentreId)
                    .setJurisdiction("CROWN")
                    .setStartDate(LocalDate.of(2025, 3, 3))
                    .setEndDate(LocalDate.of(2025, 3, 5)); // endDate > startDate => multi-day, 3 days inclusive

            service.moveHearingToPastDate(request);

            verify(courtScheduleRepository).findConsecutiveSessionsForCentre(
                    eq(courtCentreId), eq(LocalDate.of(2025, 3, 3)), eq(3));
        }
    }

    @Nested
    class ChangeCourtRoomForMultidayHearing {

        @Test
        void should_releaseAndBookOnlyChangedDays_when_validRequest() {
            // d1/d2/d3 currently allocated (room1); request re-allocates d2 and d3 only, onto room2
            // sessions with DIFFERENT per-day durations. d1 is untouched; response reflects d2/d3 only.
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate d1 = LocalDate.of(2025, 3, 3);
            final LocalDate d2 = LocalDate.of(2025, 3, 4);
            final LocalDate d3 = LocalDate.of(2025, 3, 5);

            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocation(hearingId, "cs1"),
                    existingAllocation(hearingId, "cs2"),
                    existingAllocation(hearingId, "cs3"));
            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs1", "cs2", "cs3")))
                    .thenReturn(List.of(
                            buildSessionWithId(d1, "cs1"),
                            buildSessionWithId(d2, "cs2"),
                            buildSessionWithId(d3, "cs3")));
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs2b", "cs3b")))
                    .thenReturn(List.of(
                            buildSessionWithId(d2, "cs2b"),
                            buildSessionWithId(d3, "cs3b")));

            final ChangeCourtRoomForMultidayHearingRequest request = new ChangeCourtRoomForMultidayHearingRequest()
                    .setHearingId(hearingId)
                    .setDays(List.of(
                            new RequestedDay(d2, "cs2b", 300),
                            new RequestedDay(d3, "cs3b", 200)));

            @SuppressWarnings("unchecked")
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotsCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            // Booking for changeCourtRoomForMultidayHearing must go through the NO-RELEASE variant
            // (releaseExistingHearingAllocations=false): the service already scoped its own release
            // to just the changed dates via releaseAllocatedListingsForDates, and the plain 3-arg
            // saveBookedSlots would trigger CourtScheduleRepository's internal hearing-wide release,
            // wiping out the untouched day's allocation (the production bug this test guards against).
            when(courtScheduleRepository.saveBookedSlots(slotsCaptor.capture(), eq(false), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final ChangeCourtRoomForMultidayHearingResponse response =
                    service.changeCourtRoomForMultidayHearing(request);

            verify(courtScheduleRepository).releaseAllocatedListingsForDates(hearingId, List.of(d2, d3));
            verify(courtScheduleRepository, org.mockito.Mockito.times(2))
                    .saveBookedSlots(any(), eq(false), eq(false), eq(false));
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());

            final List<AllocatedSlot> booked = slotsCaptor.getAllValues().stream()
                    .flatMap(List::stream).toList();
            assertEquals(2, booked.size());
            assertTrue(booked.stream().anyMatch(s -> "cs2b".equals(s.getCourtScheduleId()) && s.getDuration() == 300));
            assertTrue(booked.stream().anyMatch(s -> "cs3b".equals(s.getCourtScheduleId()) && s.getDuration() == 200));

            assertEquals("CHANGE_COURT_ROOM_MULTIDAY", response.source());
            assertEquals(hearingId, response.hearingId());
            assertEquals(2, response.allocatedSchedules().size());
            assertEquals(List.of("cs2b", "cs3b"), response.allocatedSchedules().stream()
                    .map(CourtSchedule::getCourtScheduleId).toList());
        }

        @Test
        void should_beIdempotentNoop_when_targetIsCurrentAllocation() {
            // Requesting d2's OWN current schedule id is a no-op: no release, no persist, but the
            // session still appears in the response.
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate d2 = LocalDate.of(2025, 3, 4);

            final List<AllocatedListing> existingAllocations = List.of(existingAllocation(hearingId, "cs2"));
            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs2")))
                    .thenReturn(List.of(buildSessionWithId(d2, "cs2")));

            final ChangeCourtRoomForMultidayHearingRequest request = new ChangeCourtRoomForMultidayHearingRequest()
                    .setHearingId(hearingId)
                    .setDays(List.of(new RequestedDay(d2, "cs2", 360)));

            final ChangeCourtRoomForMultidayHearingResponse response =
                    service.changeCourtRoomForMultidayHearing(request);

            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .releaseAllocatedListingsForDates(any(), any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
            assertEquals(1, response.allocatedSchedules().size());
            assertEquals("cs2", response.allocatedSchedules().get(0).getCourtScheduleId());
        }

        @Test
        void should_throwNoAllocationOnDate_when_hearingHasNoAllocationOnRequestedDate() {
            final String hearingId = UUID.randomUUID().toString();
            final List<AllocatedListing> existingAllocations = List.of(existingAllocation(hearingId, "cs1"));
            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);

            final ChangeCourtRoomForMultidayHearingRequest request = new ChangeCourtRoomForMultidayHearingRequest()
                    .setHearingId(hearingId)
                    .setDays(List.of(new RequestedDay(LocalDate.of(2025, 3, 6), "cs-target", 300)));

            org.junit.jupiter.api.Assertions.assertThrows(NoAllocationOnDateException.class,
                    () -> service.changeCourtRoomForMultidayHearing(request));

            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .releaseAllocatedListingsForDates(any(), any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void should_throwNoSessionAvailable_when_targetCourtScheduleIdUnknown() {
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate d2 = LocalDate.of(2025, 3, 4);
            final List<AllocatedListing> existingAllocations = List.of(existingAllocation(hearingId, "cs2"));
            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs2")))
                    .thenReturn(List.of(buildSessionWithId(d2, "cs2")));
            // "cs-unknown" is not returned by getCourtSchedulesByIdList (default empty list)

            final ChangeCourtRoomForMultidayHearingRequest request = new ChangeCourtRoomForMultidayHearingRequest()
                    .setHearingId(hearingId)
                    .setDays(List.of(new RequestedDay(d2, "cs-unknown", 300)));

            org.junit.jupiter.api.Assertions.assertThrows(NoSessionAvailableException.class,
                    () -> service.changeCourtRoomForMultidayHearing(request));

            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .releaseAllocatedListingsForDates(any(), any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void should_bookOverbooked_when_targetHasInsufficientCapacityAndOverbookingNotAllowed() {
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate d2 = LocalDate.of(2025, 3, 4);
            final List<AllocatedListing> existingAllocations = List.of(existingAllocation(hearingId, "cs2"));
            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs2")))
                    .thenReturn(List.of(buildSessionWithId(d2, "cs2")));
            final CourtSchedule fullSession = buildSessionWithId(d2, "cs2b");
            fullSession.setMaxDuration(360);
            fullSession.setTotalBooked(360);
            fullSession.setAvailableDuration(0);
            fullSession.setIsOverbookingAllowed(false);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs2b")))
                    .thenReturn(List.of(fullSession));
            @SuppressWarnings("unchecked")
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotsCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            when(courtScheduleRepository.saveBookedSlots(slotsCaptor.capture(), eq(false), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final ChangeCourtRoomForMultidayHearingRequest request = new ChangeCourtRoomForMultidayHearingRequest()
                    .setHearingId(hearingId)
                    .setDays(List.of(new RequestedDay(d2, "cs2b", 300)));

            final ChangeCourtRoomForMultidayHearingResponse response =
                    service.changeCourtRoomForMultidayHearing(request);

            verify(courtScheduleRepository).releaseAllocatedListingsForDates(hearingId, List.of(d2));
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false), eq(false));
            final List<AllocatedSlot> booked = slotsCaptor.getAllValues().stream()
                    .flatMap(List::stream).toList();
            assertTrue(booked.stream().anyMatch(s -> "cs2b".equals(s.getCourtScheduleId())));
            assertTrue(booked.stream().allMatch(s -> "MULTIDAY_COURTROOM_CHANGE".equals(s.getSource())));
            assertEquals("CHANGE_COURT_ROOM_MULTIDAY", response.source());
            assertEquals(1, response.allocatedSchedules().size());
            assertEquals("cs2b", response.allocatedSchedules().get(0).getCourtScheduleId());
        }

        @Test
        void should_mutateNothing_when_oneOfMultipleDaysIsInvalid() {
            // d2 is valid, d3's target session is unknown => the WHOLE request fails, and NOT EVEN d2
            // is released or booked (validate-all-first).
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate d2 = LocalDate.of(2025, 3, 4);
            final LocalDate d3 = LocalDate.of(2025, 3, 5);
            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocation(hearingId, "cs2"), existingAllocation(hearingId, "cs3"));
            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs2", "cs3")))
                    .thenReturn(List.of(buildSessionWithId(d2, "cs2"), buildSessionWithId(d3, "cs3")));

            final CourtSchedule validTarget = buildSessionWithId(d2, "cs2b");
            // cs3b is unknown (not returned) => target lookup yields null => NoSessionAvailableException
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of("cs2b", "cs3b")))
                    .thenReturn(List.of(validTarget));

            final ChangeCourtRoomForMultidayHearingRequest request = new ChangeCourtRoomForMultidayHearingRequest()
                    .setHearingId(hearingId)
                    .setDays(List.of(
                            new RequestedDay(d2, "cs2b", 300),
                            new RequestedDay(d3, "cs3b", 300)));

            org.junit.jupiter.api.Assertions.assertThrows(NoSessionAvailableException.class,
                    () -> service.changeCourtRoomForMultidayHearing(request));

            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .releaseAllocatedListingsForDates(any(), any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        private AllocatedListing existingAllocation(final String hearingId, final String courtScheduleId) {
            final AllocatedListing allocation = new AllocatedListing();
            allocation.setHearingId(hearingId);
            allocation.setCourtScheduleId(courtScheduleId);
            allocation.setSource("NONPOLICE");
            return allocation;
        }
    }

    @Nested
    class ReserveUnconfirmedHearing {

        @Test
        void shouldReserveUnconfirmedHearingAndStampExpiresAt() {
            final String sessionId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();
            final CourtSchedule session = buildSessionWithId(LocalDate.of(2026, 9, 10), sessionId);
            session.setActive(true);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(Collections.emptyList());
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(sessionId))).thenReturn(List.of(session));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final ReserveUnconfirmedHearingRequest request = new ReserveUnconfirmedHearingRequest()
                    .setHearingStartTime("2026-09-10T10:00:00+01:00")
                    .setSlotBased(true)
                    .setDuration(60);

            final ReserveUnconfirmedHearingResponse response =
                    service.reserveUnconfirmedHearing(sessionId, hearingId, request);

            assertEquals(sessionId, response.courtScheduleId());
            assertEquals(hearingId, response.hearingId());
            assertEquals("RESERVED_UNCONFIRMED", response.source());
            assertNotNull(response.expiresAt());

            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> captor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).saveBookedSlots(captor.capture(), eq(false), eq(false));
            final AllocatedSlot bookedSlot = captor.getValue().get(0);
            assertEquals(sessionId, bookedSlot.getCourtScheduleId());
            assertEquals(hearingId, bookedSlot.getHearingId());
            assertEquals(session.getOuCode(), bookedSlot.getOuCode());
            assertEquals("RESERVED_UNCONFIRMED", bookedSlot.getSource());
            assertNotNull(bookedSlot.getExpiresAt());
        }

        @Test
        void shouldThrowNoSessionAvailableExceptionWhenSessionMissing() {
            final String sessionId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(Collections.emptyList());
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(sessionId))).thenReturn(Collections.emptyList());

            final ReserveUnconfirmedHearingRequest request = new ReserveUnconfirmedHearingRequest()
                    .setHearingStartTime("2026-09-10T10:00:00+01:00")
                    .setSlotBased(true)
                    .setDuration(60);

            Assertions.assertThrows(NoSessionAvailableException.class,
                    () -> service.reserveUnconfirmedHearing(sessionId, hearingId, request));

            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void shouldRejectReserveWhenHearingAlreadyHasConfirmedAllocation() {
            final String sessionId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();
            final AllocatedListing confirmed = new AllocatedListing();
            confirmed.setHearingId(hearingId);
            confirmed.setCourtScheduleId(UUID.randomUUID().toString());
            confirmed.setExpiresAt(null);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(List.of(confirmed));

            final ReserveUnconfirmedHearingRequest request = new ReserveUnconfirmedHearingRequest()
                    .setHearingStartTime("2026-09-10T10:00:00+01:00")
                    .setSlotBased(true)
                    .setDuration(60);

            Assertions.assertThrows(ConfirmedBookingExistsException.class,
                    () -> service.reserveUnconfirmedHearing(sessionId, hearingId, request));

            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .getCourtSchedulesByIdList(any());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void shouldAllowReserveWhenExistingAllocationIsItselfUnconfirmed() {
            final String sessionId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();
            final CourtSchedule session = buildSessionWithId(LocalDate.of(2026, 9, 10), sessionId);
            session.setActive(true);
            final AllocatedListing priorReservation = new AllocatedListing();
            priorReservation.setHearingId(hearingId);
            priorReservation.setCourtScheduleId(UUID.randomUUID().toString());
            priorReservation.setExpiresAt(new Date());

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(List.of(priorReservation));
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(sessionId))).thenReturn(List.of(session));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final ReserveUnconfirmedHearingRequest request = new ReserveUnconfirmedHearingRequest()
                    .setHearingStartTime("2026-09-10T10:00:00+01:00")
                    .setSlotBased(true)
                    .setDuration(60);

            final ReserveUnconfirmedHearingResponse response =
                    service.reserveUnconfirmedHearing(sessionId, hearingId, request);

            assertEquals(sessionId, response.courtScheduleId());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }
    }
}