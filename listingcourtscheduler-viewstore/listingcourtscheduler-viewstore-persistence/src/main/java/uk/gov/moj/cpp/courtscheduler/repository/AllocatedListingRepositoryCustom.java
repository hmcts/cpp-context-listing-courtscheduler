package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.IdResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.MiFilterCriteria;

import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Spring Data {@code Custom} fragment for {@link AllocatedListingRepository}.
 *
 * <p>Holds the methods that can't be expressed as a method-name derived query or a
 * single {@code @Query}: ones that build a result type from raw native rows, post-process
 * results into a different domain type, or rely on the {@link AllocatedHearingsQueryBuilder}
 * to assemble dynamic SQL. Spring Data auto-wires this fragment into the main
 * repository interface via the {@code …Impl} naming convention.</p>
 */
interface AllocatedListingRepositoryCustom {

    /**
     * Native {@code GROUP BY} on {@code allocated_listings} joined with {@code court_schedule}
     * to produce a per-{@code courtScheduleId} total — count of listings for slot-based
     * schedules, sum of durations otherwise. Result rows are unpacked into the
     * {@link AllocatedListingTotalBooked} record.
     */
    List<AllocatedListingTotalBooked> getAllocatedListingsByCourtScheduleId(List<String> courtScheduleIds);

    /**
     * Calls {@link AllocatedListingRepository#findByUpdatedOnGreaterThanAndUpdatedOnLessThan(java.util.Date, java.util.Date)}
     * and projects the result into the MI domain type.
     */
    List<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria);

    /**
     * Dynamic SQL produced by {@link AllocatedHearingsQueryBuilder}; returns a paged
     * set of hearing-id rows plus the total row count.
     */
    Pair<Integer, Set<IdResponse>> findHearingIdsBy(HearingSlotRequestParam hearingIdsReq);
}
