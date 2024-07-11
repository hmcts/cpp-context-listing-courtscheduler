package uk.gov.moj.cpp.courtscheduler.repository;

import static uk.gov.moj.cpp.courtscheduler.utils.QueryConstants.NOT_EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE;

import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.Modifying;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = CourtScheduleJudiciary.class)
public abstract class CourtScheduleJudiciaryRepository extends AbstractEntityRepository<CourtScheduleJudiciary, CourtScheduleJudiciaryKey> {

    private static final String DELETE_UNALLOCATED_COURT_SCHEDULE_JUDICIARY_QUERY = "DELETE FROM COURT_SCHEDULE_JUDICIARY WHERE court_schedule_id IN " +
            " (SELECT id FROM court_Schedule WHERE court_listing_profile_id is not null AND max_slot = available_slot " +
            "AND max_duration_mins = available_duration_mins AND session_start BETWEEN :startDate AND :endDate AND oucode IN :ouCodes " +
            "AND not exists (" + NOT_EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + "))  AND active = true";

    public static final String DELETE_CSJ_BY_IDS_QUERY = "DELETE FROM COURT_SCHEDULE_JUDICIARY WHERE court_schedule_id IN :courtScheduleIds " +
            "AND not exists(select 1 from provisional_booking pb WHERE pb.active = true AND pb.court_schedule_id = court_schedule_id)";

    public abstract CourtScheduleJudiciary findByEmail(String email);

    abstract List<CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Date fromDate, Date toDate);
    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.courtScheduleId = ?1")
    abstract List<CourtScheduleJudiciary> findByCourtScheduleId(String courtScheduleId);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.courtScheduleId IN :courtScheduleIds")
    public abstract List<CourtScheduleJudiciary> findInCourtScheduleIds(@QueryParam("courtScheduleIds") final String courtScheduleIds);

    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria) {
        List<CourtScheduleJudiciary> courtScheduleJudiciaries = findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
                DateUtils.getDate(miFilterCriteria.getFromLocalDate()),
                DateUtils.getDate(miFilterCriteria.getToLocalDate()));
        return courtScheduleJudiciaries.stream().map(courtScheduleJudiciaryEntity -> new uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.Builder()
                .withCourtScheduleId(courtScheduleJudiciaryEntity.getId().getCourtScheduleId())
                .withJudiciaryId(courtScheduleJudiciaryEntity.getId().getJudiciaryId())
                .withPosition(courtScheduleJudiciaryEntity.getPosition())
                .withForenames(courtScheduleJudiciaryEntity.getForenames())
                .withEmailAddress(courtScheduleJudiciaryEntity.getEmail())
                .build()).toList();

    }

    public int deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(final LocalDate startDate,
                                                                             final LocalDate endDate,
                                                                             final String ouCodes) {
        return entityManager()
                .createNativeQuery(DELETE_UNALLOCATED_COURT_SCHEDULE_JUDICIARY_QUERY)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setParameter("ouCodes", ouCodes)
                .executeUpdate();
    }

    public int deleteSchedules(final String courtScheduleIds) {
        return entityManager()
                .createNativeQuery(DELETE_CSJ_BY_IDS_QUERY)
                .setParameter("courtScheduleIds", courtScheduleIds)
                .executeUpdate();
    }

    @Modifying
    @Query(value = "UPDATE CourtScheduleJudiciary csj SET csj.active = false, csj.updatedOn = :updatedOn WHERE csj.id.courtScheduleId IN :courtScheduleIds")
    public abstract void deactivateSchedules(@QueryParam("courtScheduleIds") final String courtScheduleIds, @QueryParam("updatedOn") final Date updatedOn);

    @Modifying
    @Query(value = "UPDATE CourtScheduleJudiciary csj SET csj.position = :position, csj.active = true, csj.updatedOn = :updatedOn WHERE csj.id.courtScheduleId =:courtScheduleId and csj.id.judiciaryId = :judiciaryId")
    public abstract void updateCourtScheduleJudiciaryPosition(@QueryParam("position") final String position,
                                                              @QueryParam("updatedOn") final Date updatedOn,
                                                              @QueryParam("courtScheduleId") final String courtScheduleId,
                                                              @QueryParam("judiciaryId") final String judiciaryId);

}
