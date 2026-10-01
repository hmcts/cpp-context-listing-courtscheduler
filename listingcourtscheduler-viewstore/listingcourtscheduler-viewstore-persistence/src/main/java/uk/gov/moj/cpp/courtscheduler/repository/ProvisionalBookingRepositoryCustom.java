package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring Data {@code Custom} fragment for {@link ProvisionalBookingRepository}. The legacy
 * repository wrote every query against the JPA Criteria API to walk the
 * {@code ProvisionalBookingKey} composite key — that's preserved verbatim in the
 * {@link ProvisionalBookingRepositoryImpl} class below.
 */
interface ProvisionalBookingRepositoryCustom {

    /** Map {@code courtScheduleId → hearingStartTime} for every active booking matching the supplied booking IDs. */
    Map<String, Instant> getCourtScheduleInfo(List<String> bookingSlots);

    /** Single-booking lookup by {@code bookingId} — uses the same JPA Criteria filter as {@link #findByBookingIdIn}. */
    Optional<ProvisionalBooking> findByBookingId(String bookingId);

    /** Bulk lookup by {@code bookingId IN (...)}. */
    List<ProvisionalBooking> findByBookingIdIn(List<String> bookingIds);

    /** Build + persist a {@link ProvisionalBooking} from a domain {@link ProvisionalSlot}. */
    void saveProvisionalBooking(ProvisionalSlot provisionalSlot, String bookingId, CourtSchedule courtSchedule);
}
