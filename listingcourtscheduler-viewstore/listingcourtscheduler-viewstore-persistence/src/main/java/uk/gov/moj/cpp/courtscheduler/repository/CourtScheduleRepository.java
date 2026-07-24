package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migrated from DeltaSpike's {@code AbstractEntityRepository<CourtSchedule, String>}
 * to a Spring Data JPA interface. Standard CRUD comes from {@link JpaRepository};
 * the legacy {@code @Query}-annotated abstract methods are preserved verbatim below;
 * everything that needed an explicit {@code EntityManager} (criteria queries, native
 * SQL, batch upserts, business orchestration) lives in
 * {@link CourtScheduleRepositoryCustom}.
 */
@Repository
public interface CourtScheduleRepository
        extends JpaRepository<CourtSchedule, String>, CourtScheduleRepositoryCustom {

    Logger LOGGER = LoggerFactory.getLogger(CourtScheduleRepository.class);

    String BUSINESS_TYPE = "businessType";
    String COURT_ROOM_ID = "courtRoomId";
    String OU_CODE = "ouCode";
    String COURT_CENTRE_ID = "courtCentreId";
    String SESSION_DATE = "sessionDate";

    // ---------------------------------------------------------------------
    //  Legacy {@code @Query}-annotated abstract methods.
    // ---------------------------------------------------------------------

    @Query("SELECT cs FROM CourtSchedule cs WHERE cs.ouCode IN :ouCodes AND cs.active = true AND cs.sessionDate BETWEEN :startDate AND :endDate")
    List<CourtSchedule> getExtractedCourtSchedules(@Param("ouCodes") List<String> ouCodes,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);

    @Query("SELECT cs FROM CourtSchedule cs WHERE cs.ouCode IN :ouCodes AND cs.sessionDate BETWEEN :startDate AND :endDate")
    List<CourtSchedule> getExtractedCourtSchedulesForGhostRota(@Param("ouCodes") List<String> ouCodes,
                                                               @Param("startDate") LocalDate startDate,
                                                               @Param("endDate") LocalDate endDate);

    @Query("SELECT cs FROM CourtSchedule cs WHERE cs.courtHouseId = :courtCentreId AND cs.courtRoomId = :courtRoomId AND cs.active = true AND cs.businessType = :businessType AND cs.sessionDate BETWEEN :startDate AND :endDate AND cs.jurisdiction = :jurisdiction")
    List<CourtSchedule> getSimilarSessions(@Param("courtCentreId") String courtCentreId,
                                           @Param("courtRoomId") String courtRoomId,
                                           @Param(BUSINESS_TYPE) String businessType,
                                           @Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate,
                                           @Param("jurisdiction") String jurisdiction);

    @Modifying
    @Transactional
    @Query("UPDATE CourtSchedule cs SET cs.active = false, cs.updatedOn = :updatedOn WHERE cs.courtScheduleId IN :courtScheduleIds AND cs.listingProfileId is not null")
    void deactivateSlots(@Param("courtScheduleIds") List<String> courtScheduleIds,
                         @Param("updatedOn") Date updatedOn);

    @Query("SELECT new uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo(entity.courtScheduleId, entity.ouCode, entity.createdOn) "
            + "FROM CourtSchedule entity WHERE entity.courtRoomId = :courtRoomId "
            + "AND entity.sessionDate = :sessionDate AND entity.businessType = :businessType "
            + "AND entity.courtSession = :courtSession")
    List<CourtScheduleMatcherInfo> findMatcherInfoByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(
            @Param(COURT_ROOM_ID) String courtRoomId,
            @Param(SESSION_DATE) LocalDate sessionDate,
            @Param(BUSINESS_TYPE) String businessType,
            @Param("courtSession") String courtSession,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Wrapper preserving the legacy single-result signature ({@code max=1, OPTIONAL}). Spring
     * Data's {@code @Query} cannot mix a constructor projection with {@code Optional}/single
     * return; the underlying multi-row query is
     * {@link #findMatcherInfoByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession}.
     * The {@code Pageable.ofSize(1)} pushes {@code LIMIT 1} to the DB so we don't ship
     * the full match set just to take the first row.
     */
    default CourtScheduleMatcherInfo findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(
            final String courtRoomId,
            final LocalDate sessionDate,
            final String businessType,
            final String courtSession) {
        final List<CourtScheduleMatcherInfo> rows = findMatcherInfoByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(
                courtRoomId, sessionDate, businessType, courtSession,
                org.springframework.data.domain.PageRequest.of(0, 1));
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Query("SELECT entity FROM CourtSchedule entity WHERE entity.courtRoomId = :courtRoomId "
            + "AND entity.sessionDate = :sessionDate AND entity.businessType = :businessType "
            + "AND entity.courtSession IN (:courtSessions) AND entity.active = true "
            + "AND entity.courtHouseId = :courtHouseId "
            + "AND entity.courtScheduleId != :excludeCourtScheduleId")
    List<CourtSchedule> findDuplicateSessionsForAssignCourtroom(@Param(COURT_ROOM_ID) String courtRoomId,
                                                                @Param(SESSION_DATE) LocalDate sessionDate,
                                                                @Param(BUSINESS_TYPE) String businessType,
                                                                @Param("courtSessions") List<String> courtSessions,
                                                                @Param("courtHouseId") String courtHouseId,
                                                                @Param("excludeCourtScheduleId") String excludeCourtScheduleId);

    @Query("SELECT cs FROM CourtSchedule cs WHERE cs.id IN :courtScheduleIds")
    List<CourtSchedule> findByCourtScheduleIds(@Param("courtScheduleIds") List<String> courtScheduleIds);

    @Query("SELECT cs FROM CourtSchedule cs WHERE cs.courtRoomId = :courtRoomId " +
            "AND cs.sessionDate >= :startDate AND cs.sessionDate <= :endDate " +
            "AND cs.businessType = :businessType AND cs.courtSession = :courtSession " +
            "AND cs.active = true")
    List<CourtSchedule> findActiveByCourtRoomIdBetweenDates(
            @Param(COURT_ROOM_ID) String courtRoomId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param(BUSINESS_TYPE) String businessType,
            @Param("courtSession") String courtSession);

    /** Method-name query — mirrors the legacy DeltaSpike abstract method. Used by the MI projection in the {@code Custom} fragment. */
    List<CourtSchedule> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Date fromDate, Date toDate);

    // ---------------------------------------------------------------------
    //  Backwards-compatible aliases for DeltaSpike's auto-generated CRUD methods.
    // ---------------------------------------------------------------------

    /** Alias for the legacy {@code findBy(K)} that returned the entity directly (or {@code null}). */
    default CourtSchedule findBy(final String id) {
        return id == null ? null : findById(id).orElse(null);
    }

    /** Legacy by-example helper used by the in-tree repository tests. */
    default List<CourtSchedule> findBy(final CourtSchedule example) {
        final CourtSchedule found = findBy(example.getCourtScheduleId());
        return found == null ? java.util.Collections.emptyList() : List.of(found);
    }

    default void remove(final CourtSchedule entity) {
        delete(entity);
    }

    @Transactional
    default void removeAndFlush(final CourtSchedule entity) {
        delete(entity);
        flush();
    }

    // ---------------------------------------------------------------------
    //  Static helper kept on the type — callers reference it via
    //  {@code CourtScheduleRepository.getCourtScheduleToBeUpdated(...)}.
    // ---------------------------------------------------------------------

    static CourtSchedule getCourtScheduleToBeUpdated(final CourtSchedule courtSchedule,
                                                     final boolean isForRotaFile,
                                                     final List<CourtSchedule> persistedCourtSchedules) {
        CourtSchedule persistedCourtSchedule = persistedCourtSchedules.get(0);
        if (persistedCourtSchedules.size() > 1 && isForRotaFile) {
            persistedCourtSchedule = persistedCourtSchedules.stream()
                    .filter(courtScheduleFound -> courtScheduleFound.getCourtSession().equals(courtSchedule.getCourtSession())
                            && courtScheduleFound.getPanel().equals(courtSchedule.getPanel()) && courtScheduleFound.isActive())
                    .findAny()
                    .orElse(persistedCourtSchedule);
            LOGGER.info("found persisted court schedule to update for rota file with courtScheduleId: {}", persistedCourtSchedule.getCourtScheduleId());
        }
        return persistedCourtSchedule;
    }
}
