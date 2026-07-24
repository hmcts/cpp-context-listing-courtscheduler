package uk.gov.moj.cpp.courtscheduler.repository;

import static java.lang.String.format;
import static java.time.LocalDate.now;
import static java.util.Objects.nonNull;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.getOrElseDefaultSessionStartAndEndTimeIfEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toExactTimestamp;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toIsoString;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toIsoStringExtended;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toMeridian;
import static uk.gov.moj.cpp.courtscheduler.utils.QueryConstants.EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE;

import uk.gov.moj.cpp.courtscheduler.converter.CourtSchedulerConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackSearchResult;
import uk.gov.moj.cpp.courtscheduler.domain.Hearing;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.SlotProcessingContext;
import uk.gov.moj.cpp.courtscheduler.domain.SlotStartTime;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.repository.criteria.CourtScheduleCriteria;
import uk.gov.moj.cpp.courtscheduler.service.CourtScheduleRetryService;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data picks this up by the {@code …Impl} naming convention as the implementation
 * of {@link CourtScheduleRepositoryCustom}. The legacy DeltaSpike {@code @Query}-annotated
 * abstract methods now live on {@link CourtScheduleRepository}; the EntityManager-driven
 * business logic stays here.
 */
@SuppressWarnings({"squid:S1312", "squid:S2629", "squid:S6813"})
@Component
@Transactional(readOnly = true)
public class CourtScheduleRepositoryImpl implements CourtScheduleRepositoryCustom {

    public static final String BUSINESS_TYPE = "businessType";
    // Returns one row per court_schedule. Aggregates fold allocated_listings into the group so
    // availability (available_slot, available_duration_mins, totalbooked{,formorning,forafternoon})
    // matches the semantics of the courtscheduler.get.hearing.slots query. hasHearingsBooked is
    // BOOL_OR so the presence flag holds whenever any allocated_listing exists for the schedule.
    public static final StringBuilder COURT_SCHEDULE_ALL_FIELDS_QUERY_STRING = new StringBuilder("""
                SELECT
                    s.id,
                    s.court_listing_profile_id,
                    s.oucode,
                    s.court_room_id,
                    s.court_room_number,
                    s.court_house_id,
                    s.court_house_name,
                    s.court_room_name,
                    s.operational_unit,
                    s.rota_business_type,
                    s.panel,
                    s.court_session,
                    s.active,
                    s.is_slot_based,
                    s.session_start,
                    s.max_slot,
                    s.max_duration_mins,
                    CASE WHEN s.is_slot_based = true THEN s.max_slot - count(al.duration) ELSE 0 END as available_slot,
                    CASE WHEN s.is_slot_based = false and s.support_ad_split = false THEN s.max_duration_mins - COALESCE(sum(al.duration), 0) ELSE 0 END as available_duration_mins,
                    COALESCE(BOOL_OR(al.id IS NOT NULL), false) as hasHearingsBooked,
                    s.created_on,
                    s.updated_on,
                    s.support_ad_split,
                    s.max_ad_morning_duration,
                    s.max_ad_afternoon_duration,
                    s.session_start_time,
                    s.session_end_time,
                    s.is_overbooking_allowed,
                    s.is_draft,
                    s.jurisdiction,
            
                    -- Total booked duration
                    CAST(COALESCE(SUM(al.duration), 0) AS INTEGER) AS totalbooked,
            
                    -- Total booked for morning
                    COALESCE(
                    SUM(
                        CASE
                            WHEN s.is_slot_based =false and s.court_session = 'AD' AND s.support_ad_split = true THEN
                                CASE
                                    WHEN al.hearing_start_time < national_break_time AND al.hearing_start_time + CAST(al.duration || ' minutes' AS INTERVAL) <= national_break_time
                                        THEN al.duration
                                    WHEN al.hearing_start_time < national_break_time AND al.hearing_start_time + CAST(al.duration || ' minutes' AS INTERVAL) > national_break_time
                                        THEN EXTRACT(EPOCH FROM (national_break_time - al.hearing_start_time)) / 60
                                    WHEN al.hearing_start_time < national_break_time
                                        THEN al.duration
                                    ELSE 0
                                    END
                        ELSE 0
                        END
                        ),0
                    ) AS totalbookedformorning,COALESCE(
                    SUM(
                        CASE
                            WHEN s.is_slot_based =false and s.court_session = 'AD' AND s.support_ad_split = true THEN
                                CASE
                                    WHEN al.hearing_start_time >= national_break_time
                                        THEN al.duration
                                    WHEN al.hearing_start_time < national_break_time AND al.hearing_start_time + CAST(al.duration || ' minutes' AS INTERVAL) > national_break_time
                                        THEN EXTRACT(EPOCH FROM (al.hearing_start_time + CAST(al.duration || ' minutes' AS INTERVAL) -  national_break_time)) / 60
                                    WHEN al.hearing_start_time >= national_break_time
                                        THEN al.duration
                                    ELSE 0
                                    END
                            ELSE 0
                            END
                        ),0
                    ) AS totalbookedforafternoon
            
                FROM court_schedule s
                LEFT OUTER JOIN allocated_listings al ON s.id = al.court_schedule_id
                WHERE s.active = TRUE AND s.id IN (:courtScheduleIds)
                GROUP BY
                    s.id,
                    s.court_listing_profile_id,
                    s.oucode,
                    s.court_room_id,
                    s.court_room_number,
                    s.court_house_id,
                    s.court_house_name,
                    s.court_room_name,
                    s.operational_unit,
                    s.rota_business_type,
                    s.panel,
                    s.court_session,
                    s.active,
                    s.is_slot_based,
                    s.session_start,
                    s.max_slot,
                    s.max_duration_mins,
                    s.available_slot,
                    s.available_duration_mins,
                    s.created_on,
                    s.updated_on,
                    s.support_ad_split,
                    s.max_ad_morning_duration,
                    s.max_ad_afternoon_duration,
                    s.session_start_time,
                    s.session_end_time,
                    s.is_overbooking_allowed,
                    s.is_draft,
                    s.jurisdiction
            """);

    private static final int BATCH_SIZE = 50;
    // PostgreSQL wire protocol encodes bind-parameter count as a signed 16-bit integer (max 32767).
    // IN-clause queries with large ID lists must be partitioned below that ceiling.
    private static final int ID_QUERY_BATCH_SIZE = 1000;
    public static final String COURT_ROOM_ID = "courtRoomId";
    public static final String OU_CODE = "ouCode";
    public static final String COURT_CENTRE_ID = "courtCentreId";
    public static final String COURT_SCHEDULE_IDS = "courtScheduleIds";
    public static final String SESSION_DATE = "sessionDate";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";
    public static final String COURTCENTREID_QUERY_CONDITION_STRING = "AND s.court_house_id = :courtCentreId ";
    public static final String SESSION_START_QUERY_CONDITION_STRING = "AND s.session_start = :sessionDate ";
    public static final String BUSINESS_TYPE_QUERY_CONDITION_STRING = "AND s.rota_business_type IN (:businessType) ";
    public static final String COURT_ROOM_ID_QUERY_CONDITION_STRING = "AND s.court_room_id = :courtRoomId ";

    @PersistenceContext
    EntityManager entityManager;
    @Inject
    CourtScheduleCriteria courtScheduleCriteria;
    // @Lazy breaks the legacy CourtScheduleRepository <-> CourtScheduleRetryService cycle
    // tolerated by CDI but rejected by Spring's AOP-aware proxy machinery.
    @Inject
    @org.springframework.context.annotation.Lazy
    CourtScheduleRetryService courtScheduleRetryService;
    @Inject
    uk.gov.moj.cpp.courtscheduler.service.CourtScheduleBatchInsertService courtScheduleBatchInsertService;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;
    @Inject
    ProvisionalBookingRepository provisionalBookingRepository;

