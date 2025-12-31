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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.transaction.Transactional;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Modifying;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;
import org.apache.deltaspike.data.api.SingleResultType;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"squid:S1312", "squid:S2629", "squid:S6813"})
@Repository
public abstract class CourtScheduleRepository extends AbstractEntityRepository<CourtSchedule, String> implements EntityRepository<CourtSchedule, String> {

    public static final String BUSINESS_TYPE = "businessType";
    public static final StringBuilder COURT_SCHEDULE_ALL_FIELDS_QUERY_STRING = new StringBuilder("""
                SELECT DISTINCT
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
                    CASE WHEN s.is_slot_based = false and s.support_ad_split =false THEN  s.max_duration_mins - sum(al.duration) ELSE 0 END as available_duration_mins,
                    case when al.id is not null then true else false end as hasHearingsBooked,
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
                    s.jurisdiction,
                    al.id
            """);

    private static final int BATCH_SIZE = 50;
    public static final String COURT_ROOM_ID = "courtRoomId";
    public static final String OU_CODE = "ouCode";
    public static final String COURT_CENTRE_ID = "courtCentreId";
    public static final String SESSION_DATE = "sessionDate";
    public static final String COURTCENTREID_QUERY_CONDITION_STRING = "AND s.court_house_id = :courtCentreId ";
    public static final String SESSION_START_QUERY_CONDITION_STRING = "AND s.session_start = :sessionDate ";
    public static final String BUSINESS_TYPE_QUERY_CONDITION_STRING = "AND s.rota_business_type IN (:businessType) ";
    public static final String COURT_ROOM_ID_QUERY_CONDITION_STRING = "AND s.court_room_id = :courtRoomId ";

    @Inject
    EntityManager entityManager;
    @Inject
    CourtScheduleCriteria courtScheduleCriteria;
    @Inject
    CourtScheduleRetryService courtScheduleRetryService;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;
    @Inject
    ProvisionalBookingRepository provisionalBookingRepository;
    private static final Logger LOGGER = LoggerFactory.getLogger(CourtScheduleRepository.class.getName());

    private static final String DELETE_UNALLOCATED_COURT_SCHEDULE_QUERY = "DELETE FROM court_schedule cs " +
            "WHERE  not exists (select 1 from allocated_listings al where al.court_schedule_id = cs.id) and " +
            "cs.session_start BETWEEN :startDate AND :endDate AND cs.oucode IN (:ouCodes) AND cs.active =true AND NOT EXISTS( " + EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + ")";

    public static final String DELETE_UNALLOCATED_FORECAST_SLOT_QUERY = "DELETE FROM court_schedule " +
            "WHERE court_listing_profile_id is null AND not exists (select 1 from allocated_listings al where al.court_schedule_id = id) AND oucode IN (:ouCodes) " +
            "AND active =true and not exists( " + EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + ")";

    public static final String DELETE_SLOTS_BY_IDS_QUERY = "DELETE FROM court_schedule cs WHERE cs.id IN (:courtScheduleIds) AND cs.court_listing_profile_id is not null AND not exists (select 1 from allocated_listings al where al.court_schedule_id = cs.id) AND  not exists(" + EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + ")";

    private static final String DELETE_REDUNDANT_ROTA_DATA = "DELETE FROM court_schedule cs WHERE cs.session_start < (CURRENT_DATE - :numberOfDays)";

