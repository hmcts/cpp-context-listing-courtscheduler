package uk.gov.moj.cpp.courtscheduler.repository;

import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleQueryParameterNames.COURT_SCHEDULE_IDS;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migrated 1:1 from the legacy DeltaSpike {@code AbstractEntityRepository<CourtScheduleJudiciary, CourtScheduleJudiciaryKey>}.
 * Preserves the legacy method-name and {@link Query} annotation style: queries that DeltaSpike
 * generated from the method name remain method-name-derived; queries that the legacy file wrote
 * out as {@code @Query} stay as {@code @Query}; methods that needed an explicit {@code EntityManager}
 * (dynamic native SQL, MI-domain projection, refresh) live in the package-private
 * {@link CourtScheduleJudiciaryRepositoryCustom} fragment + its {@code …Impl} class — both
 * colocated in this file so a single read shows the whole repository.
 */
@Repository
public interface CourtScheduleJudiciaryRepository
        extends JpaRepository<CourtScheduleJudiciary, CourtScheduleJudiciaryKey>, CourtScheduleJudiciaryRepositoryCustom {

    /** Spring Data generates the JPQL: {@code SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.email = :email}. Returns {@code null} when no match. */
    CourtScheduleJudiciary findByEmail(String email);

    /** Date-range method-name query — used by the MI projection in the {@code Custom} fragment. */
    List<CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Instant fromDate, Instant toDate);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.courtScheduleId = ?1")
    List<CourtScheduleJudiciary> findByCourtScheduleId(String courtScheduleId);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.judiciaryId = ?1 AND csj.active = true")
    List<CourtScheduleJudiciary> findByJudiciaryId(String judiciaryId);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.courtScheduleId IN (:courtScheduleIds)")
    List<CourtScheduleJudiciary> findInCourtScheduleIds(@Param(COURT_SCHEDULE_IDS) List<String> courtScheduleIds);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.judiciaryId IN (:judiciaryIds) AND csj.active = true")
    List<CourtScheduleJudiciary> findByJudiciaryIds(@Param("judiciaryIds") List<String> judiciaryIds);

    @Modifying
    @Transactional
    @Query("UPDATE CourtScheduleJudiciary csj "
            + "SET csj.active = false, csj.updatedOn = :updatedOn "
            + "WHERE csj.id.courtScheduleId IN :courtScheduleIds")
    void deactivateSchedules(@Param(COURT_SCHEDULE_IDS) List<String> courtScheduleIds,
                             @Param("updatedOn") Instant updatedOn);

    @Modifying
    @Transactional
    @Query("UPDATE CourtScheduleJudiciary csj "
            + "SET csj.position = :position, csj.active = true, csj.updatedOn = :updatedOn "
            + "WHERE csj.id.courtScheduleId = :courtScheduleId AND csj.id.judiciaryId = :judiciaryId")
    void updateCourtScheduleJudiciaryPosition(@Param("position") String position,
                                              @Param("updatedOn") Instant updatedOn,
                                              @Param("courtScheduleId") String courtScheduleId,
                                              @Param("judiciaryId") String judiciaryId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM court_schedule_judiciary csj WHERE csj.court_schedule_id IN "
            + " (SELECT cs.id FROM court_schedule cs WHERE cs.session_start BETWEEN :startDate AND :endDate AND cs.oucode IN (:ouCodes) AND active = true)",
            nativeQuery = true)
    int deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(@Param("startDate") LocalDate startDate,
                                                                     @Param("endDate") LocalDate endDate,
                                                                     @Param("ouCodes") List<String> ouCodes);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM court_schedule_judiciary csj WHERE csj.court_schedule_id IN (:courtScheduleIds) "
            + "AND not exists(select 1 from provisional_booking pb WHERE pb.active = true AND pb.court_schedule_id = csj.court_schedule_id)",
            nativeQuery = true)
    int deleteSchedules(@Param(COURT_SCHEDULE_IDS) List<String> courtScheduleIds);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM court_schedule_judiciary csj WHERE csj.court_schedule_id IN "
            + "(SELECT cs.id FROM court_schedule cs WHERE cs.session_start < (CURRENT_DATE - :numberOfDays))",
            nativeQuery = true)
    int deleteRedundantRotaData(@Param("numberOfDays") int numberOfDays);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM court_schedule_judiciary WHERE court_schedule_id IN (:courtScheduleIds)",
            nativeQuery = true)
    int deleteAllForCourtScheduleIds(@Param(COURT_SCHEDULE_IDS) List<String> courtScheduleIds);

    /**
     * Removes all judiciary rows for the given court schedules (replace-all user assignment).
     */
    default int deleteAllAssignmentsForCourtScheduleIds(final List<String> courtScheduleIds) {
        if (courtScheduleIds == null || courtScheduleIds.isEmpty()) {
            return 0;
        }
        return deleteAllForCourtScheduleIds(courtScheduleIds);
    }

    // ---------------------------------------------------------------------
    //  Backwards-compatible aliases for DeltaSpike's auto-generated CRUD methods.
    // ---------------------------------------------------------------------

    default CourtScheduleJudiciary findBy(final CourtScheduleJudiciaryKey key) {
        return key == null ? null : findById(key).orElse(null);
    }

    default void remove(final CourtScheduleJudiciary entity) {
        delete(entity);
    }
}
