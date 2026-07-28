package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackSearchResult;
import uk.gov.moj.cpp.courtscheduler.domain.Hearing;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Spring Data {@code Custom} fragment for {@link CourtScheduleRepository}. Houses the
 * methods that the legacy DeltaSpike repository wrote against the {@code EntityManager}
 * (criteria queries, native SQL, batch upserts, business orchestration). The
 * {@code …Impl} class is auto-discovered by Spring Data via the naming convention.
 */
public interface CourtScheduleRepositoryCustom {

    void saveCourtSchedules(List<CourtSchedule> courtSchedules);

    CourtSchedule update(CourtSchedule courtSchedule, boolean isForRotaFile);

    Result update(CourtSchedule persistedCourtSchedule,
                  UpdateCourtSchedule updateCourtSchedule,
                  Optional<CourtRoom> courtRoom);

    List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findBy(CourtScheduleRequestParam courtScheduleRequestParam);

    CourtSchedule retrieveCourtScheduleWithListingById(String courtScheduleId);

    int getInconsistentCourtSchedulersByOucode(String ouCode);

    List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedulesByIdList(List<String> courtScheduleIds);

    /** Attaches active judiciary assignments to the supplied domain schedules (SPRDT-1089). */
    void enrichWithJudiciary(List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules);

    /** First allocated_listings row for the hearing, if any (Crown fallback / idempotent retry check). */
    Optional<AllocatedListing> findAllocatedListingByHearingId(String hearingId);

    /**
     * Crown-only fallback search — strict on centre + date, relaxed on businessType/court_session
     * and (optionally) courtRoomId, trying non-draft/draft tiers with and without overbooking.
     */
    Optional<CrownFallbackSearchResult> searchCrownFallbackSlots(String courtCentreId,
                                                                 LocalDate hearingDate,
                                                                 int durationInMinutes,
                                                                 String courtRoomId,
                                                                 String earliestHearingTime);

    /** Consecutive AD weekday sessions sharing the anchor's room/businessType/draft state (SPRDT-1089). */
    List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findConsecutiveSessions(String anchorCourtScheduleId,
                                                                                     int daysNeeded);

    /** CROWN no-anchor consecutive search across a court centre's rooms (SPRDT-1089, AC3). */
    List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findConsecutiveSessionsForCentre(String courtCentreId,
                                                                                              LocalDate fromDate,
                                                                                              int daysNeeded);

    /** AD weekday sessions for a room/businessType in a date range, draft state unconstrained (extend path). */
    List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findAdSessionsInRange(String ouCode,
                                                                                   String courtRoomId,
                                                                                   String businessType,
                                                                                   LocalDate fromInclusive,
                                                                                   LocalDate toInclusive);

    /** Discovery + re-hydrate path for multi-day Crown searches (SPRDT-903 perf fix #4). */
    List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getMultidayHearingSlotCandidates(HearingSlotRequestParam requestParam,
                                                                                              int daysNeeded);

    List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedulesBy(CourtScheduleRequestParam courtScheduleRequestParam);

    List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria);

    Result saveBookedSlots(List<AllocatedSlot> slots, boolean isProvisionalSlot, boolean isSearchUpdate);

    /**
     * Variant with {@code releaseExistingHearingAllocations}: pass {@code false} when the caller has
     * already date-scope-released via {@link #releaseAllocatedListingsForDates(String, java.util.List)}
     * so the hearing-wide release doesn't wipe untouched days.
     */
    Result saveBookedSlots(List<AllocatedSlot> slots, boolean isProvisionalSlot, boolean isSearchUpdate,
                           boolean releaseExistingHearingAllocations);

    boolean searchBookHearingSlots(List<AllocatedSlot> slots);

    Optional<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findCourtScheduleById(String courtScheduleId);

    List<Hearing> updateListHearingSlots(RequestedSlots slots);

    Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> getCourtSchedules(HearingSlotRequestParam requestParam);

    List<CourtScheduleJudiciary> getCourtScheduleJudiciaries(List<CourtSchedule> courtScheduleList);

    List<CourtScheduleJudiciary> getCourtScheduleJudiciariesByCourtScheduleIds(List<String> courtScheduleIdList);

    List<CourtScheduleJudiciary> getCourtScheduleJudiciariesForProvisionalBooking(List<CourtSchedule> courtScheduleList);

    List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> deleteCourtSchedule(List<String> courtScheduleIdList);

    int deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(LocalDate startDate, LocalDate endDate, List<String> ouCodes);

    int deleteUnAllocatedProvisionalEntries(List<String> ouCodes);

    int deleteSlots(List<String> courtScheduleIds);

    void releaseAllocatedSlotsOrDurationFromCourtSchedule(List<AllocatedListing> allocatedListings);

    CourtSchedule searchListHearingSlotFilterCriteria(String courtCentreId,
                                                      LocalDate sessionDate,
                                                      LocalDate sessionEndDate,
                                                      LocalDateTime sessionStartTime,
                                                      String courtRoomId,
                                                      Boolean isPolice);

    int deleteRedundantRotaData(int numberOfDays);

    void releaseOldAllocatedListings(String hearingId);

    /** Date-scoped sibling of {@link #releaseOldAllocatedListings(String)}. */
    void releaseAllocatedListingsForDates(String hearingId, java.util.List<java.time.LocalDate> dates);
}
