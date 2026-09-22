package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.exception.ConfirmedBookingExistsException;
import uk.gov.moj.cpp.courtscheduler.exception.NoCapacityException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final String SESSION_ID = "cs-1";
    private static final String BOOKING_ID = "bk-1";

    @InjectMocks
    private ReservationService reservationService;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private AllocatedListingRepository allocatedListingRepository;

    @Mock
    private SessionsService sessionsService;

    // -----------------------------------------------------------------------
    // BUG9: capacity must be enforced on reserve, not only at share time
    // -----------------------------------------------------------------------

    @Test
    void shouldRejectAReservationWhenTheSessionIsFull() {
        // validator reports the session cannot take it
        givenSessionExists();
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(sessionsService.validateSessionAvailabilityListMode(List.of(SESSION_ID), 60))
                .thenReturn(Optional.of("One or more schedules are no longer available, please reschedule your hearing"));

        final NoCapacityException thrown = assertThrows(NoCapacityException.class,
                () -> reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60))));

        assertThat(thrown.getMessage(), containsString("no longer available"));
        verify(courtScheduleRepository, never()).saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldReserveWhenTheSessionHasCapacity() {
        // validator reports no problem
        givenSessionExists();
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(sessionsService.validateSessionAvailabilityListMode(List.of(SESSION_ID), 60))
                .thenReturn(Optional.empty());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)));

        verify(courtScheduleRepository).saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldAllowAnOverbookingExemptSessionToBeReservedWhenFull() {
        // the validator itself skips sessions whose isOverbookingAllowed is true, so it
        // reports no problem even at capacity — assert we do not add a second rule that
        // overrides that exemption
        givenSessionExists();
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(sessionsService.validateSessionAvailabilityListMode(List.of(SESSION_ID), 60))
                .thenReturn(Optional.empty());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)));

        verify(courtScheduleRepository).saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldNotCountTheCallersOwnHoldAgainstItOnARePick() {
        // session at capacity, but every booked row belongs to THIS bookingId: the caller's own
        // existing hold on SESSION_ID is returned by findByBookingId, and the shared validator is
        // stubbed as if the session were still full — a naive implementation that always calls the
        // validator would reject this legal re-pick.
        givenSessionExists();
        final AllocatedListing ownExistingHold = new AllocatedListing();
        ownExistingHold.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        ownExistingHold.setCourtScheduleId(SESSION_ID);
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of(ownExistingHold));
        // lenient: a correct implementation must not even call the validator for a session the
        // booking already holds, so this stub is deliberately allowed to go unused.
        lenient().when(sessionsService.validateSessionAvailabilityListMode(List.of(SESSION_ID), 60))
                .thenReturn(Optional.of("One or more schedules are no longer available, please reschedule your hearing"));
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)));

        verify(courtScheduleRepository).saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldReserveWithTodaysExpiryAndUnconfirmedSource() {
        givenSessionExists();
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        final var slot = reservationService.reserve(SESSION_ID, BOOKING_ID, "2026-10-14T10:00:00.000Z", 60);

        assertThat(slot, is(notNullValue()));
        assertThat(slot.getExpiresAt(), is(LocalDate.now(ZoneOffset.UTC)));
        assertThat(slot.getBookingId(), is(BOOKING_ID));
        assertThat("a reservation holds no hearing yet", slot.getHearingId(), is(nullValue()));
        assertThat(slot.getSource(), is("RESERVED_UNCONFIRMED"));
    }

    // The re-pick wipe used to be inherited: a reservation's hearing_id WAS the bookingId, so
    // saveBookedSlots' own hearing-wide release covered it. Reservations now live in booking_id,
    // so that release no longer sees them and reserveAll must ask for it explicitly. Without this
    // call a clerk changing their mind would hold BOTH sessions and the abandoned one would sit on
    // capacity until the 01:00 purge.
    @Test
    void shouldReleaseTheBookingsOwnUnconfirmedHoldsBeforeTakingTheNewOnes() {
        givenSessionExists();
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Result.SUCCESS());

        reservationService.reserve(SESSION_ID, BOOKING_ID, "2026-10-14T10:00:00.000Z", 60);

        final InOrder inOrder = inOrder(courtScheduleRepository);
        inOrder.verify(courtScheduleRepository).releaseReservationsForBooking(BOOKING_ID);
        inOrder.verify(courtScheduleRepository)
                .saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    // The pipeline must not ALSO try its hearing-wide release: these rows carry no hearing_id, so
    // asking for it would at best do nothing and at worst release by a null key.
    @Test
    void shouldNotAskThePipelineForItsHearingWideRelease() {
        givenSessionExists();
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Result.SUCCESS());

        reservationService.reserve(SESSION_ID, BOOKING_ID, "2026-10-14T10:00:00.000Z", 60);

        verify(courtScheduleRepository)
                .saveBookedSlots(anyList(), eq(false), eq(false), eq(false));
        verify(courtScheduleRepository, never()).releaseOldAllocatedListings(anyString());
    }

    @Test
    void shouldRejectWhenBookingAlreadyHasAConfirmedAllocation() {
        final AllocatedListing confirmed = new AllocatedListing();
        confirmed.setExpiresAt(null);
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of(confirmed));

        assertThrows(ConfirmedBookingExistsException.class,
                () -> reservationService.reserve(SESSION_ID, BOOKING_ID, "2026-10-14T10:00:00.000Z", 60));
    }

    @Test
    void shouldAllowReservingOverAnExistingUnconfirmedReservation() {
        givenSessionExists();
        final AllocatedListing existingReservation = new AllocatedListing();
        existingReservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of(existingReservation));
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        final var slot = reservationService.reserve(SESSION_ID, BOOKING_ID, "2026-10-14T10:00:00.000Z", 60);

        assertThat(slot, is(notNullValue()));
        assertThat(slot.getExpiresAt(), is(LocalDate.now(ZoneOffset.UTC)));
    }

    @Test
    void shouldThrowNoCapacityWhenPersistFails() {
        givenSessionExists();
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(Result.FAILED("no capacity"));

        assertThrows(NoCapacityException.class,
                () -> reservationService.reserve(SESSION_ID, BOOKING_ID, "2026-10-14T10:00:00.000Z", 60));
    }

    // -----------------------------------------------------------------------
    // CRITICAL 1: a booking's slots must be reserved in ONE saveBookedSlots call
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void shouldReserveEverySlotOfABookingInASingleSaveBookedSlotsCall() {
        // Every slot of a booking is keyed on the same minted bookingId, and saveBookedSlots opens
        // with a hearing-wide release on that key. One call per slot therefore released the
        // booking's own earlier slots — a 3-slot pick ended up holding 1. Assert the whole booking
        // goes over in a single call.
        givenSessionsExist("cs-1", "cs-2", "cs-3");
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        final List<AllocatedSlot> reserved = reservationService.reserveAll(BOOKING_ID,
                List.of(slotRequest("cs-1", 60), slotRequest("cs-2", 60), slotRequest("cs-3", 60)));

        assertThat(reserved.size(), is(3));

        final ArgumentCaptor<List<AllocatedSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(courtScheduleRepository, times(1)).saveBookedSlots(captor.capture(), eq(false), eq(false), eq(false));
        final List<AllocatedSlot> persisted = captor.getValue();
        assertThat("the whole booking must go over in one call", persisted.size(), is(3));
        assertThat(persisted.stream().map(AllocatedSlot::getCourtScheduleId).toList(),
                is(List.of("cs-1", "cs-2", "cs-3")));
        persisted.forEach(slot -> {
            assertThat(slot.getBookingId(), is(BOOKING_ID));
        assertThat("a reservation holds no hearing yet", slot.getHearingId(), is(nullValue()));
            assertThat(slot.getExpiresAt(), is(LocalDate.now(ZoneOffset.UTC)));
            assertThat(slot.getSource(), is("RESERVED_UNCONFIRMED"));
        });
    }

    @Test
    void shouldRunTheConfirmedAllocationGuardOnceForTheWholeBooking() {
        givenSessionsExist("cs-1", "cs-2", "cs-3");
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        reservationService.reserveAll(BOOKING_ID,
                List.of(slotRequest("cs-1", 60), slotRequest("cs-2", 60), slotRequest("cs-3", 60)));

        verify(allocatedListingRepository, times(1)).findByBookingId(BOOKING_ID);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDeriveSlotBasedFromEachSlotsOwnSession() {
        // A booking can mix a slot-based session with a duration-based one; each row's isSlotBased
        // must come from its own session, not from the first one resolved.
        givenSlotBasedSession("cs-slot");
        givenDurationBasedSession("cs-duration");
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest("cs-slot", 60), slotRequest("cs-duration", 45)));

        final ArgumentCaptor<List<AllocatedSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(courtScheduleRepository).saveBookedSlots(captor.capture(), eq(false), eq(false), eq(false));
        assertThat(captor.getValue().get(0).isSlotBased(), is(true));
        assertThat(captor.getValue().get(1).isSlotBased(), is(false));
    }

    // -----------------------------------------------------------------------
    // I3: a duration-based session must not be held for zero minutes
    // -----------------------------------------------------------------------

    @Test
    void shouldRejectADurationBasedSessionWithNoDuration() {
        // duration is optional in the request schema. Coercing null to 0 wrote a row that
        // decremented available_duration by nothing at all: the hold existed but the session
        // stayed bookable, so another clerk could take it.
        givenDurationBasedSession(SESSION_ID);
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());

        final ValidationException thrown = assertThrows(ValidationException.class,
                () -> reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, null))));

        assertThat(thrown.getErrors().getString("errorMessage").contains("duration is required"), is(true));
        verify(courtScheduleRepository, never()).saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldRejectADurationBasedSessionWithAZeroDuration() {
        givenDurationBasedSession(SESSION_ID);
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());

        assertThrows(ValidationException.class,
                () -> reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 0))));

        verify(courtScheduleRepository, never()).saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldAllowASlotBasedSessionWithNoDuration() {
        // A slot-based session decrements available_slots by one; duration is irrelevant there,
        // so omitting it must stay legal.
        givenSessionExists();
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        final List<AllocatedSlot> reserved =
                reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, null)));

        assertThat(reserved.size(), is(1));
    }

    // -----------------------------------------------------------------------
    // CROWN MULTI-DAY HOLD: a Crown multi-day pick reserves the WHOLE run immediately,
    // never only its anchor day — and never for lack of capacity (F1 court-calendar rule).
    // -----------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void shouldReserveTheWholeRunForACrownDurationBasedMultiDayPick() {
        // 1800 minutes / 360 = 5 days, 360 mins/day. Anchor is Monday; the run is Mon-Fri.
        final LocalDate monday = LocalDate.of(2026, 10, 12);
        givenCrownSession(SESSION_ID, monday, 360, 0);
        final List<CourtSchedule> run = crownConsecutiveRun(monday, 5, 360, 0);
        when(courtScheduleRepository.findConsecutiveSessions(SESSION_ID, 5)).thenReturn(run);
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        final List<AllocatedSlot> reserved =
                reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 1800)));

        assertThat(reserved.size(), is(5));
        reserved.forEach(slot -> {
            assertThat(slot.getBookingId(), is(BOOKING_ID));
        assertThat("a reservation holds no hearing yet", slot.getHearingId(), is(nullValue()));
            assertThat(slot.getExpiresAt(), is(LocalDate.now(ZoneOffset.UTC)));
            assertThat(slot.getSource(), is("RESERVED_UNCONFIRMED"));
            assertThat(slot.getDuration(), is(360));
        });
        // never even consulted for a Crown multi-day run — capacity must never block it
        verify(sessionsService, never()).validateSessionAvailabilityListMode(anyList(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReserveTheWholeCrownRunInASingleSaveBookedSlotsCall() {
        // Guards the release-each-other trap: every day of the run must go over in ONE call,
        // since saveBookedSlots opens with a hearing-wide release keyed on the shared bookingId.
        final LocalDate monday = LocalDate.of(2026, 10, 12);
        givenCrownSession(SESSION_ID, monday, 360, 0);
        final List<CourtSchedule> run = crownConsecutiveRun(monday, 5, 360, 0);
        when(courtScheduleRepository.findConsecutiveSessions(SESSION_ID, 5)).thenReturn(run);
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 1800)));

        final ArgumentCaptor<List<AllocatedSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(courtScheduleRepository, times(1)).saveBookedSlots(captor.capture(), eq(false), eq(false), eq(false));
        assertThat("the whole 5-day run must go over in one call", captor.getValue().size(), is(5));
    }

    @Test
    void shouldReserveTheWholeCrownRunEvenWhenOneDayIsFull() {
        // F1 court-calendar rule: capacity NEVER blocks a Crown multi-day reservation. One of the
        // 5 days is completely full (and not overbooking-exempt) — the run is still reserved whole.
        final LocalDate monday = LocalDate.of(2026, 10, 12);
        givenCrownSession(SESSION_ID, monday, 360, 0);
        final List<CourtSchedule> run = crownConsecutiveRun(monday, 5, 360, 0);
        run.get(2).setTotalBooked(360); // Wednesday: fully booked, overbooking not allowed
        when(courtScheduleRepository.findConsecutiveSessions(SESSION_ID, 5)).thenReturn(run);
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        final List<AllocatedSlot> reserved =
                reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 1800)));

        assertThat(reserved.size(), is(5));
        verify(courtScheduleRepository, times(1)).saveBookedSlots(anyList(), eq(false), eq(false), eq(false));
    }

    @Test
    void shouldThrowNoCapacityAndNeverSaveWhenTheCrownRunIsStructurallyImpossible() {
        // Only 3 sessions available where 5 are needed: a STRUCTURAL failure — the selector
        // returns empty, and NOTHING is persisted.
        final LocalDate monday = LocalDate.of(2026, 10, 12);
        givenCrownSession(SESSION_ID, monday, 360, 0);
        final List<CourtSchedule> shortRun = crownConsecutiveRun(monday, 3, 360, 0);
        when(courtScheduleRepository.findConsecutiveSessions(SESSION_ID, 5)).thenReturn(shortRun);
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());

        assertThrows(NoCapacityException.class,
                () -> reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 1800))));

        verify(courtScheduleRepository, never()).saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldReserveExactlyOneSessionForASingleDayCrownRequestUnchanged() {
        // durationInMinutes == 360 (one court day): NOT multi-day, completely unaffected.
        givenCrownSession(SESSION_ID, LocalDate.of(2026, 10, 12), 360, 0);
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(sessionsService.validateSessionAvailabilityListMode(List.of(SESSION_ID), 360))
                .thenReturn(Optional.empty());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        final List<AllocatedSlot> reserved =
                reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 360)));

        assertThat(reserved.size(), is(1));
        verify(courtScheduleRepository, never()).findConsecutiveSessions(anyString(), anyInt());
    }

    @Test
    void shouldNotExpandASlotBasedCrownSessionEvenIfADurationIsSent() {
        // Slot-based sessions are untouched by the Crown multi-day expansion, whatever duration
        // value happens to be present on the request.
        final CourtSchedule slotBasedCrown = crownSession(SESSION_ID, LocalDate.of(2026, 10, 12), 360, 0);
        slotBasedCrown.setSlotBased(true);
        when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(SESSION_ID))).thenReturn(List.of(slotBasedCrown));
        when(allocatedListingRepository.findByBookingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        final List<AllocatedSlot> reserved =
                reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 1800)));

        assertThat(reserved.size(), is(1));
        verify(courtScheduleRepository, never()).findConsecutiveSessions(anyString(), anyInt());
    }

    private void givenCrownSession(final String sessionId, final LocalDate date, final int maxDuration, final int totalBooked) {
        when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(sessionId)))
                .thenReturn(List.of(crownSession(sessionId, date, maxDuration, totalBooked)));
    }

    private static CourtSchedule crownSession(final String sessionId, final LocalDate date,
                                              final int maxDuration, final int totalBooked) {
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(sessionId);
        session.setActive(true);
        session.setOuCode("B01LY");
        session.setSessionDate(date);
        session.setSlotBased(false);
        session.setJurisdiction("CROWN");
        session.setMaxDuration(maxDuration);
        session.setTotalBooked(totalBooked);
        session.setIsOverbookingAllowed(false);
        return session;
    }

    /** Builds {@code count} consecutive-business-day CROWN sessions starting at {@code start}, ids cs-1..cs-N. */
    private static List<CourtSchedule> crownConsecutiveRun(final LocalDate start, final int count,
                                                           final int maxDuration, final int totalBooked) {
        final List<CourtSchedule> sessions = new java.util.ArrayList<>();
        LocalDate date = start;
        for (int i = 1; i <= count; i++) {
            sessions.add(crownSession("cs-" + i, date, maxDuration, totalBooked));
            date = uk.gov.moj.cpp.courtscheduler.common.utils.SessionAvailability.getNextBusinessDay(date);
        }
        return sessions;
    }

    private static ProvisionalSlot slotRequest(final String courtScheduleId, final Integer duration) {
        return ProvisionalSlot.ProvisionalSlotBuilder.aProvisionalSlot()
                .withCourtScheduleId(courtScheduleId)
                .withHearingStartTime("2026-10-14T10:00:00.000Z")
                .withDuration(duration)
                .build();
    }

    private void givenSessionsExist(final String... sessionIds) {
        for (final String sessionId : sessionIds) {
            givenSlotBasedSession(sessionId);
        }
    }

    private void givenSlotBasedSession(final String sessionId) {
        when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(sessionId)))
                .thenReturn(List.of(session(sessionId, true)));
    }

    private void givenDurationBasedSession(final String sessionId) {
        when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(sessionId)))
                .thenReturn(List.of(session(sessionId, false)));
    }

    private static CourtSchedule session(final String sessionId, final boolean slotBased) {
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(sessionId);
        session.setActive(true);
        session.setOuCode("B01LY");
        session.setSessionDate(LocalDate.of(2026, 10, 14));
        session.setSlotBased(slotBased);
        return session;
    }

    private void givenSessionExists() {
        givenSlotBasedSession(SESSION_ID);
    }
}
