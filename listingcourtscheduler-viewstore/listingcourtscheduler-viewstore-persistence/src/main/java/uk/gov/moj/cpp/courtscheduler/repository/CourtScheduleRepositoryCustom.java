package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
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

    List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedulesBy(CourtScheduleRequestParam courtScheduleRequestParam);

    List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria);

    Result saveBookedSlots(List<AllocatedSlot> slots, boolean isProvisionalSlot, boolean isSearchUpdate);

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

    Optional<CourtSchedule> findSessionForMoveToPastDate(String courtCentreId,
                                                         String courtRoomId,
                                                         LocalDate sessionDate,
                                                         LocalDateTime sessionStartTime,
                                                         String jurisdiction);
}
