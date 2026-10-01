package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;

import java.time.LocalDate;
import java.util.List;

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
