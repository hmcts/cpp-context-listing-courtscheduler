package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

@Service
@org.springframework.transaction.annotation.Transactional
public class SlotsRemoveService {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    /**
     * Releases what the given id holds. The id is a real hearing id when listing vacates a
     * hearing, and a bookingId when the results UI abandons an unconfirmed pick — reservations no
     * longer borrow hearing_id, so both lookups are needed and each is a no-op for the other's
     * callers.
     *
     * <p>The booking-scoped release only ever removes unconfirmed rows, so passing a bookingId
     * whose result has already been shared leaves the confirmed listing alone. Only a share may
     * change a confirmed booking.
     */
    public void remove(final String hearingIdOrBookingId) {
        courtScheduleRepository.releaseOldAllocatedListings(hearingIdOrBookingId);
        courtScheduleRepository.releaseReservationsForBooking(hearingIdOrBookingId);
    }
}
