package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

    @InjectMocks
    private SlotsUpdateService service;

    @BeforeEach
    void setup() {
        setField(service, "courtScheduleRepository", courtScheduleRepository);
        setField(service, "provisionalBookingRepository", provisionalBookingRepository);
        setField(service, "allocatedListingRepository", allocatedListingRepository);
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
    void shouldSearchUpdateAllocatedSlots() {

        final String payload = fileToString("/test-data/courtscheduler.search.update.available.hearing.slots-police.json");
        final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();

        when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(true))).thenReturn(new Result("", true));

        service.searchUpdate(allocatedSlots);

        verify(courtScheduleRepository).saveBookedSlots(allocatedSlots, false, true);
    }

    @Test
    void shouldSearchUpdateAllocatedSlots_NonPolice() {

        final String payload = fileToString("/test-data/courtscheduler.search.update.available.hearing.slots-non-police.json");
        final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();
        when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));
        service.searchUpdate(allocatedSlots);

        verify(courtScheduleRepository).saveBookedSlots(allocatedSlots, false, false);
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
    class MultiDaySearchAndBook {

        @Test
        void shouldSearchAndBookConsecutiveWeekdaySessions() {
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();
            final int durationInMinutes = 1080; // 3 days

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = buildConsecutiveSessions(
                    LocalDate.of(2026, 3, 2), 3); // Mon, Tue, Wed

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 3)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, durationInMinutes, hearingId);

            assertEquals(3, result.size());
            assertEquals(LocalDate.of(2026, 3, 2), result.get(0).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 3), result.get(1).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 4), result.get(2).getSessionDate());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldReturnEmptyWhenDurationLessThanTwoDays() {
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(UUID.randomUUID().toString(), 360, UUID.randomUUID().toString());

            assertEquals(0, result.size());
            verify(courtScheduleRepository, org.mockito.Mockito.never()).findConsecutiveSessions(any(), org.mockito.Mockito.anyInt());
        }

        @Test
        void shouldReturnEmptyWhenNotEnoughCandidates() {
            final String courtScheduleId = UUID.randomUUID().toString();

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 3))
                    .thenReturn(buildConsecutiveSessions(LocalDate.of(2026, 3, 2), 2));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 1080, UUID.randomUUID().toString());

            assertEquals(0, result.size());
            verify(courtScheduleRepository, org.mockito.Mockito.never()).saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void shouldReturnEmptyWhenSessionsNotConsecutiveDays() {
            final String courtScheduleId = UUID.randomUUID().toString();

            // Mon, Wed (gap on Tuesday)
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 2)),
                    buildSession(LocalDate.of(2026, 3, 4)));

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, UUID.randomUUID().toString());

            assertEquals(0, result.size());
            verify(courtScheduleRepository, org.mockito.Mockito.never()).saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void shouldReturnEmptyWhenBookingFails() {
            final String courtScheduleId = UUID.randomUUID().toString();

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2))
                    .thenReturn(buildConsecutiveSessions(LocalDate.of(2026, 3, 2), 2));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("booking failed", false));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, UUID.randomUUID().toString());

            assertEquals(0, result.size());
        }

        @Test
        void shouldBookWhenFridayToMondayWeekendSkip() {
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            // Friday + Monday (consecutive business days - weekend is skipped)
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 6)),  // Friday
                    buildSession(LocalDate.of(2026, 3, 9))); // Monday

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, hearingId);

            assertEquals(2, result.size());
            assertEquals(LocalDate.of(2026, 3, 6), result.get(0).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 9), result.get(1).getSessionDate());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldReturnEmptyWhenFridayToTuesdayGap() {
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            // Friday + Tuesday (NOT consecutive business days - Monday is missing)
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 6)),   // Friday
                    buildSession(LocalDate.of(2026, 3, 10))); // Tuesday

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, hearingId);

            assertEquals(0, result.size());
            verify(courtScheduleRepository, org.mockito.Mockito.never()).saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void shouldBookWhenMultiDaySpansWeekend() {
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            // Thu + Fri + Mon (3-day hearing spanning a weekend)
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 5)),  // Thursday
                    buildSession(LocalDate.of(2026, 3, 6)),  // Friday
                    buildSession(LocalDate.of(2026, 3, 9))); // Monday

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 3)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 1080, hearingId);

            assertEquals(3, result.size());
            assertEquals(LocalDate.of(2026, 3, 5), result.get(0).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 6), result.get(1).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 9), result.get(2).getSessionDate());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldBookWhenConsecutiveWeekdaySessions() {
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            // Thu + Fri - consecutive calendar days
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 5)),  // Thursday
                    buildSession(LocalDate.of(2026, 3, 6))); // Friday

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, hearingId);

            assertEquals(2, result.size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldBookFiveDayHearingWithWeekendInMiddle() {
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            // Wed + Thu + Fri + Mon + Tue (5-day hearing, weekend in the middle)
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 4)),   // Wednesday
                    buildSession(LocalDate.of(2026, 3, 5)),   // Thursday
                    buildSession(LocalDate.of(2026, 3, 6)),   // Friday
                    buildSession(LocalDate.of(2026, 3, 9)),   // Monday
                    buildSession(LocalDate.of(2026, 3, 10))); // Tuesday

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 5)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 1800, hearingId);

            assertEquals(5, result.size());
            assertEquals(LocalDate.of(2026, 3, 4), result.get(0).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 5), result.get(1).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 6), result.get(2).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 9), result.get(3).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 10), result.get(4).getSessionDate());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldBookFiveDayHearingStartingFriday() {
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            // Fri + Mon + Tue + Wed + Thu (5-day hearing starting Friday, weekend after first day)
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 6)),   // Friday
                    buildSession(LocalDate.of(2026, 3, 9)),   // Monday
                    buildSession(LocalDate.of(2026, 3, 10)),  // Tuesday
                    buildSession(LocalDate.of(2026, 3, 11)),  // Wednesday
                    buildSession(LocalDate.of(2026, 3, 12))); // Thursday

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 5)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 1800, hearingId);

            assertEquals(5, result.size());
            assertEquals(LocalDate.of(2026, 3, 6), result.get(0).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 9), result.get(1).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 12), result.get(4).getSessionDate());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldReturnEmptyWhenFiveDayHearingHasMissingDayAfterWeekend() {
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            // Wed + Thu + Fri + Tue + Wed (Monday missing after weekend)
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 4)),   // Wednesday
                    buildSession(LocalDate.of(2026, 3, 5)),   // Thursday
                    buildSession(LocalDate.of(2026, 3, 6)),   // Friday
                    buildSession(LocalDate.of(2026, 3, 10)),  // Tuesday (Monday missing!)
                    buildSession(LocalDate.of(2026, 3, 11))); // Wednesday

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 5)).thenReturn(candidates);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 1800, hearingId);

            assertEquals(0, result.size());
            verify(courtScheduleRepository, org.mockito.Mockito.never()).saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        // --- Gap #14: anchor courtScheduleId is always present in the response ---

        @Test
        void shouldIncludeAnchorCourtScheduleIdInResponse() {
            // Contract: the caller passes a courtScheduleId as the anchor, and the response must
            // include it (as the first entry) alongside the subsequent consecutive-day sessions.
            final String anchorId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule anchor = buildSession(LocalDate.of(2026, 3, 2));
            anchor.setCourtScheduleId(anchorId);
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    anchor,
                    buildSession(LocalDate.of(2026, 3, 3)),
                    buildSession(LocalDate.of(2026, 3, 4)));

            when(courtScheduleRepository.findConsecutiveSessions(anchorId, 3)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(anchorId, 1080, hearingId);

            assertEquals(3, result.size());
            assertEquals(anchorId, result.get(0).getCourtScheduleId(),
                    "response must include the submitted anchor courtScheduleId as the first entry");
            assertTrue(result.stream().map(uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule::getCourtScheduleId)
                            .anyMatch(anchorId::equals),
                    "anchor courtScheduleId must be present in the returned list");
        }

        // --- Gap #9 / #10: duration threshold + negative/zero boundaries ---

        @Test
        void shouldReturnEmptyWhenDurationJustOverOneDayButUnderTwo() {
            // 361 mins → daysNeeded = 361/360 = 1, below the 2-day floor, so we short-circuit.
            // This locks in the current boundary: 361–719 mins all resolve to a single day
            // and never reach the repository (the caller should be using search-and-book, not multiday).
            final String courtScheduleId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 361, UUID.randomUUID().toString());

            assertEquals(0, result.size());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .findConsecutiveSessions(any(), org.mockito.Mockito.anyInt());
        }

        @Test
        void shouldReturnEmptyWhenDurationIsZero() {
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(UUID.randomUUID().toString(), 0, UUID.randomUUID().toString());

            assertEquals(0, result.size());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .findConsecutiveSessions(any(), org.mockito.Mockito.anyInt());
        }

        @Test
        void shouldReturnEmptyWhenDurationIsNegative() {
            // Java integer division: -720 / 360 = -2, which is < 2, so the guard still rejects it.
            // This test documents the defensive behaviour — the service does not throw.
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(UUID.randomUUID().toString(), -720, UUID.randomUUID().toString());

            assertEquals(0, result.size());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .findConsecutiveSessions(any(), org.mockito.Mockito.anyInt());
        }

        // --- Gap #17: very long hearing spanning multiple weekends ---

        @Test
        void shouldBookTenDayHearingAcrossTwoWeekends() {
            // 10 business days starting Monday 2026-03-02, spanning two full weekends.
            // 3600 mins / 360 per day = 10.
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 2)),   // Mon week 1
                    buildSession(LocalDate.of(2026, 3, 3)),
                    buildSession(LocalDate.of(2026, 3, 4)),
                    buildSession(LocalDate.of(2026, 3, 5)),
                    buildSession(LocalDate.of(2026, 3, 6)),   // Fri week 1
                    buildSession(LocalDate.of(2026, 3, 9)),   // Mon week 2 (weekend skipped)
                    buildSession(LocalDate.of(2026, 3, 10)),
                    buildSession(LocalDate.of(2026, 3, 11)),
                    buildSession(LocalDate.of(2026, 3, 12)),
                    buildSession(LocalDate.of(2026, 3, 13))); // Fri week 2

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 10)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 3600, hearingId);

            assertEquals(10, result.size());
            assertEquals(LocalDate.of(2026, 3, 2), result.get(0).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 13), result.get(9).getSessionDate());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        // --- Gap #18: anchor courtScheduleId not found by repository ---

        @Test
        void shouldReturnEmptyWhenAnchorCourtScheduleIdNotFoundByRepository() {
            // When the anchor does not exist (or is inactive), findConsecutiveSessions returns
            // an empty list. The service must treat this as "not enough candidates" and not book.
            final String unknownAnchorId = UUID.randomUUID().toString();

            when(courtScheduleRepository.findConsecutiveSessions(unknownAnchorId, 3))
                    .thenReturn(Collections.emptyList());

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(unknownAnchorId, 1080, UUID.randomUUID().toString());

            assertEquals(0, result.size());
            verify(courtScheduleRepository, org.mockito.Mockito.never())
                    .saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        // --- DST edge: UK BST spring-forward happens on a Sunday and must not break Fri→Mon logic ---

        @Test
        void shouldBookAcrossUkDstSpringForwardWeekend() {
            // UK BST begins on Sunday 2026-03-29. Test Fri 2026-03-27 → Mon 2026-03-30.
            // Because the logic uses LocalDate (not ZonedDateTime), DST is a non-event — this test
            // locks that invariant in place so a future switch to a zoned representation can't silently break it.
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 27)),  // Friday (BST starts Sun)
                    buildSession(LocalDate.of(2026, 3, 30))); // Monday (in BST)

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, hearingId);

            assertEquals(2, result.size());
            assertEquals(LocalDate.of(2026, 3, 27), result.get(0).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 30), result.get(1).getSessionDate());
        }
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
    class AllSessionsHaveSufficientAvailability {

        @Test
        void shouldReturnTrueWhenAllSessionsHave360Available() {
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 2), 360, 0, false),
                    buildSession(LocalDate.of(2026, 3, 3), 360, 0, false));
            assertTrue(SlotsUpdateService.allSessionsHaveSufficientAvailability(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnFalseWhenOneSessionHasInsufficientAvailability() {
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 2), 360, 0, false),
                    buildSession(LocalDate.of(2026, 3, 3), 360, 200, false)); // 160 available
            Assertions.assertFalse(SlotsUpdateService.allSessionsHaveSufficientAvailability(sessions, "test-hearing"));
        }

        @Test
        void shouldBypassCheckWhenOverbookingAllowed() {
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 2), 360, 360, true), // 0 available but overbooking allowed
                    buildSession(LocalDate.of(2026, 3, 3), 360, 0, false));
            assertTrue(SlotsUpdateService.allSessionsHaveSufficientAvailability(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueWhenAllOverbookingAllowed() {
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions = List.of(
                    buildSession(LocalDate.of(2026, 3, 2), 100, 100, true),
                    buildSession(LocalDate.of(2026, 3, 3), 50, 50, true));
            assertTrue(SlotsUpdateService.allSessionsHaveSufficientAvailability(sessions, "test-hearing"));
        }

        @Test
        void shouldReturnTrueForEmptyList() {
            assertTrue(SlotsUpdateService.allSessionsHaveSufficientAvailability(List.of(), "test-hearing"));
        }
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

    @Nested
    class MultiDaySearchAndBookAvailability {

        @Test
        void shouldBookWhenOneSessionHasInsufficientAvailability() {
            // F1 (court-calendar always-assign rule): a capacity shortfall no longer rejects the
            // block — the short day is overbooked (advisorily logged) and the booking proceeds.
            final String courtScheduleId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 2), 360, 0, false),
                    buildSession(LocalDate.of(2026, 3, 3), 360, 200, false)); // only 160 available

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, UUID.randomUUID().toString());

            assertEquals(2, result.size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldProceedWhenInsufficientSessionHasOverbookingAllowed() {
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 2), 360, 360, true), // 0 available, overbooking allowed
                    buildSession(LocalDate.of(2026, 3, 3), 360, 0, false));

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, hearingId);

            assertEquals(2, result.size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        // --- Gap #12: mixed overbooking policies across days ---

        @Test
        void shouldBookMultiDayWithMixedOverbookingAcrossDays() {
            // Day 1: fully booked but overbooking allowed → passes
            // Day 2: 360 available, overbooking not allowed → passes
            // Day 3: fully booked but overbooking allowed → passes
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 2), 360, 360, true),  // overbook
                    buildSession(LocalDate.of(2026, 3, 3), 360, 0, false),   // strict, sufficient
                    buildSession(LocalDate.of(2026, 3, 4), 360, 360, true)); // overbook

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 3)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 1080, hearingId);

            assertEquals(3, result.size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldBookWhenOverbookingAllowedDayFollowedByStrictInsufficientDay() {
            // F1: the strict short day no longer poisons the batch — both days book, the short one
            // is overbooked with an advisory log.
            final String courtScheduleId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 2), 360, 360, true),   // overbook
                    buildSession(LocalDate.of(2026, 3, 3), 360, 200, false)); // 160 available, strict

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, UUID.randomUUID().toString());

            assertEquals(2, result.size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        // --- Gap #13: allDaySplit sessions (AM + PM split) in multiday ---

        @Test
        void shouldBookMultiDayWithAllDaySplitSessionsHavingSufficientDuration() {
            // Each day has AM 180 max + PM 180 max = 360 total, all unbooked → sufficient for 360/day.
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildAllDaySplitSession(LocalDate.of(2026, 3, 2), 180, 180, 0, 0),
                    buildAllDaySplitSession(LocalDate.of(2026, 3, 3), 180, 180, 0, 0));

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, hearingId);

            assertEquals(2, result.size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldBookWhenAllDaySplitSessionHasInsufficientCombinedDuration() {
            // AM 180 max - 60 booked = 120, PM 180 max - 50 booked = 130, combined = 250 < 360.
            // F1: the shortfall is advisory — the split day is overbooked and the booking proceeds.
            final String courtScheduleId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildAllDaySplitSession(LocalDate.of(2026, 3, 2), 180, 180, 0, 0),     // 360 available
                    buildAllDaySplitSession(LocalDate.of(2026, 3, 3), 180, 180, 60, 50));  // 250 available → short

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, UUID.randomUUID().toString());

            assertEquals(2, result.size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        // --- Gap #15: draft sessions returned by the repository ---

        @Test
        void shouldBookDraftSessionsWhenRepositoryReturnsThem() {
            // findConsecutiveSessions SQL filters by active=true, oucode, court_room_id and business_type,
            // but does NOT filter on is_draft. This test documents the current behaviour: draft filtering
            // is the repository/SQL's responsibility, and the service will book whatever it is given.
            // If product requires drafts to be excluded, the fix belongs in the repository SQL, not here.
            final String courtScheduleId = UUID.randomUUID().toString();
            final String hearingId = UUID.randomUUID().toString();

            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule draftDay1 = buildSession(LocalDate.of(2026, 3, 2));
            draftDay1.setIsDraft(true);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule draftDay2 = buildSession(LocalDate.of(2026, 3, 3));
            draftDay2.setIsDraft(true);

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2))
                    .thenReturn(List.of(draftDay1, draftDay2));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, hearingId);

            assertEquals(2, result.size());
            assertTrue(result.get(0).isDraft(), "draft flag is preserved on returned sessions");
            assertTrue(result.get(1).isDraft());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        // --- Gap #4: pre-existing allocations on a later day reduce availability ---

        @Test
        void shouldBookWhenLaterDayHasBookingsThatExhaustAvailability() {
            // Day 1 free, Day 2 already has 200 booked (of 360) by other hearings.
            // F1: other hearings' bookings no longer block — day 2 is overbooked, booking proceeds.
            final String courtScheduleId = UUID.randomUUID().toString();

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> candidates = List.of(
                    buildSession(LocalDate.of(2026, 3, 2), 360, 0, false),
                    buildSession(LocalDate.of(2026, 3, 3), 360, 200, false));

            when(courtScheduleRepository.findConsecutiveSessions(courtScheduleId, 2)).thenReturn(candidates);
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(courtScheduleId, 720, UUID.randomUUID().toString());

            assertEquals(2, result.size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        // --- Single→multi-day conversion: the hearing's OWN booking must not block the anchor day ---

        @Test
        void shouldReclaimOwnCapacityWhenAnchorDayIsFullyBookedByThisHearing() {
            // The anchor is the hearing's own currently-booked single-day session, so its 360
            // minutes show as consumed until saveBookedSlots' internal release runs. The
            // availability check must net the hearing's own contribution out (reclaim) — otherwise
            // converting a booked single-day hearing to multi-day can never book.
            final String hearingId = UUID.randomUUID().toString();
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule anchor =
                    buildSession(LocalDate.of(2026, 3, 2), 360, 360, false); // fully consumed by own booking
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule day2 =
                    buildSession(LocalDate.of(2026, 3, 3), 360, 0, false);

            final AllocatedListing ownBooking = new AllocatedListing();
            ownBooking.setHearingId(hearingId);
            ownBooking.setCourtScheduleId(anchor.getCourtScheduleId());
            ownBooking.setDuration(360);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(List.of(ownBooking));
            when(courtScheduleRepository.findConsecutiveSessions(anchor.getCourtScheduleId(), 2))
                    .thenReturn(List.of(anchor, day2));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(anchor.getCourtScheduleId(), 720, hearingId);

            assertEquals(2, result.size());
            assertEquals(LocalDate.of(2026, 3, 2), result.get(0).getSessionDate());
            assertEquals(LocalDate.of(2026, 3, 3), result.get(1).getSessionDate());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldBookWhenAnchorDayIsFullyBookedByAnotherHearing() {
            // The anchor day's minutes belong to a DIFFERENT hearing — nothing to reclaim.
            // F1: the day is overbooked (advisorily logged) and the booking proceeds anyway.
            final String hearingId = UUID.randomUUID().toString();
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule anchor =
                    buildSession(LocalDate.of(2026, 3, 2), 360, 360, false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule day2 =
                    buildSession(LocalDate.of(2026, 3, 3), 360, 0, false);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(List.of());
            when(courtScheduleRepository.findConsecutiveSessions(anchor.getCourtScheduleId(), 2))
                    .thenReturn(List.of(anchor, day2));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(anchor.getCourtScheduleId(), 720, hearingId);

            assertEquals(2, result.size());
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldOnlyReclaimOwnPortionWhenAnchorDayIsSharedWithAnotherHearing() {
            // The anchor day is fully consumed (360) but this hearing holds only 120 of it; reclaim
            // must never free the other hearing's 240. F1 means the residual shortfall no longer
            // rejects the booking — but the reclaim math still only nets out the hearing's OWN minutes
            // (anchor.totalBooked drops to 240, not 0).
            final String hearingId = UUID.randomUUID().toString();
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule anchor =
                    buildSession(LocalDate.of(2026, 3, 2), 360, 360, false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule day2 =
                    buildSession(LocalDate.of(2026, 3, 3), 360, 0, false);

            final AllocatedListing ownPartialBooking = new AllocatedListing();
            ownPartialBooking.setHearingId(hearingId);
            ownPartialBooking.setCourtScheduleId(anchor.getCourtScheduleId());
            ownPartialBooking.setDuration(120);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(List.of(ownPartialBooking));
            when(courtScheduleRepository.findConsecutiveSessions(anchor.getCourtScheduleId(), 2))
                    .thenReturn(List.of(anchor, day2));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                    service.multiDaySearchAndBook(anchor.getCourtScheduleId(), 720, hearingId);

            assertEquals(2, result.size());
            assertEquals(240, anchor.getTotalBooked(), "reclaim nets out only the hearing's own 120 minutes");
            verify(courtScheduleRepository).saveBookedSlots(any(), eq(false), eq(false));
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
    class DedupeByDatePreferringNonOverbooking {

        @Test
        void shouldReturnEmptyWhenInputIsEmpty() {
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> out =
                    SlotsUpdateService.dedupeByDatePreferringNonOverbooking(Collections.emptyList());

            assertTrue(out.isEmpty());
        }

        @Test
        void shouldReturnSingleSessionUnchanged() {
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs =
                    buildSession(LocalDate.of(2026, 5, 7), /*overbook*/ false);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> out =
                    SlotsUpdateService.dedupeByDatePreferringNonOverbooking(List.of(cs));

            assertEquals(1, out.size());
            assertEquals(cs.getCourtScheduleId(), out.get(0).getCourtScheduleId());
        }

        @Test
        void shouldPreferNonOverbookingWhenDuplicateDateHasOne() {
            // Two rows share 2026-05-07; one is overbookingAllowed, the other is not — dedupe should
            // keep the non-overbooking row. Matches SlotsSearchService.preferNonOverbooking so the
            // slot-search and multiday-search-and-book paths pick the same representative per date.
            final LocalDate date = LocalDate.of(2026, 5, 7);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule overbook = buildSession(date, true);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule nonOverbook = buildSession(date, false);

            // First the overbooking one, then the non-overbooking one.
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> out =
                    SlotsUpdateService.dedupeByDatePreferringNonOverbooking(List.of(overbook, nonOverbook));

            assertEquals(1, out.size());
            assertEquals(nonOverbook.getCourtScheduleId(), out.get(0).getCourtScheduleId(),
                    "dedupe should keep the non-overbooking row when one exists on the same date");
        }

        @Test
        void shouldKeepNonOverbookingRowEvenWhenItArrivesFirst() {
            // Swap ordering from the previous test — preference should still pick the non-overbooking row.
            final LocalDate date = LocalDate.of(2026, 5, 7);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule nonOverbook = buildSession(date, false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule overbook = buildSession(date, true);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> out =
                    SlotsUpdateService.dedupeByDatePreferringNonOverbooking(List.of(nonOverbook, overbook));

            assertEquals(1, out.size());
            assertEquals(nonOverbook.getCourtScheduleId(), out.get(0).getCourtScheduleId());
        }

        @Test
        void shouldReturnRowsSortedByDate() {
            // Input is not sorted; output must be sorted ascending by session_date so the downstream
            // areConsecutiveBusinessDays check sees strictly increasing dates.
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule tue =
                    buildSession(LocalDate.of(2026, 5, 5), false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule thu =
                    buildSession(LocalDate.of(2026, 5, 7), false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule wed =
                    buildSession(LocalDate.of(2026, 5, 6), false);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> out =
                    SlotsUpdateService.dedupeByDatePreferringNonOverbooking(List.of(thu, tue, wed));

            assertEquals(3, out.size());
            assertEquals(LocalDate.of(2026, 5, 5), out.get(0).getSessionDate());
            assertEquals(LocalDate.of(2026, 5, 6), out.get(1).getSessionDate());
            assertEquals(LocalDate.of(2026, 5, 7), out.get(2).getSessionDate());
        }

        @Test
        void shouldDedupeEachDateIndependently() {
            // Realistic multi-day shape: three dates, each with a duplicate (overbook + non-overbook).
            // Output should be 3 non-overbook rows, one per date, sorted.
            final LocalDate d1 = LocalDate.of(2026, 5, 5);
            final LocalDate d2 = LocalDate.of(2026, 5, 6);
            final LocalDate d3 = LocalDate.of(2026, 5, 7);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule d1over = buildSession(d1, true);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule d1non = buildSession(d1, false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule d2over = buildSession(d2, true);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule d2non = buildSession(d2, false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule d3over = buildSession(d3, true);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule d3non = buildSession(d3, false);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> out =
                    SlotsUpdateService.dedupeByDatePreferringNonOverbooking(
                            List.of(d1over, d1non, d2over, d2non, d3over, d3non));

            assertEquals(3, out.size());
            assertEquals(d1non.getCourtScheduleId(), out.get(0).getCourtScheduleId());
            assertEquals(d2non.getCourtScheduleId(), out.get(1).getCourtScheduleId());
            assertEquals(d3non.getCourtScheduleId(), out.get(2).getCourtScheduleId());
        }

        @Test
        void shouldKeepAllThreeDatesWhenNoDuplicates() {
            // No duplicates — all three rows pass through, sorted by date.
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule d1 =
                    buildSession(LocalDate.of(2026, 5, 5), true);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule d2 =
                    buildSession(LocalDate.of(2026, 5, 6), true);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule d3 =
                    buildSession(LocalDate.of(2026, 5, 7), true);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> out =
                    SlotsUpdateService.dedupeByDatePreferringNonOverbooking(List.of(d1, d2, d3));

            assertEquals(3, out.size());
        }

        @Test
        void shouldSkipRowsWithNullSessionDate() {
            // Defensive: a row with a null session_date can't be placed on a calendar so dedupe drops it.
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule withDate =
                    buildSession(LocalDate.of(2026, 5, 5), false);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule withoutDate = buildSession(null, false);

            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> out =
                    SlotsUpdateService.dedupeByDatePreferringNonOverbooking(List.of(withDate, withoutDate));

            assertEquals(1, out.size());
            assertEquals(withDate.getCourtScheduleId(), out.get(0).getCourtScheduleId());
        }

        private uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule buildSession(
                final LocalDate date, final boolean overbookingAllowed) {
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs =
                    new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
            cs.setCourtScheduleId(UUID.randomUUID().toString());
            cs.setSessionDate(date);
            cs.setOuCode("C01CY00");
            cs.setCourtRoomId("731816c1-5ee4-373a-9bda-840e13a5bcb0");
            cs.setBusinessType("GENC");
            cs.setIsOverbookingAllowed(overbookingAllowed);
            cs.setMaxDuration(360);
            return cs;
        }
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

            final CrownFallbackResponse response = service.crownFallbackSearchAndBook(request);

            assertEquals(hearingId, response.hearingId());
            assertEquals(existing.getCourtScheduleId(), response.courtScheduleId());
            assertEquals(existing.getCourtRoomId(), response.courtRoomId());
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
            when(courtScheduleRepository.createCrownFallbackSession(
                    eq(request.getCourtCentreId()), eq(request.getHearingDate()),
                    eq(request.getDurationInMinutes()), eq(request.getCourtRoomId()), eq(request.getEarliestHearingTime())))
                    .thenReturn(Optional.of(new CrownFallbackSearchResult(created, false)));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownFallbackResponse response = service.crownFallbackSearchAndBook(request);

            assertEquals(createdScheduleId, response.courtScheduleId());
            assertEquals(false, response.isDraft());
            assertEquals(false, response.overbooked());
            verify(courtScheduleRepository).createCrownFallbackSession(
                    eq(request.getCourtCentreId()), eq(request.getHearingDate()),
                    eq(request.getDurationInMinutes()), eq(request.getCourtRoomId()), eq(request.getEarliestHearingTime()));
        }

        @Test
        void shouldThrowNoSessionExceptionWhenSearchEmptyAndAutoCreateHasNoTemplateSession() {
            // Auto-creation needs an existing session at the centre to copy metadata from; a centre
            // that has never been seeded still surfaces the no-session error.
            final String hearingId = UUID.randomUUID().toString();
            final CrownFallbackRequest request = validRequest(hearingId);

            when(courtScheduleRepository.findAllocatedListingByHearingId(hearingId)).thenReturn(Optional.empty());
            when(courtScheduleRepository.searchCrownFallbackSlots(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.empty());
            when(courtScheduleRepository.createCrownFallbackSession(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                    .thenReturn(Optional.empty());

            Assertions.assertThrows(CrownFallbackNoSessionException.class,
                    () -> service.crownFallbackSearchAndBook(request));
            verify(courtScheduleRepository, org.mockito.Mockito.never()).saveBookedSlots(any(), anyBoolean(), anyBoolean());
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
        void shouldPropagateSourceLabelToAllocatedSlot() {
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

            service.crownFallbackSearchAndBook(request);

            final List<AllocatedSlot> persisted = slotCaptor.getValue();
            assertEquals(1, persisted.size());
            assertEquals("CROWN_FB_ADJOURN", persisted.get(0).getSource());
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
            assertEquals(731816, response.courtRoomId());
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

        // ─── Single→multi-day conversion (update-hearing-for-listing anchored on the hearing's
        // own booked session). The idempotency guard is anchor-strict AND block-size-strict:
        // same anchor + same day count = retry; same anchor + DIFFERENT day count = resize,
        // which must go through the move path (release + reclaim + re-book), not be swallowed
        // as a replay that returns the old block with the extra day(s) never booked. ───

        @Test
        void should_extendSingleDayHearingToMultiDay_when_anchoredOnItsOwnBookedSession() {
            // The reported bug: hearing holds ONE allocated_listings row (its single-day booking
            // on 2026-07-20); update-hearing-for-listing requests 720 mins (2 days) anchored on
            // that SAME session. Anchor matches the block's first (only) day but the day count
            // differs (1 existing vs 2 requested) → RESIZE: release the old row and book both days.
            final String hearingId = UUID.randomUUID().toString();
            final LocalDate day1 = LocalDate.of(2026, 7, 20); // Monday
            final LocalDate day2 = LocalDate.of(2026, 7, 21); // Tuesday
            final String anchorId = "cs-own-day1";

            final List<AllocatedListing> existingAllocations = List.of(
                    existingAllocationWithDuration(hearingId, anchorId, 360));
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> existingSessions = List.of(
                    buildSessionWithId(day1, anchorId));

            // Re-search: the anchor day still shows fully consumed by the hearing's own
            // not-yet-released booking; day 2 is free. reclaimHearingsOwnCapacity nets day 1 out.
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule ownFullyBookedDay1 =
                    buildSession(day1, 360, 360, false);
            ownFullyBookedDay1.setCourtScheduleId(anchorId);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule freshDay2 =
                    buildSessionWithId(day2, "cs-new-day2");

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(day1)
                    .setCourtScheduleId(anchorId)
                    .setDurationInMinutes(720);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(anchorId)))
                    .thenReturn(existingSessions);
            when(courtScheduleRepository.findConsecutiveSessions(anchorId, 2))
                    .thenReturn(List.of(ownFullyBookedDay1, freshDay2));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository).releaseOldAllocatedListings(hearingId);
            @SuppressWarnings("unchecked")
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotsCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).saveBookedSlots(slotsCaptor.capture(), eq(false), eq(false));
            assertEquals(2, slotsCaptor.getValue().size());
            assertTrue(slotsCaptor.getValue().stream().allMatch(s -> hearingId.equals(s.getHearingId())));
            // 720 total split across the 2 booked days
            assertTrue(slotsCaptor.getValue().stream().allMatch(s -> s.getDuration() == 360));
            assertEquals(2, response.sessions().size());
            assertEquals(day1, response.sessions().get(0).getSessionDate());
            assertEquals(day2, response.sessions().get(1).getSessionDate());
        }

        @Test
        void should_shrinkBlock_when_requestedFewerDaysThanExistingBlock() {
            // Resize in the other direction: a 3-day block re-requested for 720 mins (2 days),
            // anchored on its first day. Not an idempotent replay — the move path books exactly
            // the 2 requested days and releases the third.
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

            // Re-search finds the hearing's own day1/day2 still showing fully consumed — reclaimed.
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule ownDay1 = buildSession(day1, 360, 360, false);
            ownDay1.setCourtScheduleId(csDay1);
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule ownDay2 = buildSession(day2, 360, 360, false);
            ownDay2.setCourtScheduleId(csDay2);

            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(hearingId)
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(day1)
                    .setCourtScheduleId(csDay1)
                    .setDurationInMinutes(720);

            when(allocatedListingRepository.findByHearingId(hearingId)).thenReturn(existingAllocations);
            when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(csDay1, csDay2, csDay3)))
                    .thenReturn(existingSessions);
            when(courtScheduleRepository.findConsecutiveSessions(csDay1, 2))
                    .thenReturn(List.of(ownDay1, ownDay2));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(new Result("", true));

            final CrownSearchAndBookResponse response = service.crownSearchAndBook(request);

            verify(courtScheduleRepository).releaseOldAllocatedListings(hearingId);
            @SuppressWarnings("unchecked")
            final org.mockito.ArgumentCaptor<List<AllocatedSlot>> slotsCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).saveBookedSlots(slotsCaptor.capture(), eq(false), eq(false));
            assertEquals(2, slotsCaptor.getValue().size());
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
}