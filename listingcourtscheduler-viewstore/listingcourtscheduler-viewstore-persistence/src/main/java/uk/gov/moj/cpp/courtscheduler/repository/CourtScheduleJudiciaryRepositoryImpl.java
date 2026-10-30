package uk.gov.moj.cpp.courtscheduler.repository;

import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleQueryParameterNames.END_DATE;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleQueryParameterNames.START_DATE;

import uk.gov.moj.cpp.courtscheduler.openapi.model.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data picks this up by the {@code …Impl} naming convention as the implementation
 * of {@link CourtScheduleJudiciaryRepositoryCustom}.
 */
class CourtScheduleJudiciaryRepositoryImpl implements CourtScheduleJudiciaryRepositoryCustom {

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
                .setParameter("fromDate", DateUtils.getDate(miFilterCriteria.getFromDate()).toInstant())
                .setParameter("toDate", DateUtils.getDate(miFilterCriteria.getToDate()).toInstant())
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
                .withIsBenchChairman(entity.isBenchChairman())
                .withIsDeputy(entity.isDeputy())
                .withPosition(entity.getPosition())
                .withCourtListingProfileId(entity.getCourtListingProfileId())
                .withRotaJudiciaryId(entity.getRotaJudiciaryId())
                .withActive(entity.isActive())
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
        if (ALL_DAY.equals(ruleSessionType)) {
            sessionTypes.add(ALL_DAY);
        } else {
            sessionTypes.addAll(Arrays.asList(ruleSessionType, ALL_DAY));
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
