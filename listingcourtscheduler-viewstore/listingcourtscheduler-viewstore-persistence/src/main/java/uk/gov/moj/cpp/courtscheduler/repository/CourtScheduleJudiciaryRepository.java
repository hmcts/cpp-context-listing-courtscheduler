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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
    List<CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Date fromDate, Date toDate);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.courtScheduleId = ?1")
    List<CourtScheduleJudiciary> findByCourtScheduleId(String courtScheduleId);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.judiciaryId = ?1 AND csj.active = true")
    List<CourtScheduleJudiciary> findByJudiciaryId(String judiciaryId);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.courtScheduleId IN (:courtScheduleIds)")
    List<CourtScheduleJudiciary> findInCourtScheduleIds(@Param("courtScheduleIds") List<String> courtScheduleIds);

    @Query("SELECT csj FROM CourtScheduleJudiciary csj WHERE csj.id.judiciaryId IN (:judiciaryIds) AND csj.active = true")
    List<CourtScheduleJudiciary> findByJudiciaryIds(@Param("judiciaryIds") List<String> judiciaryIds);

    @Modifying
    @Transactional
    @Query("UPDATE CourtScheduleJudiciary csj "
            + "SET csj.active = false, csj.updatedOn = :updatedOn "
            + "WHERE csj.id.courtScheduleId IN :courtScheduleIds")
    void deactivateSchedules(@Param("courtScheduleIds") List<String> courtScheduleIds,
                             @Param("updatedOn") Date updatedOn);

    @Modifying
    @Transactional
    @Query("UPDATE CourtScheduleJudiciary csj "
            + "SET csj.position = :position, csj.active = true, csj.updatedOn = :updatedOn "
            + "WHERE csj.id.courtScheduleId = :courtScheduleId AND csj.id.judiciaryId = :judiciaryId")
    void updateCourtScheduleJudiciaryPosition(@Param("position") String position,
                                              @Param("updatedOn") Date updatedOn,
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
    int deleteSchedules(@Param("courtScheduleIds") List<String> courtScheduleIds);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM court_schedule_judiciary csj WHERE csj.court_schedule_id IN "
            + "(SELECT cs.id FROM court_schedule cs WHERE cs.session_start < (CURRENT_DATE - :numberOfDays))",
            nativeQuery = true)
    int deleteRedundantRotaData(@Param("numberOfDays") int numberOfDays);

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

/**
 * Spring Data {@code Custom} fragment for {@link CourtScheduleJudiciaryRepository}.
 *
 * <p>Methods that can't be expressed as a single {@code @Query} or a method-name
 * derivation: dynamic native SQL, post-processing into MI domain types, and
 * direct {@code EntityManager.refresh} access.</p>
 */
interface CourtScheduleJudiciaryRepositoryCustom {

    /** Calls the date-range JPQL query and projects each row into the MI domain type. */
    List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria);

    /** Native SQL — returns court_schedule_id, judiciary_id rows for active entries with allocated listings or provisional bookings. */
    @SuppressWarnings("rawtypes")
    List getAllocatedScheduleJudiciaryInfo(LocalDate startDate, LocalDate endDate, List<String> ouCodes);

    /**
     * Court schedule IDs where the supplied judiciary is assigned within the date range
     * (active entries on both join sides). Native SQL because the legacy version was too.
     */
    List<String> findCourtScheduleIdsByJudiciaryAndDateRange(String judiciaryId, LocalDate startDate, LocalDate endDate);

    /**
     * Same as {@link #findCourtScheduleIdsByJudiciaryAndDateRange} but additionally filters by
     * {@code court_session} matching the supplied session-type set ({@code AD} alone vs
     * {@code AM/PM + AD}). Returns the {@code (id, session_start, court_session)} triplet.
     */
    List<Object[]> findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(String judiciaryId, LocalDate startDate, LocalDate endDate, String ruleSessionType);

    /** {@link jakarta.persistence.EntityManager#refresh(Object)} — used by the in-tree repository tests. */
    void refresh(CourtScheduleJudiciary entity);
}

