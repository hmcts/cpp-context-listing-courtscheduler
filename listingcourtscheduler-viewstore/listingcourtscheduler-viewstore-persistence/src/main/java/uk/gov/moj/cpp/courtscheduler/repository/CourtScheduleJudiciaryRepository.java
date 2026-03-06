package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.annotation.processing.Generated;

import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.Modifying;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = CourtScheduleJudiciary.class)
public abstract class CourtScheduleJudiciaryRepository extends AbstractEntityRepository<CourtScheduleJudiciary, CourtScheduleJudiciaryKey> {

    private static final String DELETE_UNALLOCATED_COURT_SCHEDULE_JUDICIARY_QUERY = "DELETE FROM court_schedule_judiciary csj WHERE csj.court_schedule_id IN " +
            " (SELECT cs.id FROM court_schedule cs WHERE cs.session_start BETWEEN :startDate AND :endDate AND cs.oucode IN (:ouCodes) AND active = true)";

    public static final String DELETE_CSJ_BY_IDS_QUERY = "DELETE FROM court_schedule_judiciary csj WHERE csj.court_schedule_id IN (:courtScheduleIds) " +
            "AND not exists(select 1 from provisional_booking pb WHERE pb.active = true AND pb.court_schedule_id = csj.court_schedule_id)";

    private static final String SELECT_ALLOCATED_COURT_SCHEDULE_JUDICIARY_QUERY = "SELECT csj.court_schedule_id AS courtScheduleId, " +
            "csj.judiciary_id AS judiciaryId " +
            "FROM court_schedule_judiciary csj,court_schedule cs " +
            "WHERE  cs.id = csj.court_schedule_id and csj.active = true and cs.active = true " +
            "AND cs.oucode IN (:ouCodes)" +
            "AND cs.session_start BETWEEN :startDate AND :endDate " +
            "AND ( " +
            "EXISTS ( " +
            "SELECT 1 " +
            "FROM allocated_listings al " +
            "WHERE al.court_schedule_id = cs.id ) " +
            "OR EXISTS ( " +
            "SELECT 1 " +
            "FROM provisional_booking pb " +
            "WHERE pb.court_schedule_id = cs.id " +
            "AND pb.active = true )" +
            ")";

    private static final String DELETE_REDUNDANT_ROTA_DATA = "DELETE FROM court_schedule_judiciary csj WHERE csj.court_schedule_id IN (SELECT cs.id FROM court_schedule cs WHERE cs.session_start < (CURRENT_DATE - :numberOfDays))";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";

    public abstract CourtScheduleJudiciary findByEmail(String email);

    abstract List<CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Date fromDate, Date toDate);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.courtScheduleId = ?1")
    abstract List<CourtScheduleJudiciary> findByCourtScheduleId(String courtScheduleId);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.judiciaryId = ?1 AND csj.active = true")
    public abstract List<CourtScheduleJudiciary> findByJudiciaryId(String judiciaryId);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.courtScheduleId IN (:courtScheduleIds)")
    public abstract List<CourtScheduleJudiciary> findInCourtScheduleIds(@QueryParam("courtScheduleIds") final List<String> courtScheduleIds);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.judiciaryId IN (:judiciaryIds) AND csj.active = true")
    public abstract List<CourtScheduleJudiciary> findByJudiciaryIds(@QueryParam("judiciaryIds") final List<String> judiciaryIds);

    public List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria) {
        List<CourtScheduleJudiciary> courtScheduleJudiciaries = findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
                DateUtils.getDate(miFilterCriteria.getFromLocalDate()),
                DateUtils.getDate(miFilterCriteria.getToLocalDate()));
        return courtScheduleJudiciaries.stream().map(courtScheduleJudiciaryEntity -> new uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary.Builder()
                .withCourtScheduleId(courtScheduleJudiciaryEntity.getId().getCourtScheduleId())
                .withJudiciaryId(courtScheduleJudiciaryEntity.getId().getJudiciaryId())
                .withPosition(courtScheduleJudiciaryEntity.getPosition())
                .withTitle(courtScheduleJudiciaryEntity.getTitle())
                .withForenames(courtScheduleJudiciaryEntity.getForenames())
                .withSurname(courtScheduleJudiciaryEntity.getSurname())
                .withEmailAddress(courtScheduleJudiciaryEntity.getEmail())
                .withJudiciaryType(courtScheduleJudiciaryEntity.getJudiciaryType())
                .withIsBenchChairman(courtScheduleJudiciaryEntity.getBenchChairman())
                .withIsDeputy(courtScheduleJudiciaryEntity.getDeputy())
                .withPosition(courtScheduleJudiciaryEntity.getPosition())
                .withCourtListingProfileId(courtScheduleJudiciaryEntity.getCourtListingProfileId())
                .withRotaJudiciaryId(courtScheduleJudiciaryEntity.getRotaJudiciaryId())
                .withActive(courtScheduleJudiciaryEntity.getActive())
                .withCreatedOn(courtScheduleJudiciaryEntity.getCreatedOn())
                .withUpdatedOn(courtScheduleJudiciaryEntity.getUpdatedOn())
                .build()).toList();

    }

    public int deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return entityManager()
                .createNativeQuery(DELETE_UNALLOCATED_COURT_SCHEDULE_JUDICIARY_QUERY)
                .setParameter(START_DATE, startDate)
                .setParameter(END_DATE, endDate)
                .setParameter("ouCodes", ouCodes)
                .executeUpdate();
    }

    public int deleteSchedules(@QueryParam("courtScheduleIds") final List<String> courtScheduleIds) {
        return entityManager()
                .createNativeQuery(DELETE_CSJ_BY_IDS_QUERY)
                .setParameter("courtScheduleIds", courtScheduleIds)
                .executeUpdate();
    }

    @SuppressWarnings("squid:S2077")
    public List getAllocatedScheduleJudiciaryInfo(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return entityManager()
                .createNativeQuery(SELECT_ALLOCATED_COURT_SCHEDULE_JUDICIARY_QUERY)
                .setParameter(START_DATE, startDate)
                .setParameter(END_DATE, endDate)
                .setParameter("ouCodes", ouCodes)
                .getResultList();
    }

    @Modifying
    @Query(value = "UPDATE CourtScheduleJudiciary csj SET csj.active = false, csj.updatedOn = :updatedOn WHERE csj.id.courtScheduleId IN :courtScheduleIds")
    public abstract void deactivateSchedules(@QueryParam("courtScheduleIds") final List<String> courtScheduleIds, @QueryParam("updatedOn") final Date updatedOn);

    @Modifying
    @Query(value = "UPDATE CourtScheduleJudiciary csj SET csj.position = :position, csj.active = true, csj.updatedOn = :updatedOn WHERE csj.id.courtScheduleId =:courtScheduleId and csj.id.judiciaryId = :judiciaryId")
    public abstract void updateCourtScheduleJudiciaryPosition(@QueryParam("position") final String position,
                                                              @QueryParam("updatedOn") final Date updatedOn,
                                                              @QueryParam("courtScheduleId") final String courtScheduleId,
                                                              @QueryParam("judiciaryId") final String judiciaryId);


    public int deleteRedundantRotaData(final int numberOfDays) {
        return entityManager()
                .createNativeQuery(DELETE_REDUNDANT_ROTA_DATA)
                .setParameter("numberOfDays", numberOfDays)
                .executeUpdate();
    }

    /**
     * Find court schedule IDs where a judiciary is assigned within a date range.
     * Returns a list of court schedule IDs that have the specified judiciary assigned
     * and whose session date falls within the given date range.
     */
    public List<String> findCourtScheduleIdsByJudiciaryAndDateRange(
            final String judiciaryId,
            final LocalDate startDate,
            final LocalDate endDate) {
        final String query = "SELECT DISTINCT cs.id " +
                "FROM court_schedule cs " +
                "INNER JOIN court_schedule_judiciary csj ON cs.id = csj.court_schedule_id " +
                "WHERE csj.judiciary_id = :judiciaryId " +
                "AND cs.session_start BETWEEN :startDate AND :endDate " +
                "AND cs.active = true " +
                "AND csj.active = true";
        
        @SuppressWarnings("unchecked")
        final List<String> result = entityManager()
                .createNativeQuery(query)
                .setParameter("judiciaryId", judiciaryId)
                .setParameter(START_DATE, startDate)
                .setParameter(END_DATE, endDate)
                .getResultList();

        return result;
    }

    /**
     * Find court schedule IDs where a judiciary is assigned within a date range and session type.
     * Returns a list of court schedule IDs that have the specified judiciary assigned,
     * whose session date falls within the given date range, and whose session type matches.
     * If ruleSessionType is AD, it matches AM, PM, or AD session types.
     * If ruleSessionType is AM or PM, it matches the exact session type and AD.
     */
    public List<Object[]> findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
            final String judiciaryId,
            final LocalDate startDate,
            final LocalDate endDate,
            final String ruleSessionType) {
        final List<String> sessionTypes = new ArrayList<>();

        if (!"AD".equals(ruleSessionType)) {
            //  AM or PM needs to add AD
            sessionTypes.addAll(Arrays.asList(ruleSessionType, "AD"));
        } else {
            sessionTypes.add("AD");
        }

        final String query = "SELECT DISTINCT cs.id, cs.session_start, cs.court_session " +
                "FROM court_schedule cs " +
                "INNER JOIN court_schedule_judiciary csj ON cs.id = csj.court_schedule_id " +
                "WHERE csj.judiciary_id = :judiciaryId " +
                "AND cs.session_start BETWEEN :startDate AND :endDate " +
                "AND cs.active = true " +
                "AND csj.active = true " +
                "AND cs.court_session IN (:sessionTypes)";

        @SuppressWarnings("unchecked")
        final List<Object[]> result = entityManager()
                .createNativeQuery(query)
                .setParameter("judiciaryId", judiciaryId)
                .setParameter(START_DATE, startDate)
                .setParameter(END_DATE, endDate)
                .setParameter("sessionTypes", sessionTypes)
                .getResultList();

        return result;
    }
}