    private static final String GET_HEARING_SLOTS_QUERY_MANDATORY_PARAMS = """
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

    private static final String NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW = "CourtScheduleEntityMappingForView";
    private static final String NATIVE_QUERY_COURT_SCHEDULE_MAPPING_SLOTS = "CourtScheduleEntityMappingForSlots";
    private static final String NATIVE_QUERY_COURT_SCHEDULE_MAPPING_ALL = "CourtScheduleEntityMappingForAllFields";
    private static final int SLOT_DEFAULT = 1;
    private static final int DEFAULT_DURATION_TO_BE_RETURNED = 20;
    private static final String HEARING_START_TIME = "hearingStartTime";

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
     * Highly optimized batch processing for maximum performance.
     * Uses bulk operations, reduced entity manager operations, and optimized memory usage.
     */
    private void processOptimizedBatches(List<CourtSchedule> courtSchedules, List<CourtSchedule> failedSchedules) {
        final int totalRecords = courtSchedules.size();
        final int batchSize = Math.min(BATCH_SIZE, 1000); // Cap batch size for optimal performance
        final int numBatches = (totalRecords + batchSize - 1) / batchSize;

        LOGGER.debug("Processing {} records in {} batches of size {}", totalRecords, numBatches, batchSize);

        // Pre-allocate collections for better performance
        final List<CourtSchedule> currentBatch = new ArrayList<>(batchSize);
        int processedCount = 0;

        try {
            for (int i = 0; i < totalRecords; i++) {
                final CourtSchedule cs = courtSchedules.get(i);
                currentBatch.add(cs);

                // Persist immediately to reduce memory footprint
                entityManager.persist(cs);

                // Process batch when it reaches the configured size or is the last batch
                if (currentBatch.size() >= batchSize || i == totalRecords - 1) {
                    if (flushBatchOptimized(currentBatch, failedSchedules)) {
                        processedCount += currentBatch.size();
                        LOGGER.debug("Successfully processed batch {}/{} with {} records",
                                   (processedCount / batchSize) + 1, numBatches, currentBatch.size());
                    } else {
                        LOGGER.warn("Batch failed, processing {} records individually", currentBatch.size());
                        processIndividualRecordsFast(currentBatch, failedSchedules);
                        processedCount += currentBatch.size();
                    }
                    currentBatch.clear();
                }
            }

        } catch (Exception e) {
            LOGGER.error("Critical error during optimized batch processing: {}", e.getMessage(), e);
            // Process remaining records individually
            if (!currentBatch.isEmpty()) {
                processIndividualRecordsFast(currentBatch, failedSchedules);
            }
        }
    }

    /**
     * Optimized batch flush with minimal overhead and fast execution.
     */
    private boolean flushBatchOptimized(List<CourtSchedule> batch, List<CourtSchedule> failedSchedules) {
        try {
            // Single flush operation for maximum speed
            entityManager.flush();
            entityManager.clear();
            return true;
        } catch (Exception e) {
            LOGGER.warn("Batch flush failed for {} records: {}", batch.size(), e.getMessage());
            // Add failed records to failedSchedules list
            failedSchedules.addAll(batch);
            entityManager.clear();
            return false;
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
    public CourtSchedule update(final CourtSchedule courtSchedule, final boolean isForRotaFile) {

        return courtScheduleRetryService.retryAndSave(courtSchedule, isForRotaFile);
    }

    public static CourtSchedule getCourtScheduleToBeUpdated(final CourtSchedule courtSchedule, final boolean isForRotaFile, final List<CourtSchedule> persistedCourtSchedules) {
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

        this.save(persistedCourtSchedule);
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
        final javax.persistence.Query query = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
        query.setParameter("courtScheduleId", courtScheduleId);
        final List resultList = query.getResultList();
        return isNotEmpty(resultList) ? (CourtSchedule) resultList.get(0) : null;
    }

    @Transactional
    public int getInconsistentCourtSchedulersByOucode(String ouCode) {
        // Step 1: Find inconsistent slots
        List<Object[]> inconsistentSlots = entityManager.createNativeQuery("""
                        select
                            cs.id,
                            cs.is_slot_based,
                            cs.max_slot,
                            cs.available_slot,
                            cs.max_duration_mins,
                            cs.available_duration_mins,
                            sum(al.duration) as total_duration
                        from court_schedule cs
                        join allocated_listings al on cs.id = al.court_schedule_id
                        where cs.active = true
                          and cs.support_ad_split = false
                          and cs.oucode = :ouCode
                          and cs.session_start > current_date
                        group by cs.id, cs.is_slot_based, cs.max_slot, cs.available_slot, cs.max_duration_mins, cs.available_duration_mins
                        having (
                            (cs.is_slot_based = true and cs.max_slot - sum(al.duration) != cs.available_slot)
                            or (cs.is_slot_based = false and cs.max_duration_mins - sum(al.duration) != cs.available_duration_mins)
                        )
                        """)
                .setParameter(OU_CODE, ouCode)
                .getResultList();

        int updateCount = 0;

        for (Object[] row : inconsistentSlots) {
            String courtScheduleId = (String) row[0];
            boolean isSlotBased = (boolean) row[1];
            int maxSlot = ((Number) row[2]).intValue();
            int availableSlot = ((Number) row[3]).intValue();
            int maxDurationMins = ((Number) row[4]).intValue();
            int availableDurationMins = ((Number) row[5]).intValue();
            int totalDuration = ((Number) row[6]).intValue();

            int correctedValue = isSlotBased ? (maxSlot - totalDuration) : (maxDurationMins - totalDuration);

            CourtSchedule courtSchedule = entityManager.find(CourtSchedule.class, courtScheduleId);

            if (courtSchedule != null) {
                if (isSlotBased && correctedValue != availableSlot) {
                    courtSchedule.setAvailableSlots(correctedValue);
                } else if (!isSlotBased && correctedValue != availableDurationMins) {
                    courtSchedule.setAvailableDuration(correctedValue);
                }
                courtSchedule.setUpdatedOn(new Date());
                entityManager.merge(courtSchedule);
                updateCount++;
            }
        }
        return updateCount;
    }


    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedulesByIdList(List<String> courtScheduleIds) {

        final javax.persistence.Query query = entityManager.createNativeQuery(COURT_SCHEDULE_ALL_FIELDS_QUERY_STRING.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_ALL);

        Map<String, Object> params = new HashMap<>();
        params.put("courtScheduleIds", courtScheduleIds);
        params.forEach(query::setParameter);

        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedulesResult = getCourtSchedulesResult(query);

        ModelMapper modelMapper = new ModelMapper();
        List<CourtScheduleJudiciary> judiciaryList = getCourtScheduleJudiciariesByCourtScheduleIds(courtScheduleIds);
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> domainJudiciaries =
                judiciaryList.stream()
                        .map(j -> modelMapper.map(j, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class))
                        .toList();
        courtSchedulesResult.forEach(schedule ->
                addJudiciaries(domainJudiciaries, schedule));
        return courtSchedulesResult;
    }

    private List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedulesResult(final javax.persistence.Query query) {
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
        final javax.persistence.Query query = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
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
            params.put("isDraft", courtScheduleRequestParam.isDraft());
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

    public List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria) {
        List<CourtSchedule> courtScheduleList = findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
                DateUtils.getDate(miFilterCriteria.getFromLocalDate()),
                DateUtils.getDate(miFilterCriteria.getToLocalDate()));
        return courtScheduleList.stream().map(CourtSchedulerConverter::convertToMi).toList();
    }

    public Result saveBookedSlots(final List<AllocatedSlot> slots, final boolean isProvisionalSlot, final boolean isSearchUpdate) {
        if (isSearchUpdate) {
            return bookSlotsWithoutCourtScheduleId(slots, isProvisionalSlot);
        } else {
            return bookSlotsWithCourtScheduleId(slots, isProvisionalSlot);
        }
    }

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

    private Result bookSlotsWithCourtScheduleId(final List<AllocatedSlot> slots, final boolean isProvisionalSlot) {
        final Optional<String> hearingId = getHearingId(slots);
        hearingId.ifPresent(this::releaseOldAllocatedListings);

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

    public Optional<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findCourtScheduleById(final String courtScheduleId) {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule entity = findBy(courtScheduleId);
        return ofNullable(entity)
                .filter(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule::isActive)
                .map(CourtSchedulerConverter::convertForOverbooking);
    }

    public List<Hearing> updateListHearingSlots(final RequestedSlots slots) {

        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing> allocatedListings = new ArrayList<>();
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules = new ArrayList<>();
        final ModelMapper modelMapper = new ModelMapper();

         List<Hearing> hearings = flattenHearingSlots(slots);

         hearings.forEach(hearing -> {
             uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule cs = this.findBy(hearing.getCourtScheduleId());
            if (cs != null) {
                //check slot based /duration based for deducting the available slots
                if (cs.isSlotBased()) {
                    cs.setAvailableSlots(cs.getAvailableSlots() - 1);
                } else cs.setAvailableDuration(cs.getAvailableDuration() - hearing.getDuration());

                boolean isCourtScheduleReleased = releaseOldListingsFromAllocatedListings(hearing.getHearingId());

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
                        .map(j -> modelMapper.map(j, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class))
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
        slots.forEach(this::save);

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

        // First, get total count without pagination
        String countQuery = buildFullQuery(requestParam).replace(GET_HEARING_SLOTS_QUERY_PAGINATION, "");
        List<CourtSchedule> allSchedules = executeQuery(countQuery, queryParamsForCount);
        LOGGER.info("GET_HEARING_SLOTS_QUERY_MANDATORY_PARAMS ******* allSchedules : {}", allSchedules);
        int totalCount = allSchedules.size();
        LOGGER.info("GET_HEARING_SLOTS_QUERY_MANDATORY_PARAMS ******* allSchedules count : {}", totalCount);

        // Then get paginated results
        String paginatedQuery = buildFullQuery(requestParam);
        List<CourtSchedule> paginatedSchedules = executeQuery(paginatedQuery, queryParamsForResult);
        LOGGER.info("GET_HEARING_SLOTS_QUERY_MANDATORY_PARAMS ******* paginatedSchedules : {}", paginatedSchedules);

        if (paginatedSchedules.isEmpty()) {
            return Pair.of(0, Collections.emptyList());
        }

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> domainSchedules =
                processScheduleEntities(paginatedSchedules);

        return Pair.of(totalCount, domainSchedules);
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

        query.append(GET_HEARING_SLOTS_QUERY_GROUP_BY)
                .append(GET_HEARING_SLOTS_QUERY_PAGINATION);

        return query.toString();
    }

    private List<CourtSchedule> executeQuery(String query, Map<String, Object> params) {
        javax.persistence.Query jpaQuery = entityManager.createNativeQuery(query, NATIVE_QUERY_COURT_SCHEDULE_MAPPING_SLOTS);

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
        ModelMapper modelMapper = new ModelMapper();
        List<CourtScheduleJudiciary> judiciaryList = getCourtScheduleJudiciaries(schedulesWithProfiles);
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> domainJudiciaries =
                judiciaryList.stream()
                        .map(j -> modelMapper.map(j, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class))
                        .toList();

        domainSchedules.forEach(schedule ->
                addJudiciaries(domainJudiciaries, schedule));
    }

    public List<CourtScheduleJudiciary> getCourtScheduleJudiciaries(List<CourtSchedule> courtScheduleList) {
        return getCourtScheduleJudiciariesByCourtScheduleIds(courtScheduleList.stream().map(CourtSchedule::getCourtScheduleId).toList());
    }

    public List<CourtScheduleJudiciary> getCourtScheduleJudiciariesByCourtScheduleIds(List<String> courtScheduleIdList) {
        final long startjudiciaryquery = System.nanoTime();
        javax.persistence.Query query = entityManager.createNativeQuery("select * from court_schedule_judiciary s where s.active = true and s.court_schedule_id in (:courtScheduleIdList)", CourtScheduleJudiciary.class);
        query.setParameter("courtScheduleIdList", courtScheduleIdList);
        List<CourtScheduleJudiciary> courtScheduleJudiciaryList = query.getResultList();
        final long endjudiciaryquery = System.nanoTime();
        LOGGER.info("PRF: Time taken for judiciaryquery : {}", (endjudiciaryquery - startjudiciaryquery) / 1000000);
        return courtScheduleJudiciaryList;
    }

    public List<CourtScheduleJudiciary> getCourtScheduleJudiciariesForProvisionalBooking(List<CourtSchedule> courtScheduleList) {
        final long startjudiciaryquery = System.nanoTime();
        List<CourtScheduleJudiciary> courtScheduleJudiciaryList = entityManager.createNativeQuery("select * from court_schedule_judiciary s where s.active = true and s.court_schedule_id in (:courtScheduleIdList) and court_listing_profile_id in (:courtListingProfileIdList)", CourtScheduleJudiciary.class)
                .setParameter("courtScheduleIdList", courtScheduleList.stream().map(CourtSchedule::getCourtScheduleId).toList())
                .setParameter("courtListingProfileIdList", courtScheduleList.stream().map(CourtSchedule::getListingProfileId).toList())
                .getResultList();
        final long endjudiciaryquery = System.nanoTime();
        LOGGER.info("PRF: Time taken for judiciaryquery : {}", (endjudiciaryquery - startjudiciaryquery) / 1000000);
        return courtScheduleJudiciaryList;
    }

    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> deleteCourtSchedule(List<String> courtScheduleIdList) {
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> errorDeleteCourtSchedules = new ArrayList<>();
        ModelMapper modelMapper = new ModelMapper();
        courtScheduleIdList.forEach(courtScheduleId -> {
            CourtSchedule courtSchedule = findBy(courtScheduleId);
            if (courtSchedule != null) {
                List<AllocatedListing> allocatedListings = allocatedListingRepository.findByCourtScheduleId(courtScheduleId);
                if (isNotEmpty(allocatedListings)) {
                    uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule domainCourtSchedule =
                            modelMapper.map(courtSchedule, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.class);
                    errorDeleteCourtSchedules.add(domainCourtSchedule);
                } else {
                    if (courtSchedule.getSessionDate().isAfter(now())) {
                        List<CourtScheduleJudiciary> courtScheduleJudiciaries = courtScheduleJudiciaryRepository.findByCourtScheduleId(courtScheduleId);
                        courtScheduleJudiciaries.forEach(courtScheduleJudiciary -> courtScheduleJudiciaryRepository.remove(courtScheduleJudiciary));
                        remove(courtSchedule);
                    }
                }
            }
        });
        return errorDeleteCourtSchedules;
    }

    @SuppressWarnings({"squid:S2077"})
    public int deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return entityManager()
                .createNativeQuery(DELETE_UNALLOCATED_COURT_SCHEDULE_QUERY)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setParameter("ouCodes", ouCodes)
                .executeUpdate();
    }

    @SuppressWarnings({"squid:S2077"})
    public int deleteUnAllocatedProvisionalEntries(final List<String> ouCodes) {
        return entityManager()
                .createNativeQuery(DELETE_UNALLOCATED_FORECAST_SLOT_QUERY)
                .setParameter("ouCodes", ouCodes)
                .executeUpdate();
    }

    @SuppressWarnings({"squid:S2077"})
    public int deleteSlots(final List<String> courtScheduleIds) {
        return entityManager()
                .createNativeQuery(DELETE_SLOTS_BY_IDS_QUERY)
                .setParameter("courtScheduleIds", courtScheduleIds)
                .executeUpdate();
    }

    @Query(value = "SELECT cs FROM CourtSchedule cs WHERE cs.ouCode IN :ouCodes AND cs.active = true AND cs.sessionDate BETWEEN :startDate AND :endDate")
    public abstract List<CourtSchedule> getExtractedCourtSchedules(@QueryParam("ouCodes") final List<String> ouCodes, @QueryParam("startDate") final LocalDate startDate, @QueryParam("endDate") final LocalDate endDate);

    @Query(value = "SELECT cs FROM CourtSchedule cs WHERE cs.ouCode IN :ouCodes AND cs.sessionDate BETWEEN :startDate AND :endDate")
    public abstract List<CourtSchedule> getExtractedCourtSchedulesForGhostRota(@QueryParam("ouCodes") final List<String> ouCodes, @QueryParam("startDate") LocalDate startDate, @QueryParam("endDate") LocalDate endDate);

    @Query(value = "SELECT cs FROM CourtSchedule cs WHERE cs.courtHouseId = :courtCentreId AND courtRoomId = :courtRoomId AND active = true AND businessType = :businessType AND cs.sessionDate BETWEEN :startDate AND :endDate")
    public abstract List<CourtSchedule> getSimilarSessions(@QueryParam("courtCentreId") final String courtCentreId, @QueryParam("courtRoomId") final String courtRoomId, @QueryParam(BUSINESS_TYPE) final String businessType, @QueryParam("startDate") LocalDate startDate, @QueryParam("endDate") LocalDate endDate);

    @Modifying
    @Query(value = "UPDATE CourtSchedule cs SET cs.active = false, cs.updatedOn = :updatedOn WHERE cs.courtScheduleId IN :courtScheduleIds AND cs.listingProfileId is not null")
    public abstract void deactivateSlots(@QueryParam("courtScheduleIds") final List<String> courtScheduleIds, @QueryParam("updatedOn") final Date updatedOn);

    public void releaseAllocatedSlotsOrDurationFromCourtSchedule(final List<AllocatedListing> allocatedListings) {
        allocatedListings.forEach(allocatedListing -> {
            CourtSchedule courtSchedule = this.findBy(allocatedListing.getCourtScheduleId());
            if (courtSchedule.isSlotBased()) {
                courtSchedule.setAvailableSlots(courtSchedule.getAvailableSlots() + 1);
            } else {
                courtSchedule.setAvailableDuration(courtSchedule.getAvailableDuration() + allocatedListing.getDuration());
            }
            this.save(courtSchedule);
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
                ModelMapper modelMapper = new ModelMapper();
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
                                .map(j -> modelMapper.map(j, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class))
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
        final javax.persistence.Query selectQuery = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
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
        final javax.persistence.Query selectQuery = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
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
        final javax.persistence.Query selectQuery = entityManager.createNativeQuery(queryString.toString(), NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW);
        params.forEach((key, value) -> {
            if (value != null) {
                selectQuery.setParameter(key, value);
            }
        });
        return selectQuery.getResultList();
    }

    protected void updateCourtSchedule(final List<AllocatedSlot> allocatedSlots) {
        allocatedSlots.forEach(allocatedSlot -> {
            CourtSchedule courtSchedule = this.findBy(allocatedSlot.getCourtScheduleId());
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
            this.save(courtSchedule);
        });
    }

    @Transactional
    protected void saveAllocatedListing(final List<AllocatedSlot> allocatedSlots) {
        allocatedSlots.forEach(allocatedSlot -> {
            // Check if record already exists
            List<AllocatedListing> existingListings = allocatedListingRepository.findByCourtScheduleIdAndHearingId(allocatedSlot.getCourtScheduleId(), allocatedSlot.getHearingId());
            if (existingListings.isEmpty()) {
                AllocatedListing allocatedListing = new AllocatedListing();
                allocatedListing.setId(UUID.randomUUID().toString());
                allocatedListing.setCourtScheduleId(allocatedSlot.getCourtScheduleId());
                allocatedListing.setBookingId(allocatedSlot.getBookingId());
                allocatedListing.setHearingId(allocatedSlot.getHearingId());
                allocatedListing.setOucode(allocatedSlot.getOuCode());
                allocatedListing.setCourtRoomId(Integer.parseInt(allocatedSlot.getCourtRoomId()));
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

    protected abstract List<CourtSchedule> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Date fromDate, Date toDate);

    private Optional<String> getHearingId(final List<AllocatedSlot> slots) {
        return slots.stream()
                .map(AllocatedSlot::getHearingId)
                .findFirst();
    }

    public void releaseOldAllocatedListings(final String hearingId) {

        final List<AllocatedListing> allocatedListings = getExistingAllocatedListings(hearingId);

        if (isNotEmpty(allocatedListings)) {
            releaseOldListingsFromAllocatedListings(hearingId);
            releaseCourtScheduleAllocatedSlotsForBookingId(allocatedListings);
            releaseAllocatedSlotsOrDurationFromCourtSchedule(allocatedListings);
        }
    }

    private List<AllocatedListing> getExistingAllocatedListings(final String hearingId) {
        return allocatedListingRepository.findByHearingId(hearingId);
    }

    private Map<String, List<SlotStartTime>> getCountBasedAllocatedListing(final Set<String> courtScheduleIds, final Map<String, CourtSchedule> courtScheduleMap) {
        LOGGER.info("number of courtScheduleIds : {}", courtScheduleIds.size());

        javax.persistence.Query query = entityManager
                .createNativeQuery("select court_schedule_id , hearing_start_time, count(*) as count,sum(duration) as duration from allocated_listings where court_schedule_id IN :courtScheduleId group by court_schedule_id , hearing_start_time");
        query.setParameter("courtScheduleId", courtScheduleIds);

        final long allocatedListingsStartTime = System.nanoTime();
        List<Object[]> queryResultList = query.getResultList();
        final long allocatedListingsEndTime = System.nanoTime();
        LOGGER.info("PRF: Time taken for allocatedListings : {} ", (allocatedListingsEndTime - allocatedListingsStartTime) / 1000000);


        final Map<String, List<Pair<Timestamp, Integer>>> hearingStartTimeMapByCourtSchedule = new HashMap<>();
        queryResultList.forEach(response -> {
            final String courtScheduleId = (String) response[0];
            final List<Pair<Timestamp, Integer>> allocatedHearingStartTimesWithCounts = hearingStartTimeMapByCourtSchedule.computeIfAbsent(courtScheduleId, k -> new ArrayList<>());
            final Timestamp hearingStartTime = (Timestamp) response[1];
            //find courtScheduleId in courtScheduleMap and check if it is slot based
            final int count = courtScheduleMap.get(courtScheduleId).isSlotBased()
                    ? toInt(response[2])
                    : toInt(response[3]);

            allocatedHearingStartTimesWithCounts.add(Pair.of(hearingStartTime, count));
        });
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

    @Query(value = "SELECT new uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo(entity.courtScheduleId, entity.ouCode, entity.createdOn) " +
            "from CourtSchedule entity where entity.courtRoomId = :courtRoomId " +
            "and entity.sessionDate = :sessionDate and entity.businessType = :businessType " +
            "and entity.courtSession = :courtSession", singleResult = SingleResultType.OPTIONAL, max = 1)
    public abstract CourtScheduleMatcherInfo findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(@QueryParam("courtRoomId") String courtRoomId,
                                                                                                           @QueryParam(SESSION_DATE) LocalDate sessionDate,
                                                                                                           @QueryParam(BUSINESS_TYPE) String businessType,
                                                                                                           @QueryParam("courtSession") String courtSession);

    @Query(value = "SELECT entity from CourtSchedule entity where entity.courtRoomId = :courtRoomId " +
            "and entity.sessionDate = :sessionDate and entity.businessType = :businessType " +
            "and entity.courtSession IN (:courtSessions) and entity.active = true " +
            "and entity.courtHouseId = :courtHouseId " +
            "and entity.courtScheduleId != :excludeCourtScheduleId")
    public abstract List<CourtSchedule> findDuplicateSessionsForAssignCourtroom(@QueryParam(COURT_ROOM_ID) String courtRoomId,
                                                                                @QueryParam(SESSION_DATE) LocalDate sessionDate,
                                                                                @QueryParam(BUSINESS_TYPE) String businessType,
                                                                                @QueryParam("courtSessions") List<String> courtSessions,
                                                                                @QueryParam("courtHouseId") String courtHouseId,
                                                                                @QueryParam("excludeCourtScheduleId") String excludeCourtScheduleId);

    public int deleteRedundantRotaData(final int numberOfDays) {
        return entityManager()
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

    @Query("SELECT cs FROM CourtSchedule cs WHERE cs.id IN :courtScheduleIds")
    public abstract List<CourtSchedule> findByCourtScheduleIds(@QueryParam("courtScheduleIds") List<String> courtScheduleIds);
}