/**
 * Spring Data picks this up by the {@code …Impl} naming convention as the implementation
 * of {@link CourtScheduleJudiciaryRepositoryCustom}.
 */
class CourtScheduleJudiciaryRepositoryImpl implements CourtScheduleJudiciaryRepositoryCustom {

    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";

    private static final String SELECT_ALLOCATED_COURT_SCHEDULE_JUDICIARY_QUERY =
            "SELECT csj.court_schedule_id AS courtScheduleId, " +
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

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
            final MiFilterCriteria miFilterCriteria) {
        final List<CourtScheduleJudiciary> rows = entityManager.createQuery(
                        "SELECT csj FROM CourtScheduleJudiciary csj "
                                + "WHERE csj.updatedOn > :fromDate AND csj.updatedOn < :toDate",
                        CourtScheduleJudiciary.class)
                .setParameter("fromDate", DateUtils.getDate(miFilterCriteria.getFromLocalDate()))
                .setParameter("toDate", DateUtils.getDate(miFilterCriteria.getToLocalDate()))
                .getResultList();

        return rows.stream().map(entity -> new uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary.Builder()
                .withCourtScheduleId(entity.getId().getCourtScheduleId())
                .withJudiciaryId(entity.getId().getJudiciaryId())
                .withPosition(entity.getPosition())
                .withTitle(entity.getTitle())
                .withForenames(entity.getForenames())
                .withSurname(entity.getSurname())
                .withEmailAddress(entity.getEmail())
                .withJudiciaryType(entity.getJudiciaryType())
                .withIsBenchChairman(entity.getBenchChairman())
                .withIsDeputy(entity.getDeputy())
                .withPosition(entity.getPosition())
                .withCourtListingProfileId(entity.getCourtListingProfileId())
                .withRotaJudiciaryId(entity.getRotaJudiciaryId())
                .withActive(entity.getActive())
                .withCreatedOn(entity.getCreatedOn())
                .withUpdatedOn(entity.getUpdatedOn())
                .build()).toList();
    }

    @Override
    @SuppressWarnings({"rawtypes", "squid:S2077"})
    public List getAllocatedScheduleJudiciaryInfo(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return entityManager
                .createNativeQuery(SELECT_ALLOCATED_COURT_SCHEDULE_JUDICIARY_QUERY)
                .setParameter(START_DATE, startDate)
                .setParameter(END_DATE, endDate)
                .setParameter("ouCodes", ouCodes)
                .getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> findCourtScheduleIdsByJudiciaryAndDateRange(
            final String judiciaryId, final LocalDate startDate, final LocalDate endDate) {
        final String query = "SELECT DISTINCT cs.id " +
                "FROM court_schedule cs " +
                "INNER JOIN court_schedule_judiciary csj ON cs.id = csj.court_schedule_id " +
                "WHERE csj.judiciary_id = :judiciaryId " +
                "AND cs.session_start BETWEEN :startDate AND :endDate " +
                "AND cs.active = true " +
                "AND csj.active = true";

        return entityManager
                .createNativeQuery(query)
                .setParameter("judiciaryId", judiciaryId)
                .setParameter(START_DATE, startDate)
                .setParameter(END_DATE, endDate)
                .getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object[]> findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
            final String judiciaryId,
            final LocalDate startDate,
            final LocalDate endDate,
            final String ruleSessionType) {
        final List<String> sessionTypes = new ArrayList<>();
        if (!"AD".equals(ruleSessionType)) {
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

        return entityManager
                .createNativeQuery(query)
                .setParameter("judiciaryId", judiciaryId)
                .setParameter(START_DATE, startDate)
                .setParameter(END_DATE, endDate)
                .setParameter("sessionTypes", sessionTypes)
                .getResultList();
    }

    @Override
    @Transactional
    public void refresh(final CourtScheduleJudiciary entity) {
        entityManager.refresh(entity);
    }
}
