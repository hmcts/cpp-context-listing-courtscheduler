package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
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

    @Query("SELECT entity.courtScheduleId as courtScheduleId, entity.ouCode as ouCode, entity.createdOn as createdOn "
            + "FROM CourtSchedule entity WHERE entity.courtRoomId = :courtRoomId "
            + "AND entity.sessionDate = :sessionDate AND entity.businessType = :businessType "
            + "AND entity.courtSession = :courtSession")
    List<MatcherInfoRow> findMatcherInfoRowsByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(
            @Param(COURT_ROOM_ID) String courtRoomId,
            @Param(SESSION_DATE) LocalDate sessionDate,
            @Param(BUSINESS_TYPE) String businessType,
            @Param("courtSession") String courtSession,
            org.springframework.data.domain.Pageable pageable);

    /**
     * Strongly typed row projection for the matcher-info query. The generated OpenAPI model
     * ({@link CourtScheduleMatcherInfo}) can't be targeted directly:
     * <ul>
     *   <li>a true JPQL {@code SELECT new ...()} constructor expression needs Hibernate to find a
     *       constructor whose parameter types are assignable from the selected columns' types, but
     *       the entity's {@code createdOn} is {@code java.util.Date} while the generated model's is
     *       {@code OffsetDateTime} (openapi-generator always maps {@code format: date-time} to a
     *       {@code java.time} type) - Hibernate rejects this at bootstrap ("Missing constructor for
     *       type"), and</li>
     *   <li>Spring Data's implicit class-based (DTO) projection for a plain (no {@code new}) column
     *       select fails too - it converts each row to a {@code Map} first and there's no
     *       {@code Map -> CourtScheduleMatcherInfo} {@code Converter}, so it throws
     *       {@code ConverterNotFoundException}.</li>
     * </ul>
     * Both were empirically confirmed via {@code CourtScheduleRepositoryTest}. Only Spring Data's
     * interface-based projections work here, and OpenAPI codegen only emits concrete classes, never
     * interfaces, so there's no generated type that can stand in for this row shape. Note
     * {@code getCreatedOn()} stays {@code Date}, not {@code OffsetDateTime}: interface projections
     * don't run every accessor through a type-converting proxy either (confirmed the same way -
     * {@code UnsupportedOperationException: Cannot project java.sql.Timestamp to
     * java.time.OffsetDateTime}), so the conversion is done explicitly in
     * {@link #findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession} instead.
     */
    interface MatcherInfoRow {
        String getCourtScheduleId();

        String getOuCode();

        Date getCreatedOn();
    }

    /**
     * Wrapper preserving the legacy single-result signature ({@code max=1, OPTIONAL}). Spring
     * Data's {@code @Query} cannot mix a constructor projection with {@code Optional}/single
     * return; the underlying multi-row query is
     * {@link #findMatcherInfoRowsByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession}.
     * The {@code Pageable.ofSize(1)} pushes {@code LIMIT 1} to the DB so we don't ship
     * the full match set just to take the first row.
     */
    default CourtScheduleMatcherInfo findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(
            final String courtRoomId,
            final LocalDate sessionDate,
            final String businessType,
            final String courtSession) {
        final List<MatcherInfoRow> rows = findMatcherInfoRowsByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(
                courtRoomId, sessionDate, businessType, courtSession,
                org.springframework.data.domain.PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return null;
        }
        final MatcherInfoRow row = rows.get(0);
        return new CourtScheduleMatcherInfo()
                .courtScheduleId(row.getCourtScheduleId())
                .ouCode(row.getOuCode())
                .createdOn(DateUtils.toOffsetDateTime(row.getCreatedOn()));
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