    /**
     * Internal save helper preserving the legacy {@code AbstractEntityRepository#save} semantics:
     * persist if the row doesn't yet exist in the DB (manually-set {@code String} ids would
     * otherwise be routed through {@code merge} by Spring Data's default), merge otherwise.
     */
    private CourtSchedule saveInternal(final CourtSchedule entity) {
        if (entity.getCourtScheduleId() == null
                || entityManager.find(CourtSchedule.class, entity.getCourtScheduleId()) == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtScheduleRepositoryImpl.class.getName());

    private static final ModelMapper JUDICIARY_ENTITY_TO_DOMAIN_MODEL_MAPPER = new ModelMapper();

    private static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary mapJudiciaryEntityToDomain(final CourtScheduleJudiciary entity) {
        final uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary domain =
                JUDICIARY_ENTITY_TO_DOMAIN_MODEL_MAPPER.map(entity, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class);
        domain.setEmailAddress(entity.getEmail());
        return domain;
    }

    private static final String DELETE_UNALLOCATED_COURT_SCHEDULE_QUERY = "DELETE FROM court_schedule cs " +
            "WHERE  not exists (select 1 from allocated_listings al where al.court_schedule_id = cs.id) and " +
            "cs.session_start BETWEEN :startDate AND :endDate AND cs.oucode IN (:ouCodes) AND cs.active =true AND NOT EXISTS( " + EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + ")";

    public static final String DELETE_UNALLOCATED_FORECAST_SLOT_QUERY = "DELETE FROM court_schedule " +
            "WHERE court_listing_profile_id is null AND not exists (select 1 from allocated_listings al where al.court_schedule_id = id) AND oucode IN (:ouCodes) " +
            "AND active =true and not exists( " + EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + ")";

    public static final String DELETE_SLOTS_BY_IDS_QUERY = "DELETE FROM court_schedule cs WHERE cs.id IN (:courtScheduleIds) AND cs.court_listing_profile_id is not null AND not exists (select 1 from allocated_listings al where al.court_schedule_id = cs.id) AND  not exists(" + EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + ")";

    private static final String DELETE_REDUNDANT_ROTA_DATA = "DELETE FROM court_schedule cs WHERE cs.session_start < (CURRENT_DATE - :numberOfDays)";

    private static final String GET_HEARING_SLOTS_QUERY_SELECT_FROM = """
            SELECT cs.id,
            cs.court_listing_profile_id,
            cs.oucode,
            cs.court_room_id,
            cs.court_room_number,
            cs.court_house_id,
            cs.court_house_name,
            cs.court_room_name,
            cs.operational_unit,
            cs.rota_business_type,
            cs.panel,
            cs.court_session,
            cs.active,
            cs.is_slot_based,
            cs.session_start,
            cs.max_slot,
            cs.max_duration_mins,
            CASE WHEN cs.is_slot_based = true THEN cs.max_slot - count(al.duration) ELSE 0 END as available_slot,
            CASE WHEN cs.is_slot_based = false and cs.support_ad_split =false THEN  cs.max_duration_mins - COALESCE(sum(al.duration), 0) ELSE 0 END as available_duration_mins,
            cs.support_ad_split,
            cs.max_ad_morning_duration,
            cs.max_ad_afternoon_duration,
            cs.is_overbooking_allowed,
            cs.session_start_time,
            cs.session_end_time,
            cs.created_on,
            cs.updated_on,
            cs.national_break_time,
            cs.is_draft,
            cs.jurisdiction,
            -- Adjusted calculation for morning bookings
            COALESCE(
                    SUM(
                        CASE
                            WHEN cs.is_slot_based =false and cs.court_session = 'AD' AND cs.support_ad_split = true THEN
                                CASE
                                    WHEN al.hearing_start_time < national_break_time AND al.hearing_start_time + CAST(al.duration || ' minutes' AS INTERVAL) <= national_break_time
                                        THEN al.duration
                                    WHEN al.hearing_start_time < national_break_time AND al.hearing_start_time + CAST(al.duration || ' minutes' AS INTERVAL) > national_break_time
                                        THEN EXTRACT(EPOCH FROM (national_break_time - al.hearing_start_time)) / 60
                                    WHEN al.hearing_start_time < national_break_time
                                        THEN al.duration
                                    ELSE 0
                                    END
                        ELSE 0
                        END
                        ),0
                    ) AS totalbookedformorning,
            -- Adjusted calculation for afternoon bookings
            COALESCE(
                    SUM(
                        CASE
                            WHEN cs.is_slot_based =false and cs.court_session = 'AD' AND cs.support_ad_split = true THEN
                                CASE
                                    WHEN al.hearing_start_time >= national_break_time
                                        THEN al.duration
                                    WHEN al.hearing_start_time < national_break_time AND al.hearing_start_time + CAST(al.duration || ' minutes' AS INTERVAL) > national_break_time
                                        THEN EXTRACT(EPOCH FROM (al.hearing_start_time + CAST(al.duration || ' minutes' AS INTERVAL) -  national_break_time)) / 60
                                    WHEN al.hearing_start_time >= national_break_time
                                        THEN al.duration
                                    ELSE 0
                                    END
                            ELSE 0
                            END
                        ),0
                    ) AS totalbookedforafternoon,
            -- Total booked duration (sum of both)
            COALESCE(
                    SUM(al.duration),0
                    ) AS totalbooked
            FROM court_schedule cs LEFT JOIN allocated_listings al ON cs.id = al.court_schedule_id
            """;

    private static final String GET_HEARING_SLOTS_QUERY_MANDATORY_PARAMS = GET_HEARING_SLOTS_QUERY_SELECT_FROM + """
            WHERE cs.active = true
            AND cs.panel in (:panelType)
            AND cs.session_start BETWEEN :sessionStart AND :sessionEnd
            """;
    private static final String GET_HEARING_SLOTS_QUERY_GROUP_BY = """
                GROUP BY 
                    cs.id,
                    cs.court_listing_profile_id,
                    cs.oucode,
                    cs.court_room_id,
                    cs.court_room_number,
                    cs.court_house_id,
                    cs.court_house_name,
                    cs.court_room_name,
                    cs.operational_unit,
                    cs.rota_business_type,
                    cs.panel,
                    cs.court_session,
                    cs.active,
                    cs.is_slot_based,
                    cs.session_start,
                    cs.max_slot,
                    cs.max_duration_mins,
                    cs.available_slot,
                    cs.available_duration_mins,
                    cs.support_ad_split,
                    cs.max_ad_morning_duration,
                    cs.max_ad_afternoon_duration,
                    cs.is_overbooking_allowed,
                    cs.is_draft,
                    cs.jurisdiction
                ORDER BY cs.session_start, cs.court_house_name, cs.court_room_name, cs.court_session, cs.rota_business_type
            """;

    private static final String GET_HEARING_SLOTS_QUERY_PAGINATION = """
            LIMIT :pageSize OFFSET :offset
            """;

    // Re-hydrate the discovered multi-day candidate ids through the SAME aggregating projection
    // the single-day search uses (so the SLOTS result-set mapping applies and national_break_time
    // is selected). Used only by the multi-day re-hydrate path — see
    // rehydrateMultidayCandidatesWithSlotStartTimes. Deliberately NOT the COURT_SCHEDULE_ALL_FIELDS
    // query, which omits national_break_time and is never run through addSlotStartTimes.
    private static final String GET_HEARING_SLOTS_BY_IDS_QUERY = GET_HEARING_SLOTS_QUERY_SELECT_FROM + """
            WHERE cs.active = true
            AND cs.id IN (:courtScheduleIds)
            """ + GET_HEARING_SLOTS_QUERY_GROUP_BY;

    // Lightweight count query used by getCourtSchedules.
    // Mirrors the WHERE predicates of GET_HEARING_SLOTS_QUERY_MANDATORY_PARAMS but:
    //   - drops the LEFT JOIN allocated_listings (no WHERE clause references al.*)
    //   - drops the SUM(CASE WHEN ...) aggregates and the 25-column GROUP BY
    //   - returns a single integer instead of materialising rows
    // The result equals the number of distinct cs rows the paginated query would produce
    // (the paginated query's GROUP BY is on cs.id + functionally-dependent cs.* columns).
    // See performance analysis: "hearingSlots multi-day Crown — performance analysis & fix plan",
    // findings #1 and #2, recommended fix #1.
    private static final String GET_HEARING_SLOTS_COUNT_QUERY_MANDATORY_PARAMS = """
            SELECT COUNT(*)
            FROM court_schedule cs
            WHERE cs.active = true
            AND cs.panel in (:panelType)
            AND cs.session_start BETWEEN :sessionStart AND :sessionEnd
            """;

    // Fix #4 (perf analysis): lightweight discovery projection used by getMultidayHearingSlotCandidates.
    // Same WHERE predicates as the full query, but:
    //   - drops the LEFT JOIN allocated_listings and the SUM(CASE WHEN ...) aggregates,
    //   - drops the 25-column GROUP BY,
    //   - selects only the columns needed for grouping by (room, business type, ou code) and
    //     for the consecutive-business-day check on session_start.
    // The result is used to narrow the candidate set; survivors are then re-hydrated via
    // getCourtSchedulesByIdList, so the expensive aggregation only runs on rows that have a
    // realistic chance of being a multi-day start.
    private static final String GET_HEARING_SLOTS_DISCOVERY_QUERY_MANDATORY_PARAMS = """
            SELECT cs.id, cs.court_room_id, cs.rota_business_type, cs.oucode, cs.session_start
            FROM court_schedule cs
            WHERE cs.active = true
            AND cs.panel in (:panelType)
            AND cs.session_start BETWEEN :sessionStart AND :sessionEnd
            """;

    private static final String NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW = "CourtScheduleEntityMappingForView";
    private static final String NATIVE_QUERY_COURT_SCHEDULE_MAPPING_SLOTS = "CourtScheduleEntityMappingForSlots";
    private static final String NATIVE_QUERY_COURT_SCHEDULE_MAPPING_ALL = "CourtScheduleEntityMappingForAllFields";
    private static final int SLOT_DEFAULT = 1;
    private static final int DEFAULT_DURATION_TO_BE_RETURNED = 20;
    private static final String HEARING_START_TIME = "hearingStartTime";
    private static final String IS_DRAFT = "isDraft";

    @Transactional
    public void saveCourtSchedules(List<CourtSchedule> courtSchedules) {
        List<CourtSchedule> failedSchedules = new ArrayList<>();

        batchInsertCourtSchedules(courtSchedules, failedSchedules);

        if (!failedSchedules.isEmpty()) {
            failedSchedules.forEach(courtSchedule -> update(courtSchedule, false));
        }
    }

    protected void batchInsertCourtSchedules(List<CourtSchedule> courtSchedules, List<CourtSchedule> failedSchedules) {
        if (courtSchedules == null || courtSchedules.isEmpty()) {
            LOGGER.debug("No court schedules to process");
            return;
        }

        final long startTime = System.currentTimeMillis();
        LOGGER.info("Starting optimized batch insert of {} court schedules with batch size {}", courtSchedules.size(), BATCH_SIZE);

        // Use optimized processing for all batch sizes
        processOptimizedBatches(courtSchedules, failedSchedules);

        final long duration = System.currentTimeMillis() - startTime;
        final int processed = courtSchedules.size() - failedSchedules.size();
        final double throughput = processed > 0 ? (processed * 1000.0 / duration) : 0;

        LOGGER.info("Batch insert completed in {}ms. Processed: {}, Failed: {}, Throughput: {} records/sec",
                   duration, processed, failedSchedules.size(), throughput);
    }

    /**
     * Batch-persist records by delegating to {@link uk.gov.moj.cpp.courtscheduler.service.CourtScheduleBatchInsertService}
     * which wraps each batch in {@code Propagation.REQUIRES_NEW}. If a batch trips a
     * unique-index collision, only that inner transaction is rolled back — the caller's
     * outer transaction stays clean and the failed records fall through to the per-record
     * retry path ({@link #saveCourtSchedules} → {@link #update}) which resolves the
     * collision by updating the existing row.
     *
     * <p>Previously the persist+flush happened in the caller's transaction, so any
     * collision marked that transaction rollback-only and Spring threw
     * {@code UnexpectedRollbackException} at commit even when the per-record retry
     * had recovered every record. See {@code SaveCourtSchedulesDuplicateTest} for
     * the reproducer.</p>
     */
    private void processOptimizedBatches(List<CourtSchedule> courtSchedules, List<CourtSchedule> failedSchedules) {
        final int totalRecords = courtSchedules.size();
        final int batchSize = Math.min(BATCH_SIZE, 1000); // Cap batch size for optimal performance
        final int numBatches = (totalRecords + batchSize - 1) / batchSize;

        LOGGER.debug("Processing {} records in {} batches of size {}", totalRecords, numBatches, batchSize);

        List<CourtSchedule> currentBatch = new ArrayList<>(batchSize);
        for (int i = 0; i < totalRecords; i++) {
            currentBatch.add(courtSchedules.get(i));
            if (currentBatch.size() >= batchSize || i == totalRecords - 1) {
                attemptBatchPersist(currentBatch, failedSchedules);
                currentBatch = new ArrayList<>(batchSize);
            }
        }
    }

    /**
     * Try persisting the batch in its own transaction. On any failure (typically a
     * unique-constraint violation) the inner transaction rolls back cleanly and the
     * batch falls through to {@link #processIndividualRecordsFast}, which retries each
     * record independently in its own REQUIRES_NEW transaction — collisions are
     * resolved by updating the existing row, genuine inserts succeed on retry.
     */
    private void attemptBatchPersist(final List<CourtSchedule> batch, final List<CourtSchedule> failedSchedules) {
        try {
            courtScheduleBatchInsertService.persistBatch(batch);
        } catch (Exception e) {
            LOGGER.warn("Batch insert of {} records failed ({}); falling back to per-record retry", batch.size(), e.getMessage());
            processIndividualRecordsFast(batch, failedSchedules);
        }
    }


    /**
     * Fast individual record processing with optimized entity manager usage.
     */
    private void processIndividualRecordsFast(List<CourtSchedule> records, List<CourtSchedule> failedSchedules) {
        LOGGER.debug("Processing {} records individually with fast method", records.size());

        for (CourtSchedule cs : records) {
            try {
                // Each record is handled in its own REQUIRES_NEW transaction via retry service
                courtScheduleRetryService.upsertOne(cs);
            } catch (Exception ex) {
                LOGGER.warn("Failed to upsert individual record {}: {}", cs.getCourtScheduleId(), ex.getMessage());
                failedSchedules.add(cs);
            }
        }
    }


    //update on Create when needed
    @Transactional
    public CourtSchedule update(final CourtSchedule courtSchedule, final boolean isForRotaFile) {

        return courtScheduleRetryService.retryAndSave(courtSchedule, isForRotaFile);
    }

    @Override
    @Transactional
    public Result update(CourtSchedule persistedCourtSchedule,
                         UpdateCourtSchedule updateCourtSchedule,
                         Optional<CourtRoom> courtRoom) {
        //SessionDate and CourtHouseId should not be updated
        persistedCourtSchedule.setCourtRoomId(updateCourtSchedule.getCourtRoomId());
        persistedCourtSchedule.setBusinessType(updateCourtSchedule.getBusinessType());
        persistedCourtSchedule.setCourtSession(updateCourtSchedule.getSessionType());
        persistedCourtSchedule.setPanel(updateCourtSchedule.getPanel());
        persistedCourtSchedule.setMaxSlots(updateCourtSchedule.getMaxSlots());
        persistedCourtSchedule.setAvailableSlots(updateCourtSchedule.getAvailableSlots());
        persistedCourtSchedule.setMaxDuration(updateCourtSchedule.getMaxDuration());
        persistedCourtSchedule.setAvailableDuration(updateCourtSchedule.getAvailableDuration());
        persistedCourtSchedule.setMaxAdMorningDuration(updateCourtSchedule.getMaxDurationForMorning());
        persistedCourtSchedule.setMaxAdAfternoonDuration(updateCourtSchedule.getMaxDurationForAfternoon());
        final DateUtils.SessionStartAndEndTime sessionStartAndEndTime = getOrElseDefaultSessionStartAndEndTimeIfEmpty(updateCourtSchedule.getSessionType(), updateCourtSchedule.getSessionStartTime(), updateCourtSchedule.getSessionEndTime());
        if (StringUtils.isNotEmpty(sessionStartAndEndTime.sessionStartTime()) && StringUtils.isNotEmpty(sessionStartAndEndTime.sessionEndTime())) {
            persistedCourtSchedule.setSessionStartTime(combineDateAndTime(persistedCourtSchedule.getSessionDate(), sessionStartAndEndTime.sessionStartTime()));
            persistedCourtSchedule.setSessionEndTime(combineDateAndTime(persistedCourtSchedule.getSessionDate(), sessionStartAndEndTime.sessionEndTime()));
        }
        persistedCourtSchedule.setNationalBreakTime(persistedCourtSchedule.getNationalBreakTime());
        persistedCourtSchedule.setUpdatedOn(new Date());
        persistedCourtSchedule.setIsOverbookingAllowed(updateCourtSchedule.isOverbookingAllowed());
        persistedCourtSchedule.setJurisdiction(updateCourtSchedule.getJurisdiction());

        if (nonNull(updateCourtSchedule.getIsDraft())) {
            persistedCourtSchedule.setIsDraft(updateCourtSchedule.getIsDraft());
        }

        if (courtRoom.isPresent()) {
            persistedCourtSchedule.setOuCode(courtRoom.get().getOucode());
            persistedCourtSchedule.setCourtRoomName(courtRoom.get().getCourtroomName());
            persistedCourtSchedule.setCourtRoomNumber(courtRoom.get().getCppCourtRoomId());
            persistedCourtSchedule.setCourtHouseName(courtRoom.get().getOucodeL3Name());
            persistedCourtSchedule.setOperationalUnit(courtRoom.get().getOucodeL2Code());
        }

        saveInternal(persistedCourtSchedule);
        return Result.SUCCESS();
    }

    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findBy(CourtScheduleRequestParam courtScheduleRequestParam) {

        final int pageSize = Integer.parseInt(courtScheduleRequestParam.pageSize());
        final int pageNumber = Integer.parseInt(courtScheduleRequestParam.pageNumber());

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        courtScheduleCriteria.getCourtScheduleCriteria(courtScheduleRequestParam, criteriaBuilder, criteriaQuery);
        List<CourtSchedule> resultList = entityManager.createQuery(criteriaQuery)
                .setFirstResult((pageNumber - 1) * pageSize).setMaxResults(pageSize)
                .getResultList();
        return resultList.stream().map(CourtSchedulerConverter::convert).toList();
    }

    public CourtSchedule retrieveCourtScheduleWithListingById(final String courtScheduleId) {
        StringBuilder queryString = new StringBuilder("SELECT distinct s.*, case when al.id is not null then true else false end as hasHearingsBooked FROM court_schedule s left outer join  allocated_listings al on(s.id = al.court_schedule_id)  WHERE active = true AND s.id  = :courtScheduleId");
        final jakarta.persistence.Query query = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
        query.setParameter("courtScheduleId", courtScheduleId);
        final List resultList = query.getResultList();
        return isNotEmpty(resultList) ? (CourtSchedule) resultList.get(0) : null;
    }


    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedulesByIdList(List<String> courtScheduleIds) {
        if (isEmpty(courtScheduleIds)) {
            return new ArrayList<>();
        }
        // Defensive: a caller passing the same id twice would still be safe with the fixed SQL
        // (IN is set-based), but deduping here keeps the wire payload and param binding clean.
        final List<String> dedupedIds = new ArrayList<>(new LinkedHashSet<>(courtScheduleIds));

        final jakarta.persistence.Query query = entityManager.createNativeQuery(COURT_SCHEDULE_ALL_FIELDS_QUERY_STRING.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_ALL);

        Map<String, Object> params = new HashMap<>();
        params.put(COURT_SCHEDULE_IDS, dedupedIds);
        params.forEach(query::setParameter);

        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedulesResult = getCourtSchedulesResult(query);

        enrichWithJudiciary(courtSchedulesResult);
        return courtSchedulesResult;
    }

    @Override
    public void enrichWithJudiciary(final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules) {
        if (isEmpty(courtSchedules)) {
            return;
        }
        final List<String> courtScheduleIds = courtSchedules.stream()
                .map(uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule::getCourtScheduleId)
                .toList();
        final List<CourtScheduleJudiciary> judiciaryList = getCourtScheduleJudiciariesByCourtScheduleIds(courtScheduleIds);
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> domainJudiciaries =
                judiciaryList.stream()
                        .map(CourtScheduleRepositoryImpl::mapJudiciaryEntityToDomain)
                        .toList();
        courtSchedules.forEach(schedule ->
                addJudiciaries(domainJudiciaries, schedule));
    }

    private List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedulesResult(final jakarta.persistence.Query query) {
        final List<CourtSchedule> resultList = query.getResultList();

        final List<String> courtScheduleIdsHavingHearingsBooked = resultList.stream()
                .filter(CourtSchedule::getHasHearingsBooked)
                .map(CourtSchedule::getCourtScheduleId).toList();

        final List<AllocatedListingEachBooked> allocatedListingEachBookedList = isNotEmpty(courtScheduleIdsHavingHearingsBooked) ?
                allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(courtScheduleIdsHavingHearingsBooked).stream().toList() : Collections.emptyList();

        return resultList.stream().map(courtSchedule -> CourtSchedulerConverter.convert(courtSchedule, allocatedListingEachBookedList)).toList();
    }

    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedulesBy(final CourtScheduleRequestParam courtScheduleRequestParam) {
        StringBuilder queryString = new StringBuilder("SELECT distinct s.*, case when al.id is not null then true else false end as hasHearingsBooked FROM court_schedule s left outer join  allocated_listings al on(s.id = al.court_schedule_id)  WHERE active = true ");
        Map<String, Object> params = new HashMap<>();
        addFiltersForGetCourtSchedulesBy(courtScheduleRequestParam, queryString, params);
        final jakarta.persistence.Query query = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
        params.forEach((key, value) -> {
            if (value != null) {
                query.setParameter(key, value);
            }
        });
        return getCourtSchedulesResult(query);
    }

    private static void addFiltersForGetCourtSchedulesBy(final CourtScheduleRequestParam courtScheduleRequestParam, final StringBuilder queryString, final Map<String, Object> params) {
        if (courtScheduleRequestParam.courtCentreId() != null) {
            queryString.append("AND s.court_house_id = :courtHouseId ");
            params.put("courtHouseId", courtScheduleRequestParam.courtCentreId());
        }
        if (StringUtils.isNotBlank(courtScheduleRequestParam.courtRoomId())) {
            queryString.append(COURT_ROOM_ID_QUERY_CONDITION_STRING);
            params.put(COURT_ROOM_ID, courtScheduleRequestParam.courtRoomId());
        }
        if (courtScheduleRequestParam.businessType() != null) {
            queryString.append("AND s.rota_business_type = :businessType ");
            params.put(BUSINESS_TYPE, courtScheduleRequestParam.businessType());
        }
        if (courtScheduleRequestParam.sessionStartDate() != null) {
            queryString.append("AND s.session_start >= :sessionStartDate ");
            params.put("sessionStartDate", LocalDate.parse(courtScheduleRequestParam.sessionStartDate()));
        }
        if (courtScheduleRequestParam.sessionEndDate() != null) {
            queryString.append("AND s.session_start <= :sessionEndDate ");
            params.put("sessionEndDate", LocalDate.parse(courtScheduleRequestParam.sessionEndDate()));
        }
        if (courtScheduleRequestParam.isDraft() != null) {
            queryString.append("AND s.is_draft = :isDraft ");
            params.put(IS_DRAFT, courtScheduleRequestParam.isDraft());
        }
        queryString.append("group by s.id, al.id, s.court_room_number order by session_start ");
        if (courtScheduleRequestParam.pageSize() != null) {
            queryString.append("LIMIT :pageSize ");
            params.put("pageSize", new BigInteger(courtScheduleRequestParam.pageSize()));
        }
        if (courtScheduleRequestParam.pageNumber() != null) {
            queryString.append("OFFSET :pageNumber ");
            params.put("pageNumber", Integer.parseInt(courtScheduleRequestParam.pageNumber()) - 1);
        }
    }

    @Override
    public List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria) {
        final List<CourtSchedule> courtScheduleList = entityManager.createQuery(
                        "SELECT cs FROM CourtSchedule cs WHERE cs.updatedOn > :fromDate AND cs.updatedOn < :toDate",
                        CourtSchedule.class)
                .setParameter("fromDate", DateUtils.getDate(miFilterCriteria.getFromLocalDate()))
                .setParameter("toDate", DateUtils.getDate(miFilterCriteria.getToLocalDate()))
                .getResultList();
        return courtScheduleList.stream().map(CourtSchedulerConverter::convertToMi).toList();
    }

