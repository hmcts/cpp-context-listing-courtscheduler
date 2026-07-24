package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.Hearing;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateResponse;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingInfo;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.exception.MoveHearingToPastDateNoSessionException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.time.LocalDate;
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
import org.mockito.ArgumentCaptor;
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
    private Logger logger;

    @InjectMocks
    private SlotsUpdateService service;

    @BeforeEach
    void setup() {
        setField(service, "courtScheduleRepository", courtScheduleRepository);
        setField(service, "provisionalBookingRepository", provisionalBookingRepository);
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
    class MoveHearingToPastDate {

        private static final String MAGISTRATES = "MAGISTRATES";
        private static final String CROWN = "CROWN";

        @Test
        void shouldBookSessionAndStampSource_whenSessionFoundForExactDate() {
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final LocalDate startDate = LocalDate.of(2026, 5, 1);
            final CourtSchedule session = buildSession(startDate);

            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(startDate), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.of(session));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(Result.SUCCESS());

            final List<MoveHearingToPastDateResponse> responses =
                    service.moveHearingToPastDate(hearingId, courtCentreId, null, startDate, startDate, "09:00", "12:00", MAGISTRATES, 30);

            assertEquals(1, responses.size());
            final MoveHearingToPastDateResponse response = responses.get(0);
            assertEquals(hearingId, response.hearingId());
            assertEquals(session.getCourtScheduleId(), response.courtScheduleId());
            assertEquals(session.getCourtRoomId(), response.courtRoomId());
            assertEquals("2026-05-01", response.sessionDate());
            assertEquals(30, response.durationInMinutes());
            assertEquals("MOVE_TO_PAST_DATE", response.source());
            assertFalse(response.overbooked());

            final ArgumentCaptor<List<AllocatedSlot>> captor = ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).saveBookedSlots(captor.capture(), eq(false), eq(false));
            final AllocatedSlot bookedSlot = captor.getValue().get(0);
            assertEquals(hearingId, bookedSlot.getHearingId());
            assertEquals(session.getCourtScheduleId(), bookedSlot.getCourtScheduleId());
            assertEquals("MOVE_TO_PAST_DATE", bookedSlot.getSource());
            assertEquals(30, bookedSlot.getDuration());
        }

        @Test
        void shouldStampSubmittedStartAndEndTimeOnBookedSlot_notTheSessionWindow() {
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final LocalDate startDate = LocalDate.of(2026, 5, 1);
            // buildSession's own window is 09:00-17:00; the booked slot must reflect the SUBMITTED
            // 10:30-12:45 time-of-day instead (Round-1 principle: caller's times, not the session window).
            final CourtSchedule session = buildSession(startDate);

            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(startDate), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.of(session));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(Result.SUCCESS());

            final MoveHearingToPastDateResponse response = service.moveHearingToPastDate(
                    hearingId, courtCentreId, null, startDate, startDate, "10:30", "12:45", MAGISTRATES, 20).get(0);

            // per-day start/end are the SUBMITTED time-of-day on the sitting day, NOT the session's 09:00/17:00
            assertEquals("2026-05-01T10:30:00.000Z", response.sessionStartTime());
            assertEquals("2026-05-01T12:45:00.000Z", response.sessionEndTime());
            // duration is the value computed upstream (in HearingSlotsApi) and passed through unchanged
            assertEquals(20, response.durationInMinutes());

            final ArgumentCaptor<List<AllocatedSlot>> captor = ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository).saveBookedSlots(captor.capture(), eq(false), eq(false));
            // the persisted allocation also carries the submitted start time (not the session window)
            assertEquals("2026-05-01T10:30:00.000Z", captor.getValue().get(0).getHearingStartTime());
        }

        @Test
        void shouldBookOnlyWorkingDaysAcrossAMultiDayRangeInOneAtomicCall() {
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final LocalDate friday = LocalDate.of(2026, 7, 3);
            final LocalDate monday = LocalDate.of(2026, 7, 6);

            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(friday), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.of(buildSession(friday)));
            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(monday), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.of(buildSession(monday)));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(Result.SUCCESS());

            final List<MoveHearingToPastDateResponse> responses =
                    service.moveHearingToPastDate(hearingId, courtCentreId, null, friday, monday, "09:00", "12:00", MAGISTRATES, 30);

            // Fri + Mon only (Sat/Sun skipped)
            assertEquals(2, responses.size());
            assertEquals("2026-07-03", responses.get(0).sessionDate());
            assertEquals("2026-07-06", responses.get(1).sessionDate());
            // never queries the weekend days
            verify(courtScheduleRepository, never()).findSessionForMoveToPastDate(any(), any(), eq(LocalDate.of(2026, 7, 4)), any(), any());
            // single atomic booking call with both slots
            final ArgumentCaptor<List<AllocatedSlot>> captor = ArgumentCaptor.forClass(List.class);
            verify(courtScheduleRepository, org.mockito.Mockito.times(1)).saveBookedSlots(captor.capture(), eq(false), eq(false));
            assertEquals(2, captor.getValue().size());
        }

        @Test
        void shouldSupportCrownJurisdiction() {
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final LocalDate startDate = LocalDate.of(2026, 5, 1);

            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(startDate), any(), eq(CROWN)))
                    .thenReturn(Optional.of(buildSession(startDate)));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(Result.SUCCESS());

            final List<MoveHearingToPastDateResponse> responses =
                    service.moveHearingToPastDate(hearingId, courtCentreId, null, startDate, startDate, "09:00", "12:00", CROWN, 30);

            assertEquals(1, responses.size());
        }

        @Test
        void shouldScopeSearchToTheRequestedRoom() {
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final String courtRoomId = UUID.randomUUID().toString();
            final LocalDate startDate = LocalDate.of(2026, 5, 1);

            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), eq(courtRoomId), eq(startDate), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.of(buildSession(startDate)));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(Result.SUCCESS());

            service.moveHearingToPastDate(hearingId, courtCentreId, courtRoomId, startDate, startDate, "09:00", "12:00", MAGISTRATES, 30);

            verify(courtScheduleRepository).findSessionForMoveToPastDate(eq(courtCentreId), eq(courtRoomId), eq(startDate), any(), eq(MAGISTRATES));
        }

        @Test
        void shouldReleasePriorAllocation_onReMove() {
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final LocalDate startDate = LocalDate.of(2026, 5, 1);
            final CourtSchedule session = buildSession(startDate);

            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(startDate), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.of(session));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(Result.SUCCESS());

            service.moveHearingToPastDate(hearingId, courtCentreId, null, startDate, startDate, "09:00", "12:00", MAGISTRATES, 30);
            service.moveHearingToPastDate(hearingId, courtCentreId, null, startDate, startDate, "09:00", "12:00", MAGISTRATES, 30);

            verify(courtScheduleRepository, org.mockito.Mockito.times(2))
                    .saveBookedSlots(any(), eq(false), eq(false));
        }

        @Test
        void shouldThrowNoSessionException_whenNoSessionFoundForExactDate() {
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final LocalDate startDate = LocalDate.of(2026, 5, 1);

            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(startDate), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.empty());

            assertThrows(MoveHearingToPastDateNoSessionException.class,
                    () -> service.moveHearingToPastDate(hearingId, courtCentreId, null, startDate, startDate, "09:00", "12:00", MAGISTRATES, 30));

            verify(courtScheduleRepository, never()).saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void shouldBookNoDayWhenAnyDayInRangeHasNoSession() {
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final LocalDate day1 = LocalDate.of(2026, 7, 1);
            final LocalDate day2 = LocalDate.of(2026, 7, 2);

            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(day1), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.of(buildSession(day1)));
            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(day2), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.empty());

            assertThrows(MoveHearingToPastDateNoSessionException.class,
                    () -> service.moveHearingToPastDate(hearingId, courtCentreId, null, day1, day2, "09:00", "12:00", MAGISTRATES, 30));

            // atomic - all sessions resolved before any booking, so nothing is booked when one day misses
            verify(courtScheduleRepository, never()).saveBookedSlots(any(), anyBoolean(), anyBoolean());
        }

        @Test
        void shouldThrowNoSessionException_whenPersistFails() {
            final String hearingId = UUID.randomUUID().toString();
            final String courtCentreId = UUID.randomUUID().toString();
            final LocalDate startDate = LocalDate.of(2026, 5, 1);
            final CourtSchedule session = buildSession(startDate);

            when(courtScheduleRepository.findSessionForMoveToPastDate(eq(courtCentreId), any(), eq(startDate), any(), eq(MAGISTRATES)))
                    .thenReturn(Optional.of(session));
            when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false)))
                    .thenReturn(Result.FAILED("could not persist"));

            assertThrows(MoveHearingToPastDateNoSessionException.class,
                    () -> service.moveHearingToPastDate(hearingId, courtCentreId, null, startDate, startDate, "09:00", "12:00", MAGISTRATES, 30));
        }

        private CourtSchedule buildSession(final LocalDate startDate) {
            final CourtSchedule session = new CourtSchedule();
            session.setCourtScheduleId(UUID.randomUUID().toString());
            session.setOuCode("B01LY00");
            session.setCourtRoomId(UUID.randomUUID().toString());
            session.setCourtRoomNumber(1);
            session.setSessionDate(startDate);
            session.setSessionStartTime(Date.from(startDate.atTime(9, 0).atZone(java.time.ZoneOffset.UTC).toInstant()));
            session.setSessionEndTime(Date.from(startDate.atTime(17, 0).atZone(java.time.ZoneOffset.UTC).toInstant()));
            session.setIsDraft(false);
            session.setBusinessType("NGAP");
            session.setSlotBased(false);
            session.setAvailableDuration(240);
            return session;
        }
    }
}