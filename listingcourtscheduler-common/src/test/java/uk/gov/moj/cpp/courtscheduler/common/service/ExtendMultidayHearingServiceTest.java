package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtendMultidayHearingServiceTest {

    private static final String HEARING_ID = "11111111-1111-1111-1111-111111111111";
    private static final String OU_CODE = "B01LY00";
    private static final Integer COURT_ROOM_ID = 1;
    private static final String BUSINESS_TYPE = "TRFL";

    @InjectMocks
    private ExtendMultidayHearingService service;

    @Mock
    private AllocatedListingRepository allocatedListingRepository;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    private List<AllocatedListing> existingAllocations;

    @BeforeEach
    void setUp() {
        existingAllocations = List.of(
                allocation(LocalDate.of(2026, 3, 2)),
                allocation(LocalDate.of(2026, 3, 3)),
                allocation(LocalDate.of(2026, 3, 4))
        );
    }

    @Test
    void invalidDateRange_when_endBeforeStart() {
        final ExtendMultidayHearingException e = assertThrows(ExtendMultidayHearingException.class,
                () -> service.extend(HEARING_ID, LocalDate.of(2026, 3, 4), LocalDate.of(2026, 3, 2), 1080));

        assertEquals(ExtendMultidayHearingException.ErrorCode.INVALID_DATE_RANGE, e.getErrorCode());
        verify(allocatedListingRepository, never()).findByHearingIdOrderBySessionDateAsc(anyString());
    }

    @Test
    void noExistingAllocation_when_repositoryEmpty() {
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID))
                .thenReturn(Collections.emptyList());

        final ExtendMultidayHearingException e = assertThrows(ExtendMultidayHearingException.class,
                () -> service.extend(HEARING_ID, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 5), 1440));

        assertEquals(ExtendMultidayHearingException.ErrorCode.NO_EXISTING_ALLOCATION, e.getErrorCode());
    }

    @Test
    void startDateChangeNotAllowed_when_newStartNotMin() {
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID)).thenReturn(existingAllocations);
        when(courtScheduleRepository.getCourtSchedulesByIdList(any())).thenReturn(buildHydratedSchedules(existingAllocations));

        final ExtendMultidayHearingException e = assertThrows(ExtendMultidayHearingException.class,
                () -> service.extend(HEARING_ID, LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 5), 1440));

        assertEquals(ExtendMultidayHearingException.ErrorCode.START_DATE_CHANGE_NOT_ALLOWED, e.getErrorCode());
    }

    @Test
    void noChange_when_newEndEqualsMax() {
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID)).thenReturn(existingAllocations);
        when(courtScheduleRepository.getCourtSchedulesByIdList(any())).thenReturn(buildHydratedSchedules(existingAllocations));

        final List<CourtSchedule> result = service.extend(HEARING_ID,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4), 1080);

        assertEquals(3, result.size());
        verify(allocatedListingRepository, never()).deleteByHearingIdAndSessionDateGreaterThan(anyString(), any());
        verify(allocatedListingRepository, never()).save(any());
    }

    @Test
    void shrink_when_newEndBeforeMax() {
        final List<AllocatedListing> postDelete = existingAllocations.subList(0, 2);
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID))
                .thenReturn(existingAllocations)
                .thenReturn(postDelete);
        when(courtScheduleRepository.getCourtSchedulesByIdList(any()))
                .thenReturn(buildHydratedSchedules(existingAllocations))
                .thenReturn(buildHydratedSchedules(postDelete));

        final List<CourtSchedule> result = service.extend(HEARING_ID,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3), 720);

        verify(allocatedListingRepository).deleteByHearingIdAndSessionDateGreaterThan(HEARING_ID, LocalDate.of(2026, 3, 3));
        verify(allocatedListingRepository, never()).save(any());
        assertEquals(2, result.size());
    }

    @Test
    void extendSuccess_when_tailDaysAvailable_skipsWeekends() {
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID))
                .thenReturn(existingAllocations);

        final CourtSchedule day5 = adSession(LocalDate.of(2026, 3, 5), 360);
        final CourtSchedule day6 = adSession(LocalDate.of(2026, 3, 6), 360);
        final CourtSchedule day9 = adSession(LocalDate.of(2026, 3, 9), 360);

        when(courtScheduleRepository.findAdSessionsInRange(
                eq(OU_CODE), eq(String.valueOf(COURT_ROOM_ID)), eq(BUSINESS_TYPE),
                eq(LocalDate.of(2026, 3, 5)), eq(LocalDate.of(2026, 3, 9)), any()))
                .thenReturn(List.of(day5, day6, day9));

        final List<AllocatedListing> postExtend = new ArrayList<>(existingAllocations);
        postExtend.add(allocation(LocalDate.of(2026, 3, 5)));
        postExtend.add(allocation(LocalDate.of(2026, 3, 6)));
        postExtend.add(allocation(LocalDate.of(2026, 3, 9)));

        when(courtScheduleRepository.getCourtSchedulesByIdList(any()))
                .thenReturn(buildHydratedSchedules(existingAllocations))
                .thenReturn(buildHydratedSchedules(postExtend));

        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID))
                .thenReturn(existingAllocations)
                .thenReturn(postExtend);

        final List<CourtSchedule> result = service.extend(HEARING_ID,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 9), 2160);

        verify(allocatedListingRepository, times(3)).save(any(AllocatedListing.class));
        assertEquals(6, result.size());
        assertTrue(result.stream().noneMatch(cs ->
                cs.getSessionDate().getDayOfWeek().getValue() > 5));
    }

    @Test
    void extendBooksTailInRequestedRoom_when_courtRoomIdSupplied() {
        // SPRDT-1273: the caller-supplied main courtroom pins the tail search — room UUID passed
        // through — and the inserted allocation rows are built from the BOOKED SESSION (its room
        // number), not from an existing allocation template. SPRDT-1475: the pinned room does not
        // lift the business-type constraint — the tail keeps the block's business type.
        final String mainRoomUuid = "731816c1-5ee4-373a-9bda-840e13a5bcb0";
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID))
                .thenReturn(existingAllocations);

        final CourtSchedule day5 = adSession(LocalDate.of(2026, 3, 5), 360);
        day5.setCourtRoomId(mainRoomUuid);
        day5.setCourtRoomNumber(772);

        when(courtScheduleRepository.findAdSessionsInRange(
                eq(OU_CODE), eq(mainRoomUuid), eq(BUSINESS_TYPE),
                eq(LocalDate.of(2026, 3, 5)), eq(LocalDate.of(2026, 3, 5)), any()))
                .thenReturn(List.of(day5));

        final List<AllocatedListing> postExtend = new ArrayList<>(existingAllocations);
        postExtend.add(allocation(LocalDate.of(2026, 3, 5)));
        when(courtScheduleRepository.getCourtSchedulesByIdList(any()))
                .thenReturn(buildHydratedSchedules(existingAllocations))
                .thenReturn(buildHydratedSchedules(postExtend));
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID))
                .thenReturn(existingAllocations)
                .thenReturn(postExtend);

        service.extend(HEARING_ID, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 5), 1440, mainRoomUuid, null, 360);

        final org.mockito.ArgumentCaptor<AllocatedListing> rowCaptor =
                org.mockito.ArgumentCaptor.forClass(AllocatedListing.class);
        verify(allocatedListingRepository).save(rowCaptor.capture());
        assertEquals("cs-cand-2026-03-05", rowCaptor.getValue().getCourtScheduleId());
        assertEquals(772, rowCaptor.getValue().getCourtRoomId());
        assertEquals("EXTEND_MULTIDAY", rowCaptor.getValue().getSource());
        // Existing rows are never deleted or re-saved on extend.
        verify(allocatedListingRepository, never()).deleteByHearingIdAndSessionDateGreaterThan(anyString(), any());
    }

    @Test
    void extendInRequestedRoom_keepsTheLastDaysBusinessType_when_blockSpansTwoBusinessTypes() {
        // SPRDT-1475: a block can already hold two business types (change-court-room on the
        // remaining days); the tail continues the LAST day's business type, not day 1's.
        final String mainRoomUuid = "731816c1-5ee4-373a-9bda-840e13a5bcb0";
        final String lastDayBusinessType = "PLEA";
        final List<CourtSchedule> mixedBlock = buildHydratedSchedules(existingAllocations);
        mixedBlock.get(mixedBlock.size() - 1).setBusinessType(lastDayBusinessType);

        final CourtSchedule day5 = adSession(LocalDate.of(2026, 3, 5), 360);
        day5.setCourtRoomId(mainRoomUuid);
        day5.setBusinessType(lastDayBusinessType);
        when(courtScheduleRepository.findAdSessionsInRange(
                eq(OU_CODE), eq(mainRoomUuid), eq(lastDayBusinessType),
                eq(LocalDate.of(2026, 3, 5)), eq(LocalDate.of(2026, 3, 5)), any()))
                .thenReturn(List.of(day5));

        final List<AllocatedListing> postExtend = new ArrayList<>(existingAllocations);
        postExtend.add(allocation(LocalDate.of(2026, 3, 5)));
        when(courtScheduleRepository.getCourtSchedulesByIdList(any()))
                .thenReturn(mixedBlock)
                .thenReturn(buildHydratedSchedules(postExtend));
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID))
                .thenReturn(existingAllocations)
                .thenReturn(postExtend);

        service.extend(HEARING_ID, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 5), 1440, mainRoomUuid, null, 360);

        final org.mockito.ArgumentCaptor<AllocatedListing> rowCaptor =
                org.mockito.ArgumentCaptor.forClass(AllocatedListing.class);
        verify(allocatedListingRepository).save(rowCaptor.capture());
        assertEquals("cs-cand-2026-03-05", rowCaptor.getValue().getCourtScheduleId());
        assertEquals(lastDayBusinessType, rowCaptor.getValue().getRotaBusinessType());
    }

    @Test
    void extendInRequestedRoom_rejectsTail_when_roomHasNoSessionOfTheBlocksBusinessType() {
        // SPRDT-1475: a pinned room that only runs another business type on a tail day is
        // NO_AVAILABILITY — never a block that spans two business types.
        final String mainRoomUuid = "731816c1-5ee4-373a-9bda-840e13a5bcb0";
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID)).thenReturn(existingAllocations);
        when(courtScheduleRepository.getCourtSchedulesByIdList(any())).thenReturn(buildHydratedSchedules(existingAllocations));

        final ExtendMultidayHearingException e = assertThrows(ExtendMultidayHearingException.class,
                () -> service.extend(HEARING_ID, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 5),
                        1440, mainRoomUuid, null, 360));

        assertEquals(ExtendMultidayHearingException.ErrorCode.NO_AVAILABILITY, e.getErrorCode());
        assertEquals(List.of(LocalDate.of(2026, 3, 5)), e.getUnavailableDates());
        verify(courtScheduleRepository).findAdSessionsInRange(
                eq(OU_CODE), eq(mainRoomUuid), eq(BUSINESS_TYPE),
                eq(LocalDate.of(2026, 3, 5)), eq(LocalDate.of(2026, 3, 5)), any());
        verify(allocatedListingRepository, never()).save(any());
    }

    @Test
    void noAvailability_when_anyTailDayBlocked() {
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID)).thenReturn(existingAllocations);
        when(courtScheduleRepository.getCourtSchedulesByIdList(any())).thenReturn(buildHydratedSchedules(existingAllocations));

        final CourtSchedule day5 = adSession(LocalDate.of(2026, 3, 5), 360);
        when(courtScheduleRepository.findAdSessionsInRange(
                eq(OU_CODE), eq(String.valueOf(COURT_ROOM_ID)), eq(BUSINESS_TYPE),
                eq(LocalDate.of(2026, 3, 5)), eq(LocalDate.of(2026, 3, 6)), any()))
                .thenReturn(List.of(day5));

        final ExtendMultidayHearingException e = assertThrows(ExtendMultidayHearingException.class,
                () -> service.extend(HEARING_ID,
                        LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6), 1800));

        assertEquals(ExtendMultidayHearingException.ErrorCode.NO_AVAILABILITY, e.getErrorCode());
        assertEquals(List.of(LocalDate.of(2026, 3, 6)), e.getUnavailableDates());
        verify(allocatedListingRepository, never()).save(any());
    }

    @Test
    void extendBooksFullTailSessionBeyondCapacity_when_overbookingNotAllowed() {
        // SPRDT-1572: the Court Calendar journey is overbooking-exempt — a full tail session with
        // overbooking disallowed is still booked; capacity never makes a day unavailable.
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID)).thenReturn(existingAllocations);
        when(courtScheduleRepository.getCourtSchedulesByIdList(any())).thenReturn(buildHydratedSchedules(existingAllocations));

        final CourtSchedule fullDay5 = adSession(LocalDate.of(2026, 3, 5), 360).totalBooked(360).overbookingAllowed(false);
        when(courtScheduleRepository.findAdSessionsInRange(
                eq(OU_CODE), eq(String.valueOf(COURT_ROOM_ID)), eq(BUSINESS_TYPE),
                eq(LocalDate.of(2026, 3, 5)), eq(LocalDate.of(2026, 3, 5)), any()))
                .thenReturn(List.of(fullDay5));

        service.extend(HEARING_ID, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 5), 1440);

        final ArgumentCaptor<AllocatedListing> saved = ArgumentCaptor.forClass(AllocatedListing.class);
        verify(allocatedListingRepository).save(saved.capture());
        assertEquals(fullDay5.getCourtScheduleId(), saved.getValue().getCourtScheduleId());
    }

    @Test
    void extendPrefersSessionWithRoom_when_tailDateHasFullAndFreeSessions() {
        when(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(HEARING_ID)).thenReturn(existingAllocations);
        when(courtScheduleRepository.getCourtSchedulesByIdList(any())).thenReturn(buildHydratedSchedules(existingAllocations));

        final CourtSchedule full = adSession(LocalDate.of(2026, 3, 5), 360).totalBooked(360).courtScheduleId("cs-full");
        final CourtSchedule free = adSession(LocalDate.of(2026, 3, 5), 360).courtScheduleId("cs-free");
        when(courtScheduleRepository.findAdSessionsInRange(
                eq(OU_CODE), eq(String.valueOf(COURT_ROOM_ID)), eq(BUSINESS_TYPE),
                eq(LocalDate.of(2026, 3, 5)), eq(LocalDate.of(2026, 3, 5)), any()))
                .thenReturn(List.of(full, free));

        service.extend(HEARING_ID, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 5), 1440);

        final ArgumentCaptor<AllocatedListing> saved = ArgumentCaptor.forClass(AllocatedListing.class);
        verify(allocatedListingRepository).save(saved.capture());
        assertEquals("cs-free", saved.getValue().getCourtScheduleId());
    }

    private static AllocatedListing allocation(final LocalDate sessionDate) {
        final AllocatedListing al = new AllocatedListing();
        al.setId(UUID.randomUUID().toString());
        al.setHearingId(HEARING_ID);
        al.setCourtScheduleId("cs-" + sessionDate);
        al.setOucode(OU_CODE);
        al.setCourtRoomId(COURT_ROOM_ID);
        al.setRotaBusinessType(BUSINESS_TYPE);
        al.setDuration(360);
        al.setHearingStartTime(
                sessionDate.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        al.setSource("MULTIDAY");
        return al;
    }

    private static List<CourtSchedule> buildHydratedSchedules(final List<AllocatedListing> allocations) {
        final List<CourtSchedule> out = new ArrayList<>();
        for (final AllocatedListing al : allocations) {
            final LocalDate date = al.getHearingStartTime().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            out.add(new CourtSchedule()
                    .courtScheduleId(al.getCourtScheduleId())
                    .sessionDate(date)
                    .ouCode(OU_CODE)
                    .courtRoomId(String.valueOf(COURT_ROOM_ID))
                    .businessType(BUSINESS_TYPE)
                    .courtSession("AD"));
        }
        return out;
    }

    private static CourtSchedule adSession(final LocalDate date, final int availableMins) {
        return new CourtSchedule()
                .courtScheduleId("cs-cand-" + date)
                .sessionDate(date)
                .ouCode(OU_CODE)
                .courtRoomId(String.valueOf(COURT_ROOM_ID))
                .businessType(BUSINESS_TYPE)
                .courtSession("AD")
                .maxDuration(availableMins)
                .totalBooked(0)
                .sessionStartTime(date.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime());
    }
}