    @Transactional
    public Result saveBookedSlots(final List<AllocatedSlot> slots, final boolean isProvisionalSlot, final boolean isSearchUpdate) {
        return saveBookedSlots(slots, isProvisionalSlot, isSearchUpdate, true);
    }

    /**
     * @param releaseExistingHearingAllocations when {@code false} (and {@code isSearchUpdate} is
     *                                          {@code false}), skips {@code bookSlotsWithCourtScheduleId}'s
     *                                          internal hearing-wide {@link #releaseOldAllocatedListings(String)}
     *                                          call. Callers that have already released only a date-scoped
     *                                          subset of a hearing's allocations (e.g. {@code
     *                                          releaseAllocatedListingsForDates}) must pass {@code false} here,
     *                                          otherwise the hearing-wide release wipes out the untouched days'
     *                                          allocations before booking.
     */
    @Transactional
    public Result saveBookedSlots(final List<AllocatedSlot> slots, final boolean isProvisionalSlot, final boolean isSearchUpdate,
                                  final boolean releaseExistingHearingAllocations) {
        if (isSearchUpdate) {
            return bookSlotsWithoutCourtScheduleId(slots, isProvisionalSlot);
        } else {
            return bookSlotsWithCourtScheduleId(slots, isProvisionalSlot, releaseExistingHearingAllocations);
        }
    }

    @Override
    public Optional<AllocatedListing> findAllocatedListingByHearingId(final String hearingId) {
        final List<AllocatedListing> existing = allocatedListingRepository.findByHearingId(hearingId);
        return existing.stream().findFirst();
    }


    /**
     * Crown-only fallback search. Matches strictly on ouCode + sessionDate; relaxes businessType,
     * court_session, and (optionally) courtRoomId.
     *
     * Draft preference:
     *   - courtRoomId supplied    -> try non-draft at that room first, then draft at that room
     *   - courtRoomId not supplied -> try draft sessions (unallocated intent) at the centre
     *
     * Each tier runs twice: once with strict availability, once with overbooking relaxation.
     *
     * @param earliestHearingTime Reserved for future refinement (prefer sessions covering this time).
     *                            Currently ignored because the existing court_schedule rows don't model
     *                            "start time covers X"; any session on the target date is acceptable.
     */
    @Override
    public Optional<CrownFallbackSearchResult> searchCrownFallbackSlots(
            final String courtCentreId,
            final LocalDate hearingDate,
            final int durationInMinutes,
            final String courtRoomId,
            final String earliestHearingTime) {

        final boolean hasCourtRoomId = courtRoomId != null && !courtRoomId.isBlank();
        final boolean[] draftPreferenceOrder = hasCourtRoomId
                ? new boolean[]{false, true}
                : new boolean[]{true};

        for (final boolean overbookingAllowed : new boolean[]{false, true}) {
            for (final boolean isDraft : draftPreferenceOrder) {
                final Optional<CourtSchedule> candidate = findCrownFallbackCandidate(
                        courtCentreId, hearingDate, durationInMinutes,
                        courtRoomId, isDraft, overbookingAllowed);
                if (candidate.isPresent()) {
                    // Build domain CourtSchedule with the exact fields downstream needs for the
                    // response (sessionDate, sessionStartTime, sessionEndTime, businessType, isDraft,
                    // ouCode, courtRoomId). convertForOverbooking() leaves sessionDate null, which
                    // trips a NullPointerException when SlotsUpdateService renders the response.
                    final CourtSchedule entity = candidate.get();
                    final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule domain =
                            uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.CourtScheduleBuilder.courtSchedule()
                                    .withCourtScheduleId(entity.getCourtScheduleId())
                                    .withOuCode(entity.getOuCode())
                                    .withCourtRoomId(entity.getCourtRoomId())
                                    .withCourtHouseId(entity.getCourtHouseId())
                                    .withBusinessType(entity.getBusinessType())
                                    .withCourtSession(entity.getCourtSession())
                                    .withSessionDate(entity.getSessionDate())
                                    .withIsDraft(entity.getIsDraft())
                                    .withSessionStartTime(entity.getSessionStartTime())
                                    .withSessionEndTime(entity.getSessionEndTime())
                                    .build();
                    return Optional.of(new CrownFallbackSearchResult(domain, overbookingAllowed));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<CourtSchedule> findCrownFallbackCandidate(
            final String courtCentreId,
            final LocalDate hearingDate,
            final int durationInMinutes,
            final String courtRoomId,
            final boolean isDraft,
            final boolean allowOverbooking) {

        // Filter on court_schedule.court_house_id (same UUID as courtCentreId, per the
        // domain CourtSchedule.courtHouseId comment). This lets listing callers supply a
        // single canonical id (courtCentreId UUID) instead of the historical ouCode+courtCentreId pair.
        final StringBuilder sql = new StringBuilder(
                "SELECT s.id FROM court_schedule s "
                        + "WHERE s.active = true "
                        + "  AND s.court_house_id = :courtCentreId "
                        + "  AND DATE(s.session_start) = :hearingDate "
                        + "  AND s.is_draft = :isDraft ");

        if (courtRoomId != null && !courtRoomId.isBlank()) {
            sql.append(" AND s.court_room_id = :courtRoomId ");
        }

        if (!allowOverbooking) {
            sql.append(" AND COALESCE(s.available_duration_mins, 0) >= :durationInMinutes ");
        } else {
            sql.append(" AND s.is_overbooking_allowed = true ");
        }

        sql.append(" ORDER BY s.session_start ASC ");

        final jakarta.persistence.Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter(COURT_CENTRE_ID, courtCentreId);
        query.setParameter("hearingDate", java.sql.Date.valueOf(hearingDate));
        query.setParameter(IS_DRAFT, isDraft);
        if (courtRoomId != null && !courtRoomId.isBlank()) {
            query.setParameter(COURT_ROOM_ID, courtRoomId);
        }
        if (!allowOverbooking) {
            query.setParameter("durationInMinutes", durationInMinutes);
        }
        query.setMaxResults(1);

        @SuppressWarnings("unchecked")
        final List<String> ids = query.getResultList();
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return ofNullable(entityManager.find(CourtSchedule.class, ids.get(0)));
    }

    @Transactional
    public boolean searchBookHearingSlots(final List<AllocatedSlot> slots) {
        boolean isSearchSuccessful = false;

        slots.forEach(slot -> releaseOldAllocatedListings(slot.getHearingId()));

        final List<AllocatedSlot> updateAllocatedSlots = searchBookSlots(slots);

        if (isNotEmpty(updateAllocatedSlots)) {
            LOGGER.info("bookSlotsWithoutCourtScheduleId updateAllocatedSlots {}", updateAllocatedSlots);
            persistHearingSlots(slots, false, updateAllocatedSlots);
            slots.clear();
            slots.addAll(updateAllocatedSlots.stream().toList());
            isSearchSuccessful = true;
        }
        return isSearchSuccessful;
    }

    private Result bookSlotsWithCourtScheduleId(final List<AllocatedSlot> slots, final boolean isProvisionalSlot,
                                                final boolean releaseExistingHearingAllocations) {
        final Optional<String> hearingId = getHearingId(slots);
        if (releaseExistingHearingAllocations) {
            hearingId.ifPresent(this::releaseOldAllocatedListings);
        }

        final List<AllocatedSlot> updateAllocatedSlots = getUpdatedAllocatedSlots(slots, false);

        if (isNotEmpty(updateAllocatedSlots)) {
            persistHearingSlots(slots, isProvisionalSlot, updateAllocatedSlots);
            final Result success = Result.SUCCESS();
            updateAllocatedSlots.forEach(slot -> success.addHearingDaySchedule(slot.getSessionDate(), slot.getCourtScheduleId()));
            return success;
        } else {
            return Result.FAILED(format("courtScheduleId matching for non-provisional slot(s) has been failed,please check the logs. hearingId : %s", slots.get(0).getHearingId()));
        }
    }

    private Result bookSlotsWithoutCourtScheduleId(final List<AllocatedSlot> slots, final boolean isProvisionalSlot) {
        final Optional<String> hearingId = getHearingId(slots);
        hearingId.ifPresent(this::releaseOldAllocatedListings);

        final List<AllocatedSlot> updateAllocatedSlots = getUpdatedAllocatedSlots(slots, true);

        if (isNotEmpty(updateAllocatedSlots)) {
            LOGGER.info("bookSlotsWithoutCourtScheduleId updateAllocatedSlots {}", updateAllocatedSlots);
            persistHearingSlots(slots, isProvisionalSlot, updateAllocatedSlots);
            slots.clear();
            slots.addAll(updateAllocatedSlots.stream().toList());
        } else {
            return Result.FAILED("Not able to allocate hearing slots");
        }

        final Result success = Result.SUCCESS();
        updateAllocatedSlots.forEach(slot -> success.addHearingDaySchedule(slot.getSessionDate(), slot.getCourtScheduleId()));
        return success;
    }

    private void persistHearingSlots(final List<AllocatedSlot> slots, final boolean isProvisionalSlot, final List<AllocatedSlot> updateAllocatedSlots) {
        updateCourtSchedule(updateAllocatedSlots);
        saveAllocatedListing(updateAllocatedSlots);
        if (isProvisionalSlot) {
            deleteProvisionalBooking(slots.get(0).getBookingId());
        }
    }

    @Override
    public Optional<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findCourtScheduleById(final String courtScheduleId) {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule entity = entityManager.find(CourtSchedule.class, courtScheduleId);
        return ofNullable(entity)
                .filter(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule::isActive)
                .map(CourtSchedulerConverter::convertForOverbooking);
    }

    @Override
    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findConsecutiveSessions(
            final String anchorCourtScheduleId, final int daysNeeded) {
        final CourtSchedule anchor = entityManager.find(CourtSchedule.class, anchorCourtScheduleId);
        if (anchor == null || !anchor.isActive()) {
            LOGGER.info("findConsecutiveSessions: anchor {} not found or inactive", anchorCourtScheduleId);
            return Collections.emptyList();
        }

        final LocalDate startDate = anchor.getSessionDate();
        final int weekendBuffer = 2 * ((daysNeeded / 5) + 1);
        final LocalDate endDateExclusive = startDate.plusDays(daysNeeded + weekendBuffer);

        return queryAdWeekdaySessions(
                anchor.getOuCode(), anchor.getCourtRoomId(), anchor.getBusinessType(),
                startDate, endDateExclusive.minusDays(1), anchor.getIsDraft());
    }

    // ─── SPRDT-1089: no-anchor consecutive multi-day search (Phase 1) ─────────

    /**
     * CROWN no-anchor consecutive search (SPRDT-1089, AC3). Finds a single room in the court centre
     * (filtered on {@code court_house_id}, the canonical court-centre id — same mapping as
     * {@link #findCrownFallbackCandidate}) that has {@code daysNeeded} consecutive AD weekday sessions
     * of one business type starting from {@code fromDate}, skipping weekends. Returns the run for the
     * first qualifying (room, business type), or empty if none qualifies.
     *
     * <p>Mirrors {@link #queryAdWeekdaySessions}: {@code active = true}, {@code court_session = 'AD'},
     * {@code EXTRACT(DOW) NOT IN (0,6)}. Consecutiveness across weekends is asserted by the caller
     * ({@code areConsecutiveBusinessDays}); here we provide a generous window and let the caller trim.</p>
     */
    @Override
    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findConsecutiveSessionsForCentre(
            final String courtCentreId, final LocalDate fromDate, final int daysNeeded) {
        // Candidate (room, business type) pairs in the centre that have a session on the start date,
        // ordered so the earliest/most-populated room is tried first.
        final String roomQuery = """
                SELECT s.court_room_id, s.rota_business_type
                FROM court_schedule s
                WHERE s.active = true
                  AND s.court_house_id = :courtCentreId
                  AND s.court_session = 'AD'
                  AND EXTRACT(DOW FROM s.session_start) NOT IN (0, 6)
                  AND DATE(s.session_start) = :fromDate
                GROUP BY s.court_room_id, s.rota_business_type
                ORDER BY s.court_room_id, s.rota_business_type
                """;
        final jakarta.persistence.Query roomJpaQuery = entityManager.createNativeQuery(roomQuery);
        roomJpaQuery.setParameter(COURT_CENTRE_ID, courtCentreId);
        roomJpaQuery.setParameter("fromDate", java.sql.Date.valueOf(fromDate));

        @SuppressWarnings("unchecked")
        final List<Object[]> rooms = roomJpaQuery.getResultList();
        if (isEmpty(rooms)) {
            return Collections.emptyList();
        }

        // Generous calendar window: daysNeeded business days plus enough buffer for the weekends spanned.
        final int weekendBuffer = 2 * ((daysNeeded / 5) + 1);
        final LocalDate toInclusive = fromDate.plusDays((long) daysNeeded + weekendBuffer);

        for (final Object[] room : rooms) {
            final String courtRoomId = (String) room[0];
            final String businessType = (String) room[1];
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> run = queryAdWeekdaySessionsInCentre(
                    courtCentreId, courtRoomId, businessType, fromDate, toInclusive);
            if (run.size() >= daysNeeded) {
                return run;
            }
        }
        return Collections.emptyList();
    }

    private List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> queryAdWeekdaySessionsInCentre(
            final String courtCentreId, final String courtRoomId, final String businessType,
            final LocalDate fromInclusive, final LocalDate toInclusive) {
        final String queryStr = """
                SELECT s.id
                FROM court_schedule s
                WHERE s.active = true
                  AND s.court_house_id = :courtCentreId
                  AND s.court_room_id = :courtRoomId
                  AND s.rota_business_type = :businessType
                  AND s.court_session = 'AD'
                  AND EXTRACT(DOW FROM s.session_start) NOT IN (0, 6)
                  AND s.session_start >= :startDate
                  AND s.session_start <= :endDate
                ORDER BY s.session_start
                """;
        final jakarta.persistence.Query query = entityManager.createNativeQuery(queryStr);
        query.setParameter(COURT_CENTRE_ID, courtCentreId);
        query.setParameter(COURT_ROOM_ID, courtRoomId);
        query.setParameter(BUSINESS_TYPE, businessType);
        query.setParameter(START_DATE, java.sql.Date.valueOf(fromInclusive));
        query.setParameter(END_DATE, java.sql.Date.valueOf(toInclusive));

        @SuppressWarnings("unchecked")
        final List<String> ids = query.getResultList();
        if (isEmpty(ids)) {
            return Collections.emptyList();
        }
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions =
                new ArrayList<>(getCourtSchedulesByIdList(ids));
        sessions.sort(java.util.Comparator.comparing(
                uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule::getSessionDate));
        return sessions;
    }

    @Override
    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findAdSessionsInRange(
            final String ouCode, final String courtRoomId, final String businessType,
            final LocalDate fromInclusive, final LocalDate toInclusive) {
        return queryAdWeekdaySessions(ouCode, courtRoomId, businessType, fromInclusive, toInclusive, null);
    }

    private List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> queryAdWeekdaySessions(
            final String ouCode, final String courtRoomId, final String businessType,
            final LocalDate fromInclusive, final LocalDate toInclusive, final Boolean isDraft) {
        final StringBuilder queryStr = new StringBuilder("""
                SELECT s.id
                FROM court_schedule s
                WHERE s.active = true
                  AND s.oucode = :ouCode
                  AND s.court_room_id = :courtRoomId
                  AND s.rota_business_type = :businessType
                  AND s.court_session = 'AD'
                  AND EXTRACT(DOW FROM s.session_start) NOT IN (0, 6)
                  AND s.session_start >= :startDate
                  AND s.session_start <= :endDate
                """);
        // CROWN anchor consecutive: all days must share the anchor's draft state (isDraft non-null).
        // The extend path passes null to leave draft state unconstrained.
        if (isDraft != null) {
            queryStr.append("  AND s.is_draft = :isDraft\n");
        }
        queryStr.append("ORDER BY s.session_start");
        final jakarta.persistence.Query query = entityManager.createNativeQuery(queryStr.toString());
        query.setParameter(OU_CODE, ouCode);
        query.setParameter(COURT_ROOM_ID, courtRoomId);
        query.setParameter(BUSINESS_TYPE, businessType);
        query.setParameter(START_DATE, java.sql.Date.valueOf(fromInclusive));
        query.setParameter(END_DATE, java.sql.Date.valueOf(toInclusive));
        if (isDraft != null) {
            query.setParameter(IS_DRAFT, isDraft);
        }

        @SuppressWarnings("unchecked")
        final List<String> ids = query.getResultList();
        if (isEmpty(ids)) {
            return Collections.emptyList();
        }
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions =
                new ArrayList<>(getCourtSchedulesByIdList(ids));
        sessions.sort(java.util.Comparator.comparing(
                uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule::getSessionDate));
        return sessions;
    }

    @Transactional
    public List<Hearing> updateListHearingSlots(final RequestedSlots slots) {

        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing> allocatedListings = new ArrayList<>();
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules = new ArrayList<>();

         List<Hearing> hearings = flattenHearingSlots(slots);

         hearings.forEach(hearing -> {
             uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule cs = entityManager.find(CourtSchedule.class, hearing.getCourtScheduleId());
            if (cs != null) {
                //check slot based /duration based for deducting the available slots
                if (cs.isSlotBased()) {
                    cs.setAvailableSlots(cs.getAvailableSlots() - 1);
                } else cs.setAvailableDuration(cs.getAvailableDuration() - hearing.getDuration());

                // F3 (capacity drift): release via the RESTORING path. The old call
                // (releaseOldListingsFromAllocatedListings) deleted the hearing's allocated_listings
                // rows WITHOUT paying their minutes/slots back into court_schedule, while the deduction
                // above charged the session again — so available_duration_mins leaked the hearing's full
                // duration on every book→list cycle and sessions falsely reported no availability.
                boolean isCourtScheduleReleased = !allocatedListingRepository.findByHearingId(hearing.getHearingId()).isEmpty();
                if (isCourtScheduleReleased) {
                    releaseOldAllocatedListings(hearing.getHearingId());
                }

                final boolean isOverbookingAllowed = findCourtScheduleById(hearing.getCourtScheduleId()).stream().findFirst()
                        .map(uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule::isOverbookingAllowed)
                        .orElse(false);

                String hearingSource = resolveAllocatedListingSource(hearing.getSource(), isCourtScheduleReleased, isOverbookingAllowed);
                //prepare allocated listing
                uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing allocatedlisting = new AllocatedListing();
                allocatedlisting.setHearingId(hearing.getHearingId());
                allocatedlisting.setCourtScheduleId(hearing.getCourtScheduleId());
                allocatedlisting.setCourtRoomId(cs.getCourtRoomNumber());
                allocatedlisting.setOucode(cs.getOuCode());
                allocatedlisting.setRotaBusinessType(cs.getBusinessType());
                allocatedlisting.setId(UUID.randomUUID().toString());
                allocatedlisting.setHearingStartTime(getAdjustedHearingStartTime(hearing.getHearingStartTime(), cs));
                allocatedlisting.setDuration(getAdjustedDuration(hearing, cs));
                allocatedlisting.setSource(hearingSource);
                courtSchedules.add(cs);
                allocatedListings.add(allocatedlisting);
                hearing.setHearingStartTime(DateUtils.toResponseDateString(getAdjustedHearingStartTime(hearing.getHearingStartTime(), cs)));
                //duration should remain as requested or defaulted to 20.but we still persist 1 for slot based in allocated_listings
                hearing.setDuration(getAdjustedDurationToBeReturned(hearing));
            }
         });

        List<CourtScheduleJudiciary> judiciaryList = getCourtScheduleJudiciaries(courtSchedules);
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> domainJudiciaries =
                judiciaryList.stream()
                        .map(CourtScheduleRepositoryImpl::mapJudiciaryEntityToDomain)
                        .toList();

        hearings.forEach(hearing ->
                addJudiciaries(domainJudiciaries, hearing));

        updateCourtScheduleWithRequestedList(courtSchedules);
        saveAllocatedListingWithRequestedList(allocatedListings);

        return hearings;
    }

    private static int getAdjustedDuration(final Hearing hearing, final CourtSchedule cs) {
        return cs.isSlotBased() ? SLOT_DEFAULT : hearing.getDuration();
    }

    private static int getAdjustedDurationToBeReturned(final Hearing hearing) {
        return nonNull(hearing.getDuration()) ? hearing.getDuration() : DEFAULT_DURATION_TO_BE_RETURNED;
    }

    private void updateCourtScheduleWithRequestedList(List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> slots) {
        slots.forEach(this::saveInternal);

    }

    private void saveAllocatedListingWithRequestedList(List<AllocatedListing> slots) {
        slots.forEach(
                allocatedListing -> {
                    allocatedListing.setHearingStartTime(
                            DateUtils.toExactTimestamp(toIsoStringExtended(allocatedListing.getHearingStartTime())));
                    this.allocatedListingRepository.save(allocatedListing);
                }
        );
    }

    @SuppressWarnings("unchecked")
    public Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> getCourtSchedules(final HearingSlotRequestParam requestParam) {
        Map<String, Object> queryParamsForCount = buildQueryParams(requestParam, true);
        Map<String, Object> queryParamsForResult = buildQueryParams(requestParam, false);

        // Fix #1 (perf analysis): cheap count query instead of materialising every matching row
        // just to call .size(). The previous implementation ran the heavy SELECT (LEFT JOIN
        // allocated_listings + 3x SUM(CASE WHEN) + 25-column GROUP BY) twice per request — once
        // unpaginated to count, once paginated for the actual page. This single COUNT(*) query
        // is on court_schedule alone (no join, no aggregates, no GROUP BY) and produces the same
        // total because every WHERE predicate references cs.* only.
        String countQuery = buildCountQuery(requestParam);
        jakarta.persistence.Query countJpaQuery = entityManager.createNativeQuery(countQuery);
        queryParamsForCount.forEach(countJpaQuery::setParameter);
        int totalCount = ((Number) countJpaQuery.getSingleResult()).intValue();
        LOGGER.debug("getCourtSchedules totalCount: {}", totalCount);

        // Paginated results
        String paginatedQuery = buildFullQuery(requestParam);
        List<CourtSchedule> paginatedSchedules = executeQuery(paginatedQuery, queryParamsForResult);
        // Fix #2 (perf analysis): do not serialise the full entity list at INFO. Log the row
        // count only; if a full dump is ever needed, raise the logger to DEBUG.
        LOGGER.debug("getCourtSchedules paginated row count: {}", paginatedSchedules.size());

        if (paginatedSchedules.isEmpty()) {
            return Pair.of(0, Collections.emptyList());
        }

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> domainSchedules =
                processScheduleEntities(paginatedSchedules);

        return Pair.of(totalCount, domainSchedules);
    }

    /**
     * Fix #4 (perf analysis) — discovery + re-hydrate path for multi-day Crown searches.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Run a lightweight projection over <code>court_schedule</code> (no
     *       <code>LEFT JOIN allocated_listings</code>, no aggregates, no GROUP BY) to enumerate
     *       sessions matching the request's predicates and date window.</li>
     *   <li>Group the rows by (court_room_id, rota_business_type, oucode) and keep only the
     *       rooms where at least one window of <code>daysNeeded</code> consecutive business-day
     *       sessions exists.</li>
     *   <li>Collect the ids of <em>all</em> sessions in the surviving rooms (the look-ahead days
     *       are needed by the capacity check that runs downstream in the service) and re-hydrate
     *       them via {@link #getCourtSchedulesByIdList(List)}, which performs the expensive
     *       aggregation only on this much smaller set.</li>
     * </ol>
     *
     * <p>This is an over-approximation: a room can be returned as a candidate even if no
     * concrete start date has sufficient available capacity. The downstream filter in
     * {@code SlotsSearchService.filterForMultidayAvailability} resolves that with the now-
     * available aggregated booking data.
     *
     * @param requestParam the original (already-window-extended) request
     * @param daysNeeded   the number of consecutive business days the hearing requires
     * @return the rehydrated candidate schedules; empty if no room qualifies
     */
    @Override
    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getMultidayHearingSlotCandidates(
            final HearingSlotRequestParam requestParam, final int daysNeeded) {

        // 1. Cheap discovery query
        final String discoverySql = buildDiscoveryQuery(requestParam);
        final jakarta.persistence.Query discoveryQuery = entityManager.createNativeQuery(discoverySql);
        buildQueryParams(requestParam, true).forEach(discoveryQuery::setParameter);

        @SuppressWarnings("unchecked")
        final List<Object[]> rows = discoveryQuery.getResultList();

        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Group by (room, businessType, oucode) → distinct session dates and matching ids
        final Map<String, Set<LocalDate>> datesByRoom = new HashMap<>();
        final Map<String, List<String>> idsByRoom = new HashMap<>();
        for (final Object[] row : rows) {
            final String id = (String) row[0];
            final String roomId = (String) row[1];
            final String businessType = (String) row[2];
            final String oucode = (String) row[3];
            final LocalDate sessionDate = toLocalDate(row[4]);
            if (id == null || roomId == null || sessionDate == null) {
                continue;
            }
            final String key = roomId + "|" + businessType + "|" + oucode;
            datesByRoom.computeIfAbsent(key, k -> new TreeSet<>()).add(sessionDate);
            idsByRoom.computeIfAbsent(key, k -> new ArrayList<>()).add(id);
        }

        // 3. Surviving rooms: those with at least one daysNeeded-long business-day run
        final List<String> candidateIds = new ArrayList<>();
        for (final Map.Entry<String, Set<LocalDate>> entry : datesByRoom.entrySet()) {
            if (hasConsecutiveBusinessDays(entry.getValue(), daysNeeded)) {
                candidateIds.addAll(idsByRoom.get(entry.getKey()));
            }
        }

        LOGGER.debug("getMultidayHearingSlotCandidates discoveryRows={}, totalRooms={}, candidateIds={}",
                rows.size(), datesByRoom.size(), candidateIds.size());

        if (candidateIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. Re-hydrate the survivors — this is the only place the heavy SQL runs
        return rehydrateMultidayCandidatesWithSlotStartTimes(candidateIds);
    }

    /**
     * Re-hydrate the discovered multi-day candidate ids through the same SLOTS query +
     * {@link #processScheduleEntities(List)} pipeline the single-day hearing-slots search uses,
     * so the response carries the hourly {@code slotStartTimes} breakdown. The discovery step in
     * {@link #getMultidayHearingSlotCandidates} preserves the perf win by running this heavy
     * aggregation only on the narrowed candidate set.
     *
     * <p>This must NOT delegate to {@link #getCourtSchedulesByIdList(List)}: that path runs the
     * {@code COURT_SCHEDULE_ALL_FIELDS} query (which does not select {@code national_break_time})
     * and never calls {@code addSlotStartTimes}. SPRDT-903 perf-fix #4 switched the multi-day
     * re-hydrate to it, which is what silently dropped {@code slotStartTimes} from duration-based
     * multi-day Crown searches while single-day searches kept it.
     */
    private List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> rehydrateMultidayCandidatesWithSlotStartTimes(final List<String> courtScheduleIds) {
        if (isEmpty(courtScheduleIds)) {
            return Collections.emptyList();
        }
        final List<String> dedupedIds = new ArrayList<>(new LinkedHashSet<>(courtScheduleIds));

        final List<CourtSchedule> entities = new ArrayList<>();
        for (int i = 0; i < dedupedIds.size(); i += ID_QUERY_BATCH_SIZE) {
            final List<String> batch = dedupedIds.subList(i, Math.min(i + ID_QUERY_BATCH_SIZE, dedupedIds.size()));
            final Map<String, Object> params = new HashMap<>();
            params.put(COURT_SCHEDULE_IDS, batch);
            entities.addAll(executeQuery(GET_HEARING_SLOTS_BY_IDS_QUERY, params));
        }

        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        return processScheduleEntities(entities);
    }

    private static LocalDate toLocalDate(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        // Defensive fallback: most JDBC drivers return java.sql.Date for DATE columns, but
        // Hibernate's TemporalType handling can vary. java.sql.Date.toString() format is ISO.
        return LocalDate.parse(value.toString());
    }

    private static boolean hasConsecutiveBusinessDays(final Set<LocalDate> dates, final int daysNeeded) {
        if (daysNeeded <= 0) {
            return true;
        }
        for (final LocalDate start : dates) {
            LocalDate current = start;
            boolean valid = true;
            for (int day = 0; day < daysNeeded; day++) {
                if (day > 0) {
                    current = nextBusinessDay(current);
                }
                if (!dates.contains(current)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate nextBusinessDay(final LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }

    private String resolveAllocatedListingSource(final String rawHearingSource,
                                                 final boolean isCourtScheduleReleased,
                                                 final boolean isOverbookingAllowed) {
        String source = isNotEmpty(rawHearingSource) ? rawHearingSource : "DEFAULT";

        if (isCourtScheduleReleased) {
            return "MOVE";
        }

        if (isOverbookingAllowed) {
            return "ALLOWED";
        }
        return source;
    }

    private Map<String, Object> buildQueryParams(HearingSlotRequestParam requestParam, boolean isCountQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("panelType", List.of(requestParam.panel().split(",")));
        params.put("sessionStart", LocalDate.parse(requestParam.sessionStartDate()));
        params.put("sessionEnd", LocalDate.parse(requestParam.sessionEndDate()));

        if (StringUtils.isNotBlank(requestParam.ouCode())) {
            params.put(OU_CODE, requestParam.ouCode());
        }

        if (StringUtils.isNotBlank(requestParam.oucodeL2Code())) {
            params.put("operationalUnit", requestParam.oucodeL2Code());
        }

        // Add optional parameters only if they are present
        if (StringUtils.isNotBlank(requestParam.courtRoomId())) {
            params.put(COURT_ROOM_ID, requestParam.courtRoomId());
        }

        if (StringUtils.isNotBlank(requestParam.businessType())) {
            params.put(BUSINESS_TYPE, requestParam.businessType());
        } else {
            if (isNotEmpty(requestParam.isSlotBased())) {
                params.put("slotBased", requestParam.isSlotBased());
            }
        }

        if (StringUtils.isNotBlank(requestParam.courtSession())) {
            params.put("courtSession", List.of(requestParam.courtSession().split(",")));
        }

        if (StringUtils.isNotBlank(requestParam.hearingStartTime())) {
            params.put(HEARING_START_TIME,ZonedDateTime.parse(requestParam.hearingStartTime()));
        }

        if (StringUtils.isNotBlank(requestParam.jurisdiction())) {
            params.put("jurisdiction", requestParam.jurisdiction());
        }

        if (!isCountQuery) {
            // Add pagination parameters
            int pageSize = Integer.parseInt(requestParam.pageSize());
            int pageNumber = Integer.parseInt(requestParam.pageNumber());
            params.put("pageSize", pageSize);
            params.put("offset", (pageNumber - 1) * pageSize);
        }

        return params;
    }

    private String buildFullQuery(HearingSlotRequestParam requestParam) {
        StringBuilder query = new StringBuilder(GET_HEARING_SLOTS_QUERY_MANDATORY_PARAMS);
        appendOptionalPredicates(query, requestParam);
        query.append(GET_HEARING_SLOTS_QUERY_GROUP_BY)
                .append(GET_HEARING_SLOTS_QUERY_PAGINATION);

        return query.toString();
    }

    // Fix #1 (perf analysis): companion to buildFullQuery that produces a cheap COUNT(*) query
    // sharing the same predicate set, so totalCount cannot drift away from the paginated query.
    private String buildCountQuery(HearingSlotRequestParam requestParam) {
        StringBuilder query = new StringBuilder(GET_HEARING_SLOTS_COUNT_QUERY_MANDATORY_PARAMS);
        appendOptionalPredicates(query, requestParam);
        return query.toString();
    }

    // Fix #4 (perf analysis): companion to buildFullQuery that produces the slim discovery
    // projection. Shares the same optional-predicate appender so the discovery and the
    // re-hydrated set are guaranteed to come from the same WHERE clause.
    private String buildDiscoveryQuery(HearingSlotRequestParam requestParam) {
        StringBuilder query = new StringBuilder(GET_HEARING_SLOTS_DISCOVERY_QUERY_MANDATORY_PARAMS);
        appendOptionalPredicates(query, requestParam);
        return query.toString();
    }

    private void appendOptionalPredicates(StringBuilder query, HearingSlotRequestParam requestParam) {
        if (StringUtils.isNotBlank(requestParam.ouCode())) {
            query.append(" AND cs.oucode = :ouCode");
        }

        if (StringUtils.isNotBlank(requestParam.oucodeL2Code())) {
            query.append(" AND cs.operational_unit = :operationalUnit");
        }

        if (StringUtils.isNotBlank(requestParam.courtRoomId())) {
            query.append(" AND cs.court_room_id = :courtRoomId");
        }

        if (StringUtils.isNotBlank(requestParam.businessType())) {
            query.append(" AND cs.rota_business_type = :businessType");
        } else {
            if (isNotEmpty(requestParam.isSlotBased())) {
                query.append(" AND cs.is_slot_based = :slotBased");
            }
        }

        if (StringUtils.isNotBlank(requestParam.courtSession())) {
            query.append(" AND cs.court_session in (:courtSession)");
        }

        if (StringUtils.isNotBlank(requestParam.hearingStartTime())) {
            query.append(" AND (:hearingStartTime) between cs.session_start_time AND cs.session_end_time ");
        }

        if (StringUtils.isNotBlank(requestParam.status())) {
            if ("DRAFT".equalsIgnoreCase(requestParam.status())) {
                query.append(" AND cs.is_draft = true");
            } else if ("FINAL".equalsIgnoreCase(requestParam.status())) {
                query.append(" AND cs.is_draft = false");
            }
            // "ALL" or any other value means no is_draft filter
        }

        if (StringUtils.isNotBlank(requestParam.jurisdiction())) {
            query.append(" AND cs.jurisdiction = :jurisdiction");
        }
    }

    private List<CourtSchedule> executeQuery(String query, Map<String, Object> params) {
        jakarta.persistence.Query jpaQuery = entityManager.createNativeQuery(query, NATIVE_QUERY_COURT_SCHEDULE_MAPPING_SLOTS);

        // Set parameters
        params.forEach(jpaQuery::setParameter);

        return jpaQuery.getResultList();
    }

    private List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> processScheduleEntities(List<CourtSchedule> scheduleEntities) {
        final long mappingStartTime = System.nanoTime();

        Set<String> courtScheduleIds = new TreeSet<>();
        Map<String, CourtSchedule> courtScheduleMap = scheduleEntities.stream()
                .collect(Collectors.toMap(CourtSchedule::getCourtScheduleId, Function.identity()));
        scheduleEntities.forEach(e -> courtScheduleIds.add(e.getCourtScheduleId()));

        Map<String, List<SlotStartTime>> slotStartTimeList = getCountBasedAllocatedListing(courtScheduleIds, courtScheduleMap);

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> domainSchedules = scheduleEntities.stream()
                .map(CourtSchedulerConverter::convert)
                .toList();

        processJudiciaryDetails(scheduleEntities, domainSchedules);

        domainSchedules.forEach(schedule ->
                addSlotStartTimes(slotStartTimeList, schedule));

        final long mappingEndTime = System.nanoTime();
        LOGGER.info("PRF: Time taken for mapping : {}", (mappingEndTime - mappingStartTime) / 1000000);

        return domainSchedules;
    }

    private void processJudiciaryDetails(List<CourtSchedule> schedulesWithProfiles,
                                         List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> domainSchedules) {
        List<CourtScheduleJudiciary> judiciaryList = getCourtScheduleJudiciaries(schedulesWithProfiles);
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> domainJudiciaries =
                judiciaryList.stream()
                        .map(CourtScheduleRepositoryImpl::mapJudiciaryEntityToDomain)
                        .toList();

        domainSchedules.forEach(schedule ->
                addJudiciaries(domainJudiciaries, schedule));
    }

    public List<CourtScheduleJudiciary> getCourtScheduleJudiciaries(List<CourtSchedule> courtScheduleList) {
        return getCourtScheduleJudiciariesByCourtScheduleIds(courtScheduleList.stream().map(CourtSchedule::getCourtScheduleId).toList());
    }

    public List<CourtScheduleJudiciary> getCourtScheduleJudiciariesByCourtScheduleIds(List<String> courtScheduleIdList) {
        if (courtScheduleIdList == null || courtScheduleIdList.isEmpty()) {
            return Collections.emptyList();
        }
        final long startjudiciaryquery = System.nanoTime();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryList = new ArrayList<>();
        for (int i = 0; i < courtScheduleIdList.size(); i += ID_QUERY_BATCH_SIZE) {
            final List<String> batch = courtScheduleIdList.subList(i, Math.min(i + ID_QUERY_BATCH_SIZE, courtScheduleIdList.size()));
            final jakarta.persistence.Query query = entityManager.createNativeQuery(
                    "select * from court_schedule_judiciary s where s.active = true and s.court_schedule_id in (:courtScheduleIdList)",
                    CourtScheduleJudiciary.class);
            query.setParameter("courtScheduleIdList", batch);
            courtScheduleJudiciaryList.addAll(query.getResultList());
        }
        final long endjudiciaryquery = System.nanoTime();
        LOGGER.info("PRF: Time taken for judiciaryquery : {}", (endjudiciaryquery - startjudiciaryquery) / 1000000);
        return courtScheduleJudiciaryList;
    }

    public List<CourtScheduleJudiciary> getCourtScheduleJudiciariesForProvisionalBooking(List<CourtSchedule> courtScheduleList) {
        if (courtScheduleList == null || courtScheduleList.isEmpty()) {
            return Collections.emptyList();
        }
        final long startjudiciaryquery = System.nanoTime();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryList = new ArrayList<>();
        for (int i = 0; i < courtScheduleList.size(); i += ID_QUERY_BATCH_SIZE) {
            final List<CourtSchedule> batch = courtScheduleList.subList(i, Math.min(i + ID_QUERY_BATCH_SIZE, courtScheduleList.size()));
            courtScheduleJudiciaryList.addAll(entityManager.createNativeQuery(
                    "select * from court_schedule_judiciary s where s.active = true and s.court_schedule_id in (:courtScheduleIdList) and court_listing_profile_id in (:courtListingProfileIdList)",
                    CourtScheduleJudiciary.class)
                .setParameter("courtScheduleIdList", batch.stream().map(CourtSchedule::getCourtScheduleId).toList())
                .setParameter("courtListingProfileIdList", batch.stream().map(CourtSchedule::getListingProfileId).toList())
                .getResultList());
        }
        final long endjudiciaryquery = System.nanoTime();
        LOGGER.info("PRF: Time taken for judiciaryquery : {}", (endjudiciaryquery - startjudiciaryquery) / 1000000);
        return courtScheduleJudiciaryList;
    }

    @Transactional
    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> deleteCourtSchedule(List<String> courtScheduleIdList) {
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> errorDeleteCourtSchedules = new ArrayList<>();
        ModelMapper modelMapper = new ModelMapper();
        courtScheduleIdList.forEach(courtScheduleId -> {
            CourtSchedule courtSchedule = entityManager.find(CourtSchedule.class, courtScheduleId);
            if (courtSchedule != null) {
                List<AllocatedListing> allocatedListings = allocatedListingRepository.findByCourtScheduleId(courtScheduleId);
                if (isNotEmpty(allocatedListings)) {
                    uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule domainCourtSchedule =
                            modelMapper.map(courtSchedule, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.class);
                    errorDeleteCourtSchedules.add(domainCourtSchedule);
                } else {
                    if (!courtSchedule.getSessionDate().isBefore(now())) {
                        List<CourtScheduleJudiciary> courtScheduleJudiciaries = courtScheduleJudiciaryRepository.findByCourtScheduleId(courtScheduleId);
                        courtScheduleJudiciaries.forEach(courtScheduleJudiciary -> courtScheduleJudiciaryRepository.remove(courtScheduleJudiciary));
                        entityManager.remove(entityManager.contains(courtSchedule) ? courtSchedule : entityManager.merge(courtSchedule));
                    }
                }
            }
        });
        return errorDeleteCourtSchedules;
    }

    @Transactional
    @SuppressWarnings({"squid:S2077"})
    public int deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return entityManager
                .createNativeQuery(DELETE_UNALLOCATED_COURT_SCHEDULE_QUERY)
                .setParameter(START_DATE, startDate)
                .setParameter(END_DATE, endDate)
                .setParameter("ouCodes", ouCodes)
                .executeUpdate();
    }

    @Transactional
    @SuppressWarnings({"squid:S2077"})
    public int deleteUnAllocatedProvisionalEntries(final List<String> ouCodes) {
        return entityManager
                .createNativeQuery(DELETE_UNALLOCATED_FORECAST_SLOT_QUERY)
                .setParameter("ouCodes", ouCodes)
                .executeUpdate();
    }

    @Transactional
    @SuppressWarnings({"squid:S2077"})
    public int deleteSlots(final List<String> courtScheduleIds) {
        return entityManager
                .createNativeQuery(DELETE_SLOTS_BY_IDS_QUERY)
                .setParameter(COURT_SCHEDULE_IDS, courtScheduleIds)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void releaseAllocatedSlotsOrDurationFromCourtSchedule(final List<AllocatedListing> allocatedListings) {
        allocatedListings.forEach(allocatedListing -> {
            CourtSchedule courtSchedule = entityManager.find(CourtSchedule.class, allocatedListing.getCourtScheduleId());
            if (courtSchedule.isSlotBased()) {
                courtSchedule.setAvailableSlots(courtSchedule.getAvailableSlots() + 1);
            } else {
                courtSchedule.setAvailableDuration(courtSchedule.getAvailableDuration() + allocatedListing.getDuration());
            }
            saveInternal(courtSchedule);
        });
    }

    protected void releaseCourtScheduleAllocatedSlotsForBookingId(final List<AllocatedListing> allocatedListings) {

        allocatedListings.forEach(allocatedListing -> {
            Optional<ProvisionalBooking> byBookingId = provisionalBookingRepository.findByBookingId(allocatedListing.getBookingId());
            if (byBookingId.isPresent()) {
                ProvisionalBooking provisionalBooking = byBookingId.get();
                provisionalBooking.setActive(true);
                provisionalBookingRepository.save(provisionalBooking);
            }
        });
    }

    protected boolean releaseOldListingsFromAllocatedListings(final String hearingId) {
        List<AllocatedListing> allocatedListings = this.allocatedListingRepository.findByHearingId(hearingId);
        allocatedListings.forEach(allocatedListing -> this.allocatedListingRepository.remove(allocatedListing));
        return !allocatedListings.isEmpty();
    }

    List<AllocatedSlot> searchBookSlots(final List<AllocatedSlot> allocatedSlots) {

        final List<AllocatedSlot> matchedSlots = new ArrayList<>();
        for (final AllocatedSlot allocatedSlot : allocatedSlots) {

            final LocalDateTime sessionFromHearingStartTime = StringUtils.isNotBlank(allocatedSlot.getHearingStartTime()) ? (ZonedDateTime.parse(allocatedSlot.getHearingStartTime())).toLocalDateTime() : null;
            final LocalDate hearingSessionSearchCutOff = StringUtils.isNotBlank(allocatedSlot.getHearingSessionDateSearchCutOff()) ? LocalDate.parse(allocatedSlot.getHearingSessionDateSearchCutOff()) : null;
            final CourtSchedule courtScheduleFound = searchListHearingSlotFilterCriteria(allocatedSlot.getCourtCentreId(), LocalDate.parse(
                    allocatedSlot.getSessionDate()), hearingSessionSearchCutOff ,sessionFromHearingStartTime, allocatedSlot.getCourtRoomUUId(), allocatedSlot.isPolice());

            if (courtScheduleFound != null) {
                List<CourtScheduleJudiciary> judiciaryList = getCourtScheduleJudiciaries(List.of(courtScheduleFound));
                allocatedSlot.setCourtRoomId(String.valueOf(courtScheduleFound.getCourtRoomNumber()));
                allocatedSlot.setCourtRoomUUId(String.valueOf(courtScheduleFound.getCourtRoomId()));
                allocatedSlot.setCourtScheduleId(courtScheduleFound.getCourtScheduleId());
                allocatedSlot.setCourtRoom(courtScheduleFound.getCourtRoomName());
                allocatedSlot.setOuCode(courtScheduleFound.getOuCode());
                allocatedSlot.setHearingStartTime(toIsoStringExtended(getAdjustedHearingStartTime(allocatedSlot.getHearingStartTime(),courtScheduleFound)));
                allocatedSlot.setSlotBased(courtScheduleFound.isSlotBased());
                allocatedSlot.setJudiciaries(
                        judiciaryList.stream()
                                .map(CourtScheduleRepositoryImpl::mapJudiciaryEntityToDomain)
                                .toList());
                allocatedSlot.setSource(allocatedSlot.isPolice() ? "POLICE" : "NONPOLICE");
                matchedSlots.add(allocatedSlot);
            } else {
                LOGGER.error(format("Could not update slot as court schedule id not found for combination %s, %s, %s, %s",
                        allocatedSlot.getOuCode(), allocatedSlot.getSessionDate(), allocatedSlot.getSession(), allocatedSlot.getCourtRoomId()));
            }
        }

        return matchedSlots;
    }

    private List<AllocatedSlot> getUpdatedAllocatedSlots(final List<AllocatedSlot> allocatedSlots, final boolean isSearchUpdate) {

        final List<AllocatedSlot> matchedSlots = new ArrayList<>();

        for (final AllocatedSlot allocatedSlot : allocatedSlots) {

            if (isBlank(allocatedSlot.getCourtScheduleId())) {
                allocatedSlot.setCourtScheduleId(null);
            }
            final String sessionFromHearingStartTime = allocatedSlot.getHearingStartTime() == null ? allocatedSlot.getSession() : toMeridian(allocatedSlot.getHearingStartTime());
            final CourtSchedule slotsFound = getCourtScheduleIdAndSlotBased(allocatedSlot.getOuCode(), LocalDate.parse(allocatedSlot.getSessionDate()),
                    sessionFromHearingStartTime, allocatedSlot.getCourtRoomId(), allocatedSlot.getCourtScheduleId(), isSearchUpdate);

            if (slotsFound != null) {
                final Pair<Optional<String>, Boolean> pair = Pair.of(of(slotsFound.getCourtScheduleId()), slotsFound.isSlotBased());
                allocatedSlot.setCourtRoomId(String.valueOf(slotsFound.getCourtRoomNumber()));
                allocatedSlot.setCourtRoomUUId(String.valueOf(slotsFound.getCourtRoomId()));
                allocatedSlot.setCourtScheduleId(slotsFound.getCourtScheduleId());
                allocatedSlot.setCourtRoom(slotsFound.getCourtRoomName());
                updateCourtScheduleAndSlotBased(allocatedSlot, pair);
                matchedSlots.add(allocatedSlot);
            } else {
                LOGGER.error(format("Could not update slot as court schedule id not found for combination %s, %s, %s, %s",
                        allocatedSlot.getOuCode(), allocatedSlot.getSessionDate(), allocatedSlot.getSession(), allocatedSlot.getCourtRoomId()));
            }
        }

        return matchedSlots;
    }

    private void updateCourtScheduleAndSlotBased(final AllocatedSlot allocatedSlot, final Pair<Optional<String>, Boolean> pair) {
        final Optional<String> courtSchedule = pair.getKey();
        if (courtSchedule.isPresent()) {
            allocatedSlot.setCourtScheduleId(courtSchedule.get());
            final boolean isSlotBased = pair.getValue();
            allocatedSlot.setSlotBased(isSlotBased);
        }
    }

    private CourtSchedule getCourtScheduleIdAndSlotBased(final String ouCode,
                                                         final LocalDate sessionDate,
                                                         final String session,
                                                         final String courtRoomNumber,
                                                         final String courtScheduleId,
                                                         final boolean isSearchUpdate) {

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        List<CourtSchedule> courtScheduleList;
        if (isSearchUpdate) {
            LOGGER.info(format("Trying to find a match with these params for SPI : courtCentreId: %s sessionDate: %s session: %s courtRoomNumber:%s ", ouCode, sessionDate, session, courtRoomNumber));
            courtScheduleList = getCourtScheduleForSearchUpdateFilterCriteria(ouCode, sessionDate, session, courtRoomNumber, true);
            LOGGER.info(format("found %d matches for SPI", courtScheduleList.size()));
            if (CollectionUtils.isEmpty(courtScheduleList)) {
                LOGGER.info(format("Trying to find a match with these params for SPI : courtCentreId: %s sessionDate: %s session: %s ", ouCode, sessionDate, session));
                courtScheduleList = getCourtScheduleForSearchUpdateFilterCriteria(ouCode, sessionDate, session, courtRoomNumber, false);
                LOGGER.info(format("found %d matches SPI", courtScheduleList.size()));
            }
        } else {
            courtScheduleCriteria.createFetchCourtScheduleEitherByidOrFiltersCriteria(courtScheduleId, ouCode, sessionDate, session, courtRoomNumber, criteriaBuilder, criteriaQuery);
            LOGGER.info(format("Trying to find a match with these params : courtCentreId: %s sessionDate: %s session: %s courtRoomNumber:%s courtScheduleId: %s  ", ouCode, sessionDate, session, courtRoomNumber, courtScheduleId));
            courtScheduleList =
                    entityManager.createQuery(criteriaQuery).getResultList();
            LOGGER.info(format("found %d matches", courtScheduleList.size()));
        }

        if (CollectionUtils.isNotEmpty(courtScheduleList)) {
            return courtScheduleList.get(0);
        }
        return null;
    }

    private List<CourtSchedule> getCourtScheduleForSearchUpdateFilterCriteria(String ouCode,
                                                                              LocalDate sessionDate,
                                                                              String courtSessionString,
                                                                              String courtRoomNumber,
                                                                              boolean isNarrowSearch) {
        LOGGER.info("Criteria Query Params: courtCentreId {} sessionDate {} courtSessionString {} courtRoomNumber {}", ouCode, sessionDate, courtSessionString, courtRoomNumber);
        final List<String> businessType = List.of("REM", "NGAP", "GAP");
        final List<String> courtSession = List.of("AD", courtSessionString);

        StringBuilder queryString = new StringBuilder("SELECT distinct s.*, case when al.id is not null then true else false end as hasHearingsBooked FROM court_schedule s left outer join  allocated_listings al on(s.id = al.court_schedule_id) WHERE s.active = true ");
        Map<String, Object> params = new HashMap<>();

        queryString.append(COURTCENTREID_QUERY_CONDITION_STRING);
        params.put(COURT_CENTRE_ID, ouCode);
        queryString.append(SESSION_START_QUERY_CONDITION_STRING);
        params.put(SESSION_DATE, sessionDate);
        queryString.append("AND s.court_session IN (:courtSession) ");
        params.put("courtSession", courtSession);
        queryString.append(BUSINESS_TYPE_QUERY_CONDITION_STRING);
        params.put(BUSINESS_TYPE, businessType);

        if (isNarrowSearch && StringUtils.isNotBlank(courtRoomNumber)) {
            queryString.append("AND s.court_room_number = :courtRoomNumber ");
            params.put("courtRoomNumber", Integer.parseInt(courtRoomNumber));
        }

        queryString.append("order by s.rota_business_type desc, s.court_room_number asc");
        LOGGER.info("getCourtScheduleForSearchUpdateFilterCriteria Criteria Query Params: queryString {}", queryString);
        final jakarta.persistence.Query selectQuery = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
        params.forEach((key, value) -> {
            if (value != null) {
                selectQuery.setParameter(key, value);
            }
        });

        return selectQuery.getResultList();
    }


    public CourtSchedule searchListHearingSlotFilterCriteria(String courtCentreId,
                                                                              LocalDate sessionDate,
                                                                              LocalDate sessionEndDate,
                                                                              LocalDateTime sessionStartTime,
                                                                              String courtRoomId,
                                                                              Boolean isPolice) {
        LOGGER.info("CourtScheduleRepository:searchListHearingSlotFilterCriteria courtCentreId: {}, sessionDate: {}, sessionEndDate: {}, hearingStartTime: {}, courtRoomId: {}",
                courtCentreId, sessionDate, sessionEndDate, sessionStartTime, courtRoomId);

        List<CourtSchedule> resultList;
        if (Boolean.TRUE.equals(isPolice)) {
            resultList = getCourtSchedulesForPolice(courtCentreId, sessionDate, sessionEndDate, sessionStartTime, courtRoomId);
        } else {
            //get court schedules for non-spi
            resultList = getCourtSchedulesForNonPolice(courtCentreId, sessionDate, sessionStartTime, courtRoomId);
        }

        return (resultList != null && !resultList.isEmpty()) ? resultList.get(0) : null;
    }

    private List<CourtSchedule> getCourtSchedulesForPolice(String courtCentreId, LocalDate sessionDate, LocalDate sessionEndDate, LocalDateTime sessionStartTime, String courtRoomId) {
        List<CourtSchedule> resultList;
        do {
            resultList = performFallbackSearchForPolice(courtCentreId, sessionDate, sessionStartTime, courtRoomId);
            sessionDate = sessionDate.plusDays(1);
        } while (isSearchResultEmpty(resultList) && shouldContinueSearch(sessionDate, sessionEndDate));
        return resultList;
    }

    /**
     * Performs a fallback search strategy for police court schedules.
     * Tries multiple search combinations with progressively relaxed criteria.
     */
    private List<CourtSchedule> performFallbackSearchForPolice(String courtCentreId, LocalDate sessionDate, LocalDateTime sessionStartTime, String courtRoomId) {
        // 1st attempt: All parameters
        List<CourtSchedule> resultList = searchWithLogging("1st Call with All params",
            () -> searchListQueryFilterCriteriaForPolice(courtCentreId, sessionDate, sessionStartTime, courtRoomId),
            courtCentreId, sessionDate, sessionStartTime, courtRoomId);

        if (isSearchResultEmpty(resultList)) {
            // 2nd attempt: Remove sessionStartTime
            resultList = searchWithLogging("2nd Call with All params except sessionStartTime",
                () -> searchListQueryFilterCriteriaForPolice(courtCentreId, sessionDate, null, courtRoomId),
                courtCentreId, sessionDate, null, courtRoomId);
            resultList = applyClosestTimeFilterIfNeeded(resultList, sessionStartTime);
        }

        if (isSearchResultEmpty(resultList)) {
            // 3rd attempt: Remove courtRoomId
            resultList = searchWithLogging("3rd Call with All params except courtRoom",
                () -> searchListQueryFilterCriteriaForPolice(courtCentreId, sessionDate, sessionStartTime, null),
                courtCentreId, sessionDate, sessionStartTime, null);
        }

        if (isSearchResultEmpty(resultList)) {
            // 4th attempt: Remove both sessionStartTime and courtRoomId
            resultList = searchWithLogging("4th Call with All params except courtRoom and hearingStartTime",
                () -> searchListQueryFilterCriteriaForPolice(courtCentreId, sessionDate, null, null),
                courtCentreId, sessionDate, null, null);
            resultList = applyClosestTimeFilterIfNeeded(resultList, sessionStartTime);
        }

        return resultList;
    }

    /**
     * Executes a search with logging and returns the result.
     */
    private List<CourtSchedule> searchWithLogging(String attemptDescription,
                                                 java.util.function.Supplier<List<CourtSchedule>> searchFunction,
                                                 String courtCentreId, LocalDate sessionDate,
                                                 LocalDateTime sessionStartTime, String courtRoomId) {
        LOGGER.info("CourtScheduleRepository:searchListHearingSlotFilterCriteria {} courtCentreId: {}, sessionDate: {}, sessionStartTime: {}, courtRoomId: {}",
                   attemptDescription, courtCentreId, sessionDate, sessionStartTime, courtRoomId);
        return searchFunction.get();
    }

    /**
     * Applies the closest time filter if conditions are met.
     */
    private List<CourtSchedule> applyClosestTimeFilterIfNeeded(List<CourtSchedule> resultList, LocalDateTime sessionStartTime) {
        if (sessionStartTime != null && !isSearchResultEmpty(resultList)) {
            return findClosestCourtScheduleByTimeAndBusinessType(resultList, sessionStartTime);
        }
        return resultList;
    }

    /**
     * Checks if the search result is empty or null.
     */
    private boolean isSearchResultEmpty(List<CourtSchedule> resultList) {
        return resultList == null || resultList.isEmpty();
    }

    /**
     * Determines if the search should continue based on date constraints.
     */
    private boolean shouldContinueSearch(LocalDate currentDate, LocalDate sessionEndDate) {
        return sessionEndDate != null && (currentDate.isBefore(sessionEndDate) || currentDate.isEqual(sessionEndDate));
    }

    /**
     * Finds the closest court schedule by comparing session start times between first and second schedules
     * if they have the same business type. Returns a list containing the court schedule with the closest 
     * session start time to the requested time.
     */
    private List<CourtSchedule> findClosestCourtScheduleByTimeAndBusinessType(List<CourtSchedule> courtSchedules, LocalDateTime requestedTime) {
        if (courtSchedules == null || courtSchedules.isEmpty() || requestedTime == null) {
            return courtSchedules;
        }

        // If there's only one schedule, return it
        if (courtSchedules.size() == 1) {
            LOGGER.info("Only one court schedule found, returning it");
            return courtSchedules;
        }

        LOGGER.info("Finding closest court schedule for requested time: {} comparing all schedules with same business type", requestedTime);

        // Calculate national break out time
        LocalDate sessionDate = courtSchedules.get(0).getSessionDate();
        Date nationalBreakTime = TimezoneUtils.calculateNationalBreakTime(sessionDate);
        LocalDateTime nationalBreakOutTime = convertToLocalDateTime(nationalBreakTime);

        LOGGER.info("National break out time calculated: {} for session date: {}", nationalBreakOutTime, sessionDate);

        // Create two separate filter lists based on national break out time
        List<CourtSchedule> schedulesBeforeBreak = courtSchedules.stream()
                .filter(schedule -> {
                    if (schedule.getSessionStartTime() == null) {
                        return false;
                    }
                    LocalDateTime scheduleTime = convertToLocalDateTime(schedule.getSessionStartTime());
                    return scheduleTime.isBefore(nationalBreakOutTime);
                })
                .toList();

        List<CourtSchedule> schedulesAfterBreak = courtSchedules.stream()
                .filter(schedule -> {
                    if (schedule.getSessionEndTime() == null) {
                        return false;
                    }
                    LocalDateTime scheduleTime = convertToLocalDateTime(schedule.getSessionEndTime());
                    return scheduleTime.isAfter(nationalBreakOutTime);
                })
                .toList();

        LOGGER.info("Created separate filter lists - Before break: {} schedules, After break: {} schedules",
                schedulesBeforeBreak.size(), schedulesAfterBreak.size());

        // Select the appropriate list based on requested time
        List<CourtSchedule> selectedSchedules;
        if (requestedTime.isBefore(nationalBreakOutTime)) {
            selectedSchedules = schedulesBeforeBreak;
            LOGGER.info("Requested time {} is before national break out time {}, selecting {} schedules before break",
                    requestedTime, nationalBreakOutTime, selectedSchedules.size());
        } else {
            selectedSchedules = schedulesAfterBreak;
            LOGGER.info("Requested time {} is after or equal to national break out time {}, selecting {} schedules after break",
                    requestedTime, nationalBreakOutTime, selectedSchedules.size());
        }

        // If no schedules in selected list, use all schedules as fallback
        if (selectedSchedules.isEmpty()) {
            LOGGER.info("No schedules in selected list, using all {} schedules as fallback", courtSchedules.size());
            selectedSchedules = courtSchedules;
        }

        // Find the schedule with the closest session start time from selected schedules
        CourtSchedule closestSchedule = selectedSchedules.get(0);

        LOGGER.info("Found closest court schedule: {} with session start time: {} and business type: {}",
                closestSchedule.getCourtScheduleId(), closestSchedule.getSessionStartTime(), closestSchedule.getBusinessType());

        return List.of(closestSchedule);
    }

    /**
     * Converts Date to LocalDateTime for comparison
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
    }

    private List<CourtSchedule> getCourtSchedulesForNonPolice(String courtCentreId, LocalDate sessionDate, LocalDateTime sessionStartTime, String courtRoomId) {
        List<CourtSchedule> resultList;
            LOGGER.info("CourtScheduleRepository:searchListHearingSlotFilterCriteria First Call with All params for courtCentreId: {} and sessionDate: {}", courtCentreId, sessionDate);
            resultList = searchListQueryFilterCriteriaForNonPolice(courtCentreId, sessionDate, sessionStartTime, courtRoomId);
        return resultList;
    }

    private List<CourtSchedule> searchListQueryFilterCriteriaForNonPolice(String courtCentreId,
                                                                   LocalDate sessionDate,
                                                                   LocalDateTime sessionStartTime,
                                                                   String courtRoomId) {
        LOGGER.info("Criteria Query Params: courtCentreId {} sessionDate {} hearingStartTime {} courtRoomId {}", courtCentreId, sessionDate, sessionStartTime, courtRoomId);

        StringBuilder queryString = new StringBuilder("SELECT distinct s.*, case when al.id is not null then true else false end as hasHearingsBooked FROM " +
                "court_schedule s left outer join  allocated_listings al on(s.id = al.court_schedule_id) WHERE s.active = true ");
        Map<String, Object> params = new HashMap<>();

        final List<String> businessType = List.of("NCFL");
        queryString.append(BUSINESS_TYPE_QUERY_CONDITION_STRING);
        params.put(BUSINESS_TYPE, businessType);

        queryString.append(COURTCENTREID_QUERY_CONDITION_STRING);
        params.put(COURT_CENTRE_ID, courtCentreId);

        queryString.append(SESSION_START_QUERY_CONDITION_STRING);
        params.put(SESSION_DATE, sessionDate);

        if(sessionStartTime != null) {
            queryString.append("AND (:hearingStartTime) between s.session_start_time and s.session_end_time ");
            params.put(HEARING_START_TIME, sessionStartTime);
        }
        if (courtRoomId != null) {
            queryString.append(COURT_ROOM_ID_QUERY_CONDITION_STRING);
            params.put(COURT_ROOM_ID, courtRoomId);
        } else return Collections.emptyList(); //courtroom required to allocate session for non-spi

        queryString.append("order by s.rota_business_type desc, s.court_room_number asc");
        LOGGER.info("searchListQueryFilterCriteriaForNonPolice Criteria Query Params: queryString {}", queryString);
        final jakarta.persistence.Query selectQuery = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
        params.forEach((key, value) -> {
            if (value != null) {
                selectQuery.setParameter(key, value);
            }
        });
        return selectQuery.getResultList();
    }

    private List<CourtSchedule> searchListQueryFilterCriteriaForPolice(String courtCentreId,
                                                              LocalDate sessionDate,
                                                              LocalDateTime sessionStartTime,
                                                              String courtRoomId) {
        LOGGER.info("Criteria Query Params: courtCentreId {} sessionDate {} hearingStartTime {} courtRoomId {}", courtCentreId, sessionDate, sessionStartTime, courtRoomId);
        final List<String> businessType = List.of("YFL", "TRFL", "DAFL", "NGAP", "GAP", "REM");

        StringBuilder queryString = new StringBuilder("SELECT s.*, case when al.id is not null then true else false end as hasHearingsBooked " +
                "FROM court_schedule s left outer join  allocated_listings al on(s.id = al.court_schedule_id) WHERE s.active = true ");
        Map<String, Object> params = new HashMap<>();

        queryString.append(BUSINESS_TYPE_QUERY_CONDITION_STRING);
        params.put(BUSINESS_TYPE, businessType);

        queryString.append(COURTCENTREID_QUERY_CONDITION_STRING);
        params.put(COURT_CENTRE_ID, courtCentreId);
        queryString.append(SESSION_START_QUERY_CONDITION_STRING);
        params.put(SESSION_DATE, sessionDate);
        if(sessionStartTime != null) {
            queryString.append("AND (:hearingStartTime) between s.session_start_time and s.session_end_time ");
            params.put(HEARING_START_TIME, sessionStartTime);
        }
        if(courtRoomId != null) {
            queryString.append(COURT_ROOM_ID_QUERY_CONDITION_STRING);
            params.put(COURT_ROOM_ID, courtRoomId);
        }

        queryString.append("order by CASE s.rota_business_type when 'YFL' then 1 when 'TRFL' then 2 when 'DAFL' then 3 when 'NGAP' then 4 when 'GAP' then 5 when 'REM' then 6 else 7 END" +
                ", s.court_room_number asc");

        LOGGER.info("searchListQueryFilterCriteriaForPolice Criteria Query Params: queryString {}", queryString);
        final jakarta.persistence.Query selectQuery = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
        params.forEach((key, value) -> {
            if (value != null) {
                selectQuery.setParameter(key, value);
            }
        });
        return selectQuery.getResultList();
    }

    protected void updateCourtSchedule(final List<AllocatedSlot> allocatedSlots) {
        allocatedSlots.forEach(allocatedSlot -> {
            CourtSchedule courtSchedule = entityManager.find(CourtSchedule.class, allocatedSlot.getCourtScheduleId());
            LOGGER.info("CourtSchedule to be updated after allocation : is {}", courtSchedule);
            if (courtSchedule.isSlotBased()) {
                Integer availableSlots = courtSchedule.getAvailableSlots();
                LOGGER.info("CourtSchedule to be updated after allocation : with available slots {}", availableSlots);
                courtSchedule.setAvailableSlots(availableSlots - 1);
            } else {
                LOGGER.info("CourtSchedule to be updated after allocation : with available duration {}", courtSchedule.getAvailableDuration());
                courtSchedule.setAvailableDuration(courtSchedule.getAvailableDuration() - allocatedSlot.getDuration());
            }
            LOGGER.info("Final CourtSchedule object before update : with available slots {}", courtSchedule);
            saveInternal(courtSchedule);
        });
    }

    @Transactional
    protected void saveAllocatedListing(final List<AllocatedSlot> allocatedSlots) {
        allocatedSlots.forEach(allocatedSlot -> {
            // Check if record already exists
            List<AllocatedListing> existingListings = allocatedListingRepository.findByCourtScheduleIdAndHearingId(allocatedSlot.getCourtScheduleId(), allocatedSlot.getHearingId());
            if (existingListings.isEmpty()) {
                final CourtSchedule courtSchedule = entityManager.find(CourtSchedule.class, allocatedSlot.getCourtScheduleId());
                AllocatedListing allocatedListing = new AllocatedListing();
                allocatedListing.setId(UUID.randomUUID().toString());
                allocatedListing.setCourtScheduleId(allocatedSlot.getCourtScheduleId());
                allocatedListing.setBookingId(allocatedSlot.getBookingId());
                allocatedListing.setHearingId(allocatedSlot.getHearingId());
                allocatedListing.setOucode(allocatedSlot.getOuCode());
                allocatedListing.setCourtRoomId(Integer.parseInt(allocatedSlot.getCourtRoomId()));
                allocatedListing.setRotaBusinessType(courtSchedule.getBusinessType());
                allocatedListing.setDuration(allocatedSlot.isSlotBased() ? SLOT_DEFAULT : allocatedSlot.getDuration());
                allocatedListing.setHearingStartTime(toExactTimestamp(allocatedSlot.getHearingStartTime()));
                allocatedListing.setSource(allocatedSlot.getSource());
                LOGGER.info("bookSlotsWithoutCourtScheduleId saveAllocatedListing {}", allocatedListing);
                this.allocatedListingRepository.save(allocatedListing);
            } else {
                LOGGER.info("Record already exists for courtScheduleId {} and hearingId {}",
                        allocatedSlot.getCourtScheduleId(),
                        allocatedSlot.getHearingId());
            }
        });
    }

    @Transactional
    protected void deleteProvisionalBooking(final String bookingId) {
        Optional<ProvisionalBooking> byBookingId = this.provisionalBookingRepository.findByBookingId(bookingId);
        if (byBookingId.isPresent()) {
            ProvisionalBooking provisionalBooking = byBookingId.get();
            provisionalBooking.setActive(false);
            this.provisionalBookingRepository.save(provisionalBooking);
        } else {
            LOGGER.error(format("CHECK: bookingid not found %s", bookingId));
        }
    }

    private Optional<String> getHearingId(final List<AllocatedSlot> slots) {
        return slots.stream()
                .map(AllocatedSlot::getHearingId)
                .findFirst();
    }

    @Transactional
    public void releaseOldAllocatedListings(final String hearingId) {

        final List<AllocatedListing> allocatedListings = getExistingAllocatedListings(hearingId);

        if (isNotEmpty(allocatedListings)) {
            releaseOldListingsFromAllocatedListings(hearingId);
            releaseCourtScheduleAllocatedSlotsForBookingId(allocatedListings);
            releaseAllocatedSlotsOrDurationFromCourtSchedule(allocatedListings);
        }
    }

    /**
     * Date-scoped sibling of {@link #releaseOldAllocatedListings(String)}: releases only the
     * hearing's allocations whose court schedule session date is in {@code dates}, leaving
     * allocations on other dates untouched.
     */
    @Override
    @Transactional
    public void releaseAllocatedListingsForDates(final String hearingId, final List<LocalDate> dates) {

        final List<AllocatedListing> allocatedListings = getExistingAllocatedListings(hearingId).stream()
                .filter(allocatedListing -> dates.contains(
                        entityManager.find(CourtSchedule.class, allocatedListing.getCourtScheduleId()).getSessionDate()))
                .toList();

        if (isNotEmpty(allocatedListings)) {
            allocatedListings.forEach(allocatedListingRepository::remove);
            releaseCourtScheduleAllocatedSlotsForBookingId(allocatedListings);
            releaseAllocatedSlotsOrDurationFromCourtSchedule(allocatedListings);
        }
    }

    private List<AllocatedListing> getExistingAllocatedListings(final String hearingId) {
        return allocatedListingRepository.findByHearingId(hearingId);
    }

    private Map<String, List<SlotStartTime>> getCountBasedAllocatedListing(final Set<String> courtScheduleIds, final Map<String, CourtSchedule> courtScheduleMap) {
        LOGGER.info("number of courtScheduleIds : {}", courtScheduleIds.size());

        final List<String> idList = new ArrayList<>(courtScheduleIds);
        final Map<String, List<Pair<Timestamp, Integer>>> hearingStartTimeMapByCourtSchedule = new HashMap<>();

        for (int i = 0; i < idList.size(); i += ID_QUERY_BATCH_SIZE) {
            final List<String> batch = idList.subList(i, Math.min(i + ID_QUERY_BATCH_SIZE, idList.size()));
            final jakarta.persistence.Query query = entityManager
                    .createNativeQuery("select court_schedule_id , hearing_start_time, count(*) as count,sum(duration) as duration from allocated_listings where court_schedule_id IN :courtScheduleId group by court_schedule_id , hearing_start_time");
            query.setParameter("courtScheduleId", batch);

            final long allocatedListingsStartTime = System.nanoTime();
            final List<Object[]> queryResultList = query.getResultList();
            final long allocatedListingsEndTime = System.nanoTime();
            LOGGER.info("PRF: Time taken for allocatedListings : {} ", (allocatedListingsEndTime - allocatedListingsStartTime) / 1000000);

            queryResultList.forEach(response -> {
                final String courtScheduleId = (String) response[0];
                final List<Pair<Timestamp, Integer>> allocatedHearingStartTimesWithCounts = hearingStartTimeMapByCourtSchedule.computeIfAbsent(courtScheduleId, k -> new ArrayList<>());
                final Timestamp hearingStartTime = toTimestamp(response[1]);
                //find courtScheduleId in courtScheduleMap and check if it is slot based
                final int count = courtScheduleMap.get(courtScheduleId).isSlotBased()
                        ? toInt(response[2])
                        : toInt(response[3]);
                allocatedHearingStartTimesWithCounts.add(Pair.of(hearingStartTime, count));
            });
        }

        return processSlotStartTimes(courtScheduleMap, hearingStartTimeMapByCourtSchedule);
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException e) {
                LOGGER.info("Unable to parse int from value: {}", value);
            }
        }
        return 0;
    }

    /**
     * Native queries on Postgres TIMESTAMP columns under Hibernate 7 + the recent JDBC
     * driver return {@link java.time.Instant} — the legacy code path expected
     * {@link java.sql.Timestamp}. Accept both shapes.
     */
    private static Timestamp toTimestamp(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts;
        }
        if (value instanceof java.time.Instant instant) {
            return Timestamp.from(instant);
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return Timestamp.from(odt.toInstant());
        }
        throw new IllegalArgumentException("Unsupported timestamp shape from native query: " + value.getClass());
    }

    private static Map<String, List<SlotStartTime>> processSlotStartTimes(final Map<String, CourtSchedule> courtScheduleMap, final Map<String, List<Pair<Timestamp, Integer>>> hearingStartTimeMapByCourtSchedule) {
        final Map<String, List<SlotStartTime>> resultStringListMap = new HashMap<>();
        courtScheduleMap.keySet().forEach(courtScheduleId -> {
            final CourtSchedule courtSchedule = courtScheduleMap.get(courtScheduleId);
            final SlotProcessingContext ctx = getSlotProcessingContext(courtSchedule,hearingStartTimeMapByCourtSchedule);

            final List<SlotStartTime> slotStartTimes;
            if ("AD".equals(ctx.courtSession()) && Boolean.TRUE.equals(courtSchedule.getSupportAdSplit())) {
                slotStartTimes = processSlotStartTimesWithADSplitSupport(ctx.courtScheduleAllocatedPair(), ctx.sessionStartDateTime(), ctx.sessionEndDateTime(), getNationalBreakTime(courtSchedule));
            } else if (ctx.sessionStartDateTime().toLocalDate().equals(ctx.sessionEndDateTime().toLocalDate().minusDays(1))) {
                slotStartTimes = processSlotStartTimesMultiDay(ctx); //special handling for sessionStartTime(UTC) falling in previous day
            } else {
                slotStartTimes = processSlotStartTimes(ctx);//normal AM, PM sessions
            }

            resultStringListMap.put(courtScheduleId, slotStartTimes);
            LOGGER.info("courtScheduleId : {} slotStartTimes : {}", courtScheduleId, slotStartTimes);
        });
        return resultStringListMap;
    }
    private static SlotProcessingContext getSlotProcessingContext(final CourtSchedule courtSchedule, final Map<String, List<Pair<Timestamp, Integer>>> hearingStartTimeMapByCourtSchedule) {
        final Date sessionStartTime = courtSchedule.getSessionStartTime();
        final Date sessionEndTime = courtSchedule.getSessionEndTime();
        final List<Pair<Timestamp, Integer>> courtScheduleAllocatedPair = hearingStartTimeMapByCourtSchedule.get(courtSchedule.getCourtScheduleId());
        final LocalDateTime sessionStartDateTime = sessionStartTime.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
        final LocalDateTime sessionEndDateTime = sessionEndTime.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
        final int sessionStartHour = sessionStartDateTime.getHour();
        final int sessionStartMinute = sessionStartDateTime.getMinute();
        final int sessionEndHour = sessionEndDateTime.getHour();
        final int sessionEndMinute = sessionEndDateTime.getMinute();
        final AtomicInteger nextMinutePart = new AtomicInteger(sessionStartMinute);
        final boolean slotBased = courtSchedule.isSlotBased();
        final String courtSession  = courtSchedule.getCourtSession();

        final int nationalBreakTimeStartHour = "AD".equals(courtSession) ? getNationalBreakTime(courtSchedule).getHour() : 0;

        return new SlotProcessingContext(sessionStartHour,
               sessionEndHour,
               sessionEndMinute,
               nextMinutePart,
               courtScheduleAllocatedPair,
               sessionStartDateTime,
               sessionEndDateTime,
               slotBased,
               nationalBreakTimeStartHour,
                courtSession) ;
    }

    private static LocalDateTime getNationalBreakTime(CourtSchedule courtSchedule) {
        return courtSchedule.getNationalBreakTime().toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime();
    }

    private static List<SlotStartTime> processSlotStartTimesWithADSplitSupport(List<Pair<Timestamp, Integer>> courtScheduleAllocatedPair, LocalDateTime sessionStartDateTime, LocalDateTime sessionEndDateTime, LocalDateTime nationalBreakTime) {
        // if support AD split then return only two slots with skipping NATIONAL BREAK
        final List<SlotStartTime> slotStartTimes = new ArrayList<>();
        final LocalDateTime nationalBreakEnd = nationalBreakTime.plusHours(1);

        // Morning slot
        if (!sessionStartDateTime.isAfter(nationalBreakTime)) {
            SlotStartTime morningSlot = new SlotStartTime();
            morningSlot.setSessionStartTime(toIsoString(sessionStartDateTime));
            morningSlot.setSessionEndTime(toIsoString(nationalBreakTime));
            morningSlot.setCount(sumAllocatedDuration(courtScheduleAllocatedPair, sessionStartDateTime, nationalBreakTime));
            slotStartTimes.add(morningSlot);
        }

        // Afternoon slot
        if (!sessionEndDateTime.isBefore(nationalBreakEnd)) {
            SlotStartTime afternoonSlot = new SlotStartTime();
            afternoonSlot.setSessionStartTime(toIsoString(nationalBreakEnd));
            afternoonSlot.setSessionEndTime(toIsoString(sessionEndDateTime));
            afternoonSlot.setCount(sumAllocatedDuration(courtScheduleAllocatedPair, nationalBreakEnd, sessionEndDateTime));
            slotStartTimes.add(afternoonSlot);
        }

        return slotStartTimes;

    }

    private static int sumAllocatedDuration(List<Pair<Timestamp, Integer>> allocations, LocalDateTime sessionStartTime, LocalDateTime sessionEndTime) {
        if (allocations == null) return 0;
        return allocations.stream()
                .filter(pair -> {
                    LocalDateTime ts = pair.getLeft().toLocalDateTime();
                    return !ts.isBefore(sessionStartTime) && ts.isBefore(sessionEndTime);
                })
                .mapToInt(Pair::getRight)
                .sum();
    }

    private static List<SlotStartTime> processSlotStartTimes(SlotProcessingContext ctx) {
        final List<SlotStartTime> slotStartTimes = new ArrayList<>();

        return getSlotStartTimes(ctx, ctx.sessionStartHour(), slotStartTimes);
    }

    private static List<SlotStartTime> processSlotStartTimesMultiDay(SlotProcessingContext ctx) {
        final List<SlotStartTime> slotStartTimes = new ArrayList<>();

            getSlotBeforeMidnight(ctx, slotStartTimes);
        return getSlotStartTimes(ctx, 0, slotStartTimes);
    }

    private static List<Pair<Timestamp, Integer>> filterBySlotWindow(
            final List<Pair<Timestamp, Integer>> allocations,
            final LocalDateTime slotStart,
            final LocalDateTime slotEnd,
            final LocalDateTime sessionEnd,
            final boolean isFinalBucket,
            final int sessionEndMinute) {

        if (allocations == null || allocations.isEmpty()) return Collections.emptyList();

        final boolean includeSessionEndInThisBucket =
                isFinalBucket && sessionEndMinute == 0; // only when the last bucket ends exactly at :00

        return allocations.stream()
                .filter(p -> {
                    LocalDateTime ts = p.getLeft().toLocalDateTime();
                    // normal case: [start, end)
                    boolean in = !ts.isBefore(slotStart) && ts.isBefore(slotEnd);
                    // special rule: if this is the last bucket and the session ends on the hour,
                    // pull in a booking exactly at the session end (== slotEnd) into the previous bucket
                    if (!in && includeSessionEndInThisBucket) {
                        in = ts.equals(sessionEnd);
                    }
                    return in;
                })
                .toList();
    }

    private static List<SlotStartTime> getSlotStartTimes(SlotProcessingContext ctx, int sessionStartHour, List<SlotStartTime> slotStartTimes) {
        for (int currentHour = sessionStartHour; currentHour <= ctx.sessionEndHour(); currentHour++) {
            if ((currentHour == ctx.sessionEndHour() && ctx.sessionEndMinute() > 0) || currentHour < ctx.sessionEndHour()) {
                if ((ctx.nationalBreakTimeStartHour() != 0) && (currentHour == ctx.nationalBreakTimeStartHour())) {
                    LOGGER.info("Skipping national break slot at {}", currentHour);
                    continue;
                }

                decideNextMinutePart(currentHour, ctx.sessionStartHour(), ctx.sessionEndHour(), ctx.nextMinutePart(), ctx.sessionEndMinute());
                final List<Pair<Timestamp, Integer>> filteredList = getCourtScheduleAllocatedPairForHourlyPart(
                        ctx.courtScheduleAllocatedPair(), currentHour, ctx.sessionEndHour(), ctx.sessionEndMinute(), ctx.nextMinutePart());
                final Pair<Integer, Integer> startAndEndTime = getStartAndEndTimeForHourlyPart(
                        currentHour, ctx.sessionStartHour(), ctx.sessionEndHour(), ctx.sessionEndMinute(), ctx.nextMinutePart());
                final int slotEndHour = (currentHour == ctx.sessionEndHour()) ? ctx.sessionEndHour() : currentHour + 1;

                final LocalDateTime slotStartTime = LocalDateTime.of(ctx.sessionEndDateTime().toLocalDate(), LocalTime.of(currentHour, startAndEndTime.getLeft()));
                final LocalDateTime slotEndTime = LocalDateTime.of(ctx.sessionEndDateTime().toLocalDate(), LocalTime.of(slotEndHour, startAndEndTime.getRight()));

                final AtomicInteger count = new AtomicInteger(0);
                filteredList.forEach(pair -> count.set(count.get() + pair.getRight()));

                SlotStartTime slot = new SlotStartTime();
                slot.setSessionStartTime(toIsoString(slotStartTime));
                slot.setSessionEndTime(toIsoString(slotEndTime));
                slot.setCount(count.get());
                setHearingTimestamp(ctx.slotBased(), currentHour, filteredList, slotEndHour, slot);

                slotStartTimes.add(slot);
            }
        }

        return slotStartTimes;
    }

    private static void getSlotBeforeMidnight(SlotProcessingContext ctx, List<SlotStartTime> slotStartTimes) {
        decideNextMinutePart(ctx.sessionStartHour(), ctx.sessionStartHour(), ctx.sessionEndHour(), ctx.nextMinutePart(), ctx.sessionEndMinute());
        final List<Pair<Timestamp, Integer>> filteredList = getCourtScheduleAllocatedPairForHourlyPart(
                ctx.courtScheduleAllocatedPair(), ctx.sessionStartHour(), ctx.sessionEndHour(), ctx.sessionEndMinute(), ctx.nextMinutePart());
        final Pair<Integer, Integer> startAndEndTime = getStartAndEndTimeForHourlyPart(
                ctx.sessionStartHour(), ctx.sessionStartHour(), ctx.sessionEndHour(), ctx.sessionEndMinute(), ctx.nextMinutePart());

        final LocalDateTime slotStartTime = LocalDateTime.of(ctx.sessionStartDateTime().toLocalDate(), LocalTime.of(ctx.sessionStartHour(), startAndEndTime.getLeft()));
        final LocalDateTime slotEndTime = LocalDateTime.of(ctx.sessionEndDateTime().toLocalDate(), LocalTime.of(0, startAndEndTime.getRight()));

        final AtomicInteger count = new AtomicInteger(0);
        filteredList.forEach(pair -> count.set(count.get() + pair.getRight()));

        SlotStartTime slot = new SlotStartTime();
        slot.setSessionStartTime(toIsoString(slotStartTime));
        slot.setSessionEndTime(toIsoString(slotEndTime));
        slot.setCount(count.get());
        setHearingTimestamp(ctx.slotBased(), ctx.sessionStartHour(), filteredList, 0, slot);

        slotStartTimes.add(slot);
    }

    private static void setHearingTimestamp(final boolean slotBased, final int currentHour, final List<Pair<Timestamp, Integer>> filteredList, final int slotEndHour, final SlotStartTime responseSlotStartTime) {
        if (slotBased) {
            filteredList.forEach(timestampIntegerPair -> {
                final int hearingHour = timestampIntegerPair.getLeft().toLocalDateTime().getHour();
                if (hearingHour >= currentHour && hearingHour < slotEndHour)
                    responseSlotStartTime.setHearingStartTime(toIsoString(timestampIntegerPair.getLeft().toLocalDateTime()));
            });
        }
    }

    private static Pair<Integer, Integer> getStartAndEndTimeForHourlyPart(final int currentHour,
                                                                          final int sessionStartHour,
                                                                          final int sessionEndHour,
                                                                          final int sessionEndMinute,
                                                                          final AtomicInteger nextMinutePart) {
        final int startTimeMinute = currentHour != sessionStartHour ? 0 : nextMinutePart.get();
        final int endTimeMinute = (currentHour > sessionEndHour) ? 0 : getEndTimeMinute(currentHour, sessionEndHour, sessionEndMinute);


        return Pair.of(startTimeMinute, endTimeMinute);
    }

    private static int getEndTimeMinute(int currentHour, int sessionEndHour, int sessionEndMinute) {
        return (currentHour == sessionEndHour) ? sessionEndMinute : 0;
    }

    private static List<Pair<Timestamp, Integer>> getCourtScheduleAllocatedPairForHourlyPart(final List<Pair<Timestamp, Integer>> courtScheduleAllocatedPair, final int currentHourPart, final int sessionEndHour, final int sessionEndMinute, final AtomicInteger nextMinutePart) {
        if (isEmpty(courtScheduleAllocatedPair)) {
            return Collections.emptyList();
        }
        return courtScheduleAllocatedPair.stream()
                .filter(pair -> {
                    final int pairHour = pair.getLeft().toLocalDateTime().getHour();
                    final int pairMinute = pair.getLeft().toLocalDateTime().getMinute();

                    boolean inSameHourWindow = pairHour == currentHourPart &&
                            (
                                    (currentHourPart < sessionEndHour && pairMinute >= nextMinutePart.get())
                                    || (currentHourPart == sessionEndHour && pairMinute <= nextMinutePart.get())
                                    || (currentHourPart == 23 && sessionEndHour == 22 && pairMinute <= nextMinutePart.get())
                            );

                    boolean includeExactEndOnBoundary =
                            (sessionEndMinute == 0)
                                    && (currentHourPart == sessionEndHour - 1)
                                    && (pairHour == sessionEndHour)
                                    && (pairMinute == 0);

                    return inSameHourWindow || includeExactEndOnBoundary;
                })
                .toList();
    }

    private static void decideNextMinutePart(final int currentHour, final int sessionStartHour, final int sessionEndHour, final AtomicInteger nextMinutePart, final int sessionEndMinute) {
        if (currentHour != sessionStartHour) {
            if (currentHour == sessionEndHour) {
                nextMinutePart.set(sessionEndMinute);
            } else {
                nextMinutePart.set(0);
            }
        }
    }

    private void addJudiciaries(final List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> courtScheduleJudiciaries,
                                final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule courtSchedule) {
        of(courtScheduleJudiciaries.stream()
                        .filter(courtScheduleJudiciary -> courtScheduleJudiciary.getCourtScheduleId().equals(courtSchedule.getCourtScheduleId()))
                        .toList())
                .ifPresent(courtSchedule.getJudiciaries()::addAll);
    }

    private void addJudiciaries(final List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> courtScheduleJudiciaries,
                                final Hearing hearing) {
        of(courtScheduleJudiciaries.stream()
                        .filter(courtScheduleJudiciary -> courtScheduleJudiciary.getCourtScheduleId().equals(hearing.getCourtScheduleId()))
                        .toList())
                .ifPresent(hearing.getJudiciaries()::addAll);
    }

    private void addSlotStartTimes(final Map<String, List<SlotStartTime>> slotStartTimes, final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule courtSchedule) {
        final List<SlotStartTime> yes = slotStartTimes.get(courtSchedule.getCourtScheduleId());
        if (isNotEmpty(yes)) {
            courtSchedule.getSlotStartTimes().addAll(yes);
        }
    }

    @Override
    @Transactional
    public int deleteRedundantRotaData(final int numberOfDays) {
        return entityManager
                .createNativeQuery(DELETE_REDUNDANT_ROTA_DATA)
                .setParameter("numberOfDays", numberOfDays)
                .executeUpdate();
    }

    private List<Hearing> flattenHearingSlots(RequestedSlots requestedSlots) {
        List<Hearing> result = new ArrayList<>();

        for (HearingSlot hearingSlot : requestedSlots.getHearingSlots()) {
            for (RequestedCourtSchedule requestedCourtSchedule : hearingSlot.getCourtScheduleIds()) {
                Hearing hearing = new Hearing();
                hearing.setHearingId(hearingSlot.getHearingId());
                hearing.setCourtScheduleId(requestedCourtSchedule.getCourtScheduleId());
                hearing.setHearingStartTime(requestedCourtSchedule.getHearingStartTime());
                hearing.setDuration(requestedCourtSchedule.getDurationInMinutes());
                hearing.setSource(requestedCourtSchedule.getSource());
                result.add(hearing);
            }
        }
        return result;
    }

    private static Date getAdjustedHearingStartTime(String isoDateString, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule cs) {
        if (isoDateString == null || DateUtils.getDate(isoDateString).before(cs.getSessionStartTime()) || DateUtils.getDate(isoDateString).after(cs.getSessionEndTime())) {
            return cs.getSessionStartTime();
        } else {
            return DateUtils.getDate(isoDateString);
        }
    }

}
