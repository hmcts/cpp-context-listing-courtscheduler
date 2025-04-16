package uk.gov.moj.cpp.courtscheduler.repository;

import static java.lang.String.format;
import static java.util.Optional.of;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.getOrElseDefaultSessionStartAndEndTimeIfEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toIsoString;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toMeridian;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toRoundedTimestamp;
import static uk.gov.moj.cpp.courtscheduler.utils.QueryConstants.EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE;

import uk.gov.moj.cpp.courtscheduler.converter.CourtSchedulerConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.SlotStartTime;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.repository.criteria.CourtScheduleCriteria;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    @Inject
    EntityManager entityManager;
    @Inject
    CourtScheduleCriteria courtScheduleCriteria;
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
                                               cs.available_slot,
                                               cs.available_duration_mins,
                                               cs.support_ad_split,
                                               cs.max_ad_morning_duration,
                                               cs.max_ad_afternoon_duration,
                                               cs.is_overbooking_allowed,
                                               cs.session_start_time,
                                               cs.session_end_time,
                                               cs.created_on,
                                               cs.updated_on,
            
             -- Adjusted calculation for morning bookings
                   COALESCE(
                           SUM(
                                   CASE
                                       WHEN cs.court_session = 'AD' AND cs.support_ad_split = true THEN
                                           CASE
                                               WHEN al.hearing_start_time < national_break_time
                                                   AND al.hearing_start_time +
                                                       CAST(al.duration || ' minutes' AS INTERVAL) <= national_break_time
                                                   THEN al.duration
                                               WHEN al.hearing_start_time < national_break_time
                                                   AND al.hearing_start_time +
                                                       CAST(al.duration || ' minutes' AS INTERVAL) > national_break_time
                                                   THEN EXTRACT(EPOCH FROM
                                                                (national_break_time - al.hearing_start_time)) /
                                                        60
                                               ELSE 0
                                               END
                                       WHEN al.hearing_start_time < national_break_time
                                           THEN al.duration
                                       ELSE 0
                                       END
                           ),
                           0
                   ) AS totalbookedformorning,
            
                   -- Adjusted calculation for afternoon bookings
                   COALESCE(
                           SUM(
                                   CASE
                                       WHEN cs.court_session = 'AD' AND cs.support_ad_split = true THEN
                                           CASE
                                               WHEN al.hearing_start_time >= national_break_time
                                                   THEN al.duration
                                               WHEN al.hearing_start_time < national_break_time
                                                   AND al.hearing_start_time +
                                                       CAST(al.duration || ' minutes' AS INTERVAL) > national_break_time
                                                   THEN EXTRACT(EPOCH FROM (al.hearing_start_time +
                                                                            CAST(al.duration || ' minutes' AS INTERVAL) -
                                                                            national_break_time)) / 60
                                               ELSE 0
                                               END
                                       WHEN al.hearing_start_time >= national_break_time
                                           THEN al.duration
                                       ELSE 0
                                       END
                           ),
                           0
                   ) AS totalbookedforafternoon,
            
                                               -- Total booked duration (sum of both)
                                               COALESCE(
                                                       SUM(al.duration),
                                                       0
                                               ) AS totalbooked
                                        FROM court_schedule cs
                                                 LEFT JOIN allocated_listings al ON cs.id = al.court_schedule_id
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
                    cs.is_overbooking_allowed
                ORDER BY cs.session_start, cs.court_house_name, cs.court_room_name, cs.court_session, cs.rota_business_type
            """;

    private static final String GET_HEARING_SLOTS_QUERY_PAGINATION = """
            LIMIT :pageSize OFFSET :offset
            """;

    private static final String NATIVE_QUERY_COURT_SCHEDULE_MAPPING_VIEW = "CourtScheduleEntityMappingForView";
    private static final String NATIVE_QUERY_COURT_SCHEDULE_MAPPING_SLOTS = "CourtScheduleEntityMappingForSlots";
    private static final int SLOT_DEFAULT = 1;

    private void logMultiplePersistedSchedules(List<CourtSchedule> persistedCourtSchedules, CourtSchedule courtSchedule) {
        if (persistedCourtSchedules.size() > 1) {
            LOGGER.info("having more than one persisted court schedule: {}", courtSchedule);
        }
    }

    private boolean hasMaxSlotsChanged(CourtSchedule persistedCourtSchedule, CourtSchedule courtSchedule) {
        return persistedCourtSchedule.getMaxSlots().intValue() != courtSchedule.getMaxSlots().intValue();
    }

    private boolean hasMaxDurationChanged(CourtSchedule persistedCourtSchedule, CourtSchedule courtSchedule) {
        return persistedCourtSchedule.getMaxDuration() > 0
                && persistedCourtSchedule.getMaxDuration().intValue() != courtSchedule.getMaxDuration().intValue();
    }

    private boolean hasNewMaxSlotsOrDuration(CourtSchedule courtSchedule) {
        return courtSchedule.getMaxSlots() > 0 || courtSchedule.getMaxDuration() > 0;
    }

    //update on Create when needed
    public CourtSchedule update(final CourtSchedule courtSchedule, final boolean isForRotaFile) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        courtScheduleCriteria.createMultipleSessionsCourtScheduleCriteria(courtSchedule, criteriaBuilder, criteriaQuery);
        final List<CourtSchedule> persistedCourtSchedules = entityManager.createQuery(criteriaQuery).getResultList();

        logMultiplePersistedSchedules(persistedCourtSchedules, courtSchedule);

        if (isNotEmpty(persistedCourtSchedules)) {
            final CourtSchedule persistedCourtSchedule = getCourtScheduleToBeUpdated(courtSchedule, isForRotaFile, persistedCourtSchedules);

            boolean hasMaxSlotsChanged = hasMaxSlotsChanged(persistedCourtSchedule, courtSchedule);
            boolean hasMaxDurationChanged = hasMaxDurationChanged(persistedCourtSchedule, courtSchedule);
            boolean hasNewMaxSlotsOrDuration = hasNewMaxSlotsOrDuration(courtSchedule);
            boolean hasSupportAdSplitChanged = courtSchedule.getSupportAdSplit()
                    && (persistedCourtSchedule.getMaxAdMorningDuration().intValue() != courtSchedule.getMaxAdMorningDuration().intValue()
                    || persistedCourtSchedule.getMaxAdAfternoonDuration().intValue() != courtSchedule.getMaxAdAfternoonDuration().intValue());
            boolean hasSameADSplit = Objects.equals(persistedCourtSchedule.getSupportAdSplit(), courtSchedule.getSupportAdSplit());

            if ((isForRotaFile || hasMaxSlotsChanged || hasMaxDurationChanged || hasNewMaxSlotsOrDuration || hasSupportAdSplitChanged) && hasSameADSplit) {
                if (Boolean.TRUE.equals(persistedCourtSchedule.getSupportAdSplit())) {
                    persistedCourtSchedule.setMaxAdMorningDuration(courtSchedule.getMaxAdMorningDuration());
                    persistedCourtSchedule.setMaxAdAfternoonDuration(courtSchedule.getMaxAdAfternoonDuration());
                } else {
                    persistedCourtSchedule.setMaxDuration(courtSchedule.getMaxDuration());
                }
                persistedCourtSchedule.setMaxSlots(courtSchedule.getMaxSlots());
                persistedCourtSchedule.setAvailableSlots(courtSchedule.getAvailableSlots());
                persistedCourtSchedule.setAvailableDuration(courtSchedule.getAvailableDuration());
                persistedCourtSchedule.setCreatedOn(persistedCourtSchedule.getCreatedOn());
                persistedCourtSchedule.setUpdatedOn(new Date());
                if (isForRotaFile) {
                    persistedCourtSchedule.setActive(true);
                }

                this.save(persistedCourtSchedule);
            }
            return courtSchedule;
        }
        return null;
    }

    private static CourtSchedule getCourtScheduleToBeUpdated(final CourtSchedule courtSchedule, final boolean isForRotaFile, final List<CourtSchedule> persistedCourtSchedules) {
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
                         uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule updateCourtSchedule,
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
        persistedCourtSchedule.setSupportAdSplit(updateCourtSchedule.isAllDaySplit());
        persistedCourtSchedule.setMaxAdMorningDuration(updateCourtSchedule.getMaxDurationForMorning());
        persistedCourtSchedule.setMaxAdAfternoonDuration(updateCourtSchedule.getMaxDurationForAfternoon());
        final DateUtils.sessionStartAndEndTime sessionStartAndEndTime = getOrElseDefaultSessionStartAndEndTimeIfEmpty(updateCourtSchedule.getSessionType(), updateCourtSchedule.getSessionStartTime(), updateCourtSchedule.getSessionEndTime());
        if (StringUtils.isNotEmpty(sessionStartAndEndTime.sessionStartTime()) && StringUtils.isNotEmpty(sessionStartAndEndTime.sessionEndTime())) {
            persistedCourtSchedule.setSessionStartTime(combineDateAndTime(persistedCourtSchedule.getSessionDate(), sessionStartAndEndTime.sessionStartTime()));
            persistedCourtSchedule.setSessionEndTime(combineDateAndTime(persistedCourtSchedule.getSessionDate(), sessionStartAndEndTime.sessionEndTime()));
        }
        persistedCourtSchedule.setNationalBreakTime(persistedCourtSchedule.getNationalBreakTime());
        persistedCourtSchedule.setUpdatedOn(new Date());
        persistedCourtSchedule.setIsOverbookingAllowed(updateCourtSchedule.isOverbookingAllowed());

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
        final List<CourtSchedule> resultList = query.getResultList();
        final List<String> courtScheduleIdsHavingHearingsBooked = resultList.stream()
                .filter(CourtSchedule::getHasHearingsBooked)
                .map(CourtSchedule::getCourtScheduleId).toList();

        final List<AllocatedListingEachBooked> allocatedListingEachBookedList = isNotEmpty(courtScheduleIdsHavingHearingsBooked) ?
                allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(courtScheduleIdsHavingHearingsBooked).stream().toList() : Collections.emptyList();

        return resultList.stream().map(courtSchedule -> CourtSchedulerConverter.convert(courtSchedule, allocatedListingEachBookedList)).toList();
    }

    private static void addFiltersForGetCourtSchedulesBy(final CourtScheduleRequestParam courtScheduleRequestParam, final StringBuilder queryString, final Map<String, Object> params) {
        if (courtScheduleRequestParam.courtCentreId() != null) {
            queryString.append("AND s.court_house_id = :courtHouseId ");
            params.put("courtHouseId", courtScheduleRequestParam.courtCentreId());
        }
        if (StringUtils.isNotBlank(courtScheduleRequestParam.courtRoomId())) {
            queryString.append("AND s.court_room_id = :courtRoomId ");
            params.put("courtRoomId", courtScheduleRequestParam.courtRoomId());
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

    private Result bookSlotsWithCourtScheduleId(final List<AllocatedSlot> slots, final boolean isProvisionalSlot) {
        final Optional<String> hearingId = getHearingId(slots);
        hearingId.ifPresent(this::releaseOldAllocatedListings);

        final List<AllocatedSlot> updateAllocatedSlots = getUpdatedAllocatedSlots(slots, false);

        if (isNotEmpty(updateAllocatedSlots)) {
            persistHearingSlots(slots, isProvisionalSlot, updateAllocatedSlots);
            return Result.SUCCESS();
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
        return Result.SUCCESS();
    }

    private void persistHearingSlots(final List<AllocatedSlot> slots, final boolean isProvisionalSlot, final List<AllocatedSlot> updateAllocatedSlots) {
        updateCourtSchedule(updateAllocatedSlots);
        saveAllocatedListing(updateAllocatedSlots);
        if (isProvisionalSlot) {
            deleteProvisionalBooking(slots.get(0).getBookingId());
        }
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

    private Map<String, Object> buildQueryParams(HearingSlotRequestParam requestParam, boolean isCountQuery) {
        Map<String, Object> params = new HashMap<>();
        params.put("panelType", List.of(requestParam.panel().split(",")));
        params.put("sessionStart", LocalDate.parse(requestParam.sessionStartDate()));
        params.put("sessionEnd", LocalDate.parse(requestParam.sessionEndDate()));

        if (StringUtils.isNotBlank(requestParam.ouCode())) {
            params.put("ouCode", requestParam.ouCode());
        }

        if (StringUtils.isNotBlank(requestParam.oucodeL2Code())) {
            params.put("operationalUnit", requestParam.oucodeL2Code());
        }

        // Add optional parameters only if they are present
        if (StringUtils.isNotBlank(requestParam.courtRoomId())) {
            params.put("courtRoomId", requestParam.courtRoomId());
        }

        if (StringUtils.isNotBlank(requestParam.businessType())) {
            params.put(BUSINESS_TYPE, requestParam.businessType());
        }

        if (StringUtils.isNotBlank(requestParam.courtSession())) {
            params.put("courtSession", List.of(requestParam.courtSession().split(",")));
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
        }

        if (StringUtils.isNotBlank(requestParam.courtSession())) {
            query.append(" AND cs.court_session in (:courtSession)");
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
        LOGGER.info("BRS: Time taken for mapping : {}", (mappingEndTime - mappingStartTime) / 1000000);

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
        final long startjudiciaryquery = System.nanoTime();
        javax.persistence.Query query = entityManager.createNativeQuery("select * from court_schedule_judiciary s where s.active = true and s.court_schedule_id in (:courtScheduleIdList)", CourtScheduleJudiciary.class);
        query.setParameter("courtScheduleIdList", courtScheduleList.stream().map(CourtSchedule::getCourtScheduleId).toList());
        List<CourtScheduleJudiciary> courtScheduleJudiciaryList = query.getResultList();
        final long endjudiciaryquery = System.nanoTime();
        LOGGER.info("BRS: Time taken for judiciaryquery : {}", (endjudiciaryquery - startjudiciaryquery) / 1000000);
        return courtScheduleJudiciaryList;
    }

    public List<CourtScheduleJudiciary> getCourtScheduleJudiciariesForProvisionalBooking(List<CourtSchedule> courtScheduleList) {
        final long startjudiciaryquery = System.nanoTime();
        List<CourtScheduleJudiciary> courtScheduleJudiciaryList = entityManager.createNativeQuery("select * from court_schedule_judiciary s where s.active = true and s.court_schedule_id in (:courtScheduleIdList) and court_listing_profile_id in (:courtListingProfileIdList)", CourtScheduleJudiciary.class)
                .setParameter("courtScheduleIdList", courtScheduleList.stream().map(CourtSchedule::getCourtScheduleId).toList())
                .setParameter("courtListingProfileIdList", courtScheduleList.stream().map(CourtSchedule::getListingProfileId).toList())
                .getResultList();
        final long endjudiciaryquery = System.nanoTime();
        LOGGER.info("BRS: Time taken for judiciaryquery : {}", (endjudiciaryquery - startjudiciaryquery) / 1000000);
        return courtScheduleJudiciaryList;
    }

    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> deleteCourtSchedule(List<String> courtScheduleIdList) {
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> errorDeleteCourtSchedules = new ArrayList<>();
        ModelMapper modelMapper = new ModelMapper();
        courtScheduleIdList.forEach(courtScheduleId -> {
            List<AllocatedListing> allocatedListings = allocatedListingRepository.findByCourtScheduleId(courtScheduleId);
            CourtSchedule courtSchedule = findBy(courtScheduleId);
            if (courtSchedule != null) {
                if (isNotEmpty(allocatedListings)) {
                    uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule domainCourtSchedule =
                            modelMapper.map(courtSchedule, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.class);
                    errorDeleteCourtSchedules.add(domainCourtSchedule);
                } else {
                    List<CourtScheduleJudiciary> courtScheduleJudiciaries = courtScheduleJudiciaryRepository.findByCourtScheduleId(courtScheduleId);
                    courtScheduleJudiciaries.forEach(courtScheduleJudiciary -> courtScheduleJudiciaryRepository.remove(courtScheduleJudiciary));
                    remove(courtSchedule);
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

    protected void releaseAllocatedSlotsOrDurationFromCourtSchedule(final List<AllocatedListing> allocatedListings) {
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

    protected void releaseOldListingsFromAllocatedListings(final String hearingId) {
        List<AllocatedListing> allocatedListings = this.allocatedListingRepository.findByHearingId(hearingId);
        allocatedListings.forEach(allocatedListing -> this.allocatedListingRepository.remove(allocatedListing));
    }

    private List<AllocatedSlot> getUpdatedAllocatedSlots(final List<AllocatedSlot> allocatedSlots, final boolean isSearchUpdate) {

        final List<AllocatedSlot> matchedSlots = new ArrayList<>();

        for (final AllocatedSlot allocatedSlot : allocatedSlots) {

            if (isBlank(allocatedSlot.getCourtScheduleId())) {
                allocatedSlot.setCourtScheduleId(null);
            }
            final String sessionFromHearingStartTime = allocatedSlot.getHearingStartTime() == null ? allocatedSlot.getSession() : toMeridian(allocatedSlot.getHearingStartTime());
            final CourtSchedule slotsFound = getCourtScheduleIdAndSlotBased(allocatedSlot.getOuCode(), LocalDate.parse(allocatedSlot.getSessionDate()), sessionFromHearingStartTime, allocatedSlot.getCourtRoomId(), allocatedSlot.getCourtScheduleId(), isSearchUpdate);

            if (slotsFound != null) {
                final Pair<Optional<String>, Boolean> pair = Pair.of(of(slotsFound.getCourtScheduleId()), slotsFound.isSlotBased());
                allocatedSlot.setCourtRoomId(String.valueOf(slotsFound.getCourtRoomNumber()));
                allocatedSlot.setCourtRoomUUId(String.valueOf(slotsFound.getCourtRoomId()));
                allocatedSlot.setCourtScheduleId(slotsFound.getCourtScheduleId());
                allocatedSlot.setCourtRoom(slotsFound.getCourtRoomName());
                updateCourtScheduleAndSlotBased(allocatedSlot, pair);
                matchedSlots.add(allocatedSlot);
            } else {
                LOGGER.error(format("Could not update slot as court schedule id not found for combination %s, %s, %s, %s", allocatedSlot.getOuCode(), allocatedSlot.getSessionDate(), allocatedSlot.getSession(), allocatedSlot.getCourtRoomId()));
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
            LOGGER.info(format("Trying to find a match with these params for SPI : ouCode: %s sessionDate: %s session: %s courtRoomNumber:%s ", ouCode, sessionDate, session, courtRoomNumber));
            courtScheduleList = getCourtScheduleForSearchUpdateFilterCriteria(ouCode, sessionDate, session, courtRoomNumber, true);
            LOGGER.info(format("found %d matches for SPI", courtScheduleList.size()));
            if (CollectionUtils.isEmpty(courtScheduleList)) {
                LOGGER.info(format("Trying to find a match with these params for SPI : ouCode: %s sessionDate: %s session: %s ", ouCode, sessionDate, session));
                courtScheduleList = getCourtScheduleForSearchUpdateFilterCriteria(ouCode, sessionDate, session, courtRoomNumber, false);
                LOGGER.info(format("found %d matches SPI", courtScheduleList.size()));
            }
        } else {
            courtScheduleCriteria.createFetchCourtScheduleEitherByidOrFiltersCriteria(courtScheduleId, ouCode, sessionDate, session, courtRoomNumber, criteriaBuilder, criteriaQuery);
            LOGGER.info(format("Trying to find a match with these params : ouCode: %s sessionDate: %s session: %s courtRoomNumber:%s courtScheduleId: %s  ", ouCode, sessionDate, session, courtRoomNumber, courtScheduleId));
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
        LOGGER.info("Criteria Query Params: ouCode {} sessionDate {} courtSessionString {} courtRoomNumber {}", ouCode, sessionDate, courtSessionString, courtRoomNumber);
        final List<String> businessType = List.of("REM", "NGAP", "GAP");
        final List<String> courtSession = List.of("AD", courtSessionString);

        StringBuilder queryString = new StringBuilder("SELECT distinct s.*, case when al.id is not null then true else false end as hasHearingsBooked FROM court_schedule s left outer join  allocated_listings al on(s.id = al.court_schedule_id) WHERE s.active = true ");
        Map<String, Object> params = new HashMap<>();

        queryString.append("AND s.oucode = :ouCode ");
        params.put("ouCode", ouCode);
        queryString.append("AND s.session_start = :sessionDate ");
        params.put("sessionDate", sessionDate);
        queryString.append("AND s.court_session IN (:courtSession) ");
        params.put("courtSession", courtSession);
        queryString.append("AND s.rota_business_type IN (:businessType) ");
        params.put(BUSINESS_TYPE, businessType);

        if (isNarrowSearch && StringUtils.isNotBlank(courtRoomNumber)) {
            queryString.append("AND s.court_room_number = :courtRoomNumber ");
            params.put("courtRoomNumber", Integer.parseInt(courtRoomNumber));
        }

        queryString.append("order by s.rota_business_type desc, s.court_room_number asc");
        LOGGER.info("Criteria Query Params: queryString {}", queryString);
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
            AllocatedListing allocatedListing = new AllocatedListing();
            allocatedListing.setId(UUID.randomUUID().toString());
            allocatedListing.setCourtScheduleId(allocatedSlot.getCourtScheduleId());
            allocatedListing.setBookingId(allocatedSlot.getBookingId());
            allocatedListing.setHearingId(allocatedSlot.getHearingId());
            allocatedListing.setOucode(allocatedSlot.getOuCode());
            allocatedListing.setCourtRoomId(Integer.parseInt(allocatedSlot.getCourtRoomId()));
            allocatedListing.setDuration(allocatedSlot.isSlotBased() ? SLOT_DEFAULT : allocatedSlot.getDuration());
            allocatedListing.setHearingStartTime(toRoundedTimestamp(allocatedSlot.getHearingStartTime()));
            LOGGER.info("bookSlotsWithoutCourtScheduleId saveAllocatedListing {}", allocatedListing);
            this.allocatedListingRepository.save(allocatedListing);
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
            releaseAllocatedSlotsOrDurationFromCourtSchedule(allocatedListings);

            releaseCourtScheduleAllocatedSlotsForBookingId(allocatedListings);

            releaseOldListingsFromAllocatedListings(hearingId);
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
        LOGGER.info("BRS: Time taken for allocatedListings : {} ", (allocatedListingsEndTime - allocatedListingsStartTime) / 1000000);


        final Map<String, List<Pair<Timestamp, Integer>>> hearingStartTimeMapByCourtSchedule = new HashMap<>();
        queryResultList.forEach(response -> {
            final String courtScheduleId = (String) response[0];
            final List<Pair<Timestamp, Integer>> allocatedHearingStartTimesWithCounts = hearingStartTimeMapByCourtSchedule.computeIfAbsent(courtScheduleId, k -> new ArrayList<>());
            final Timestamp hearingStartTime = (Timestamp) response[1];
            //find courtScheduleId in courtScheduleMap and check if it is slot based
            final int count = courtScheduleMap.get(courtScheduleId).isSlotBased() ? ((BigInteger) response[2]).intValue() : ((BigDecimal) response[3]).intValue();
            allocatedHearingStartTimesWithCounts.add(Pair.of(hearingStartTime, count));
        });
        return processSlotStartTimes(courtScheduleMap, hearingStartTimeMapByCourtSchedule);
    }

    private static Map<String, List<SlotStartTime>> processSlotStartTimes(final Map<String, CourtSchedule> courtScheduleMap, final Map<String, List<Pair<Timestamp, Integer>>> hearingStartTimeMapByCourtSchedule) {
        final Map<String, List<SlotStartTime>> resultStringListMap = new HashMap<>();
        courtScheduleMap.keySet().forEach(courtScheduleId -> {
            final CourtSchedule courtSchedule = courtScheduleMap.get(courtScheduleId);
            final Date sessionStartTime = courtSchedule.getSessionStartTime();
            final Date sessionEndTime = courtSchedule.getSessionEndTime();
            final List<Pair<Timestamp, Integer>> courtScheduleAllocatedPair = hearingStartTimeMapByCourtSchedule.get(courtScheduleId);
            final LocalDateTime sessionStartDateTime = sessionStartTime.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
            final LocalDateTime sessionEndDateTime = sessionEndTime.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime();
            final int sessionStartHour = sessionStartDateTime.getHour();
            final int sessionStartMinute = sessionStartDateTime.getMinute();
            final int sessionEndHour = sessionEndDateTime.getHour();
            final int sessionEndMinute = sessionEndDateTime.getMinute();
            final AtomicInteger nextMinutePart = new AtomicInteger(sessionStartMinute);
            final boolean slotBased = courtSchedule.isSlotBased();
            final List<SlotStartTime> slotStartTimes = processSlotStartTimes(sessionStartHour, sessionEndHour, sessionEndMinute, nextMinutePart, courtScheduleAllocatedPair, sessionStartDateTime, slotBased);
            resultStringListMap.put(courtScheduleId, slotStartTimes);

        });
        return resultStringListMap;
    }

    private static List<SlotStartTime> processSlotStartTimes(final int sessionStartHour,
                                                             final int sessionEndHour,
                                                             final int sessionEndMinute,
                                                             final AtomicInteger nextMinutePart,
                                                             final List<Pair<Timestamp, Integer>> courtScheduleAllocatedPair,
                                                             final LocalDateTime sessionStartDateTime, final boolean slotBased) {
        final List<SlotStartTime> slotStartTimes = new ArrayList<>();
        for (int currentHour = sessionStartHour; currentHour <= sessionEndHour; currentHour++) {
            if ((currentHour == sessionEndHour && sessionEndMinute > 0) || currentHour < sessionEndHour) {
                decideNextMinutePart(currentHour, sessionStartHour, sessionEndHour, nextMinutePart, sessionEndMinute);
                final List<Pair<Timestamp, Integer>> filteredList = getCourtScheduleAllocatedPairForHourlyPart(courtScheduleAllocatedPair, currentHour, sessionEndHour, nextMinutePart);
                final Pair<Integer, Integer> startAndEndTimeForHourlyPart = getStartAndEndTimeForHourlyPart(currentHour, sessionStartHour, sessionEndHour, sessionEndMinute, nextMinutePart);
                final int slotEndHour = (currentHour == sessionEndHour && sessionEndMinute > 0) ? sessionEndHour : currentHour + 1;
                final LocalDateTime slotStartTime = LocalDateTime.of(sessionStartDateTime.toLocalDate(), LocalTime.of(currentHour, startAndEndTimeForHourlyPart.getLeft()));
                final LocalDateTime slotEndTime = LocalDateTime.of(sessionStartDateTime.toLocalDate(), LocalTime.of(slotEndHour, startAndEndTimeForHourlyPart.getRight()));
                final AtomicInteger count = new AtomicInteger(0);
                filteredList.forEach(pair -> count.set(count.get() + pair.getRight()));
                SlotStartTime responseSlotStartTime = new SlotStartTime();
                responseSlotStartTime.setSessionStartTime(toIsoString(slotStartTime));
                responseSlotStartTime.setSessionEndTime(toIsoString(slotEndTime));
                responseSlotStartTime.setCount(count.get());
                setHearingTimestamp(slotBased, currentHour, filteredList, slotEndHour, responseSlotStartTime);
                slotStartTimes.add(responseSlotStartTime);
            }
        }
        return slotStartTimes;
    }

    private static void setHearingTimestamp(final boolean slotBased, final int currentHour, final List<Pair<Timestamp, Integer>> filteredList, final int slotEndHour, final SlotStartTime responseSlotStartTime) {
        if (slotBased) {
            final int slotStartHour = currentHour;
            filteredList.forEach(timestampIntegerPair -> {
                final int hearingHour = timestampIntegerPair.getLeft().toLocalDateTime().getHour();
                if (hearingHour >= slotStartHour && hearingHour < slotEndHour)
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
        final int endTimeMinute = currentHour == sessionEndHour ? sessionEndMinute : 0;

        return Pair.of(startTimeMinute, endTimeMinute);
    }

    private static List<Pair<Timestamp, Integer>> getCourtScheduleAllocatedPairForHourlyPart(final List<Pair<Timestamp, Integer>> courtScheduleAllocatedPair, final int currentHourPart, final int sessionEndHour, final AtomicInteger nextMinutePart) {
        if (isEmpty(courtScheduleAllocatedPair)) {
            return Collections.emptyList();
        }
        return courtScheduleAllocatedPair.stream()
                .filter(pair -> {
                    final int pairHour = pair.getLeft().toLocalDateTime().getHour();
                    final int pairMinute = pair.getLeft().toLocalDateTime().getMinute();
                    return pairHour == currentHourPart &&
                            ((currentHourPart < sessionEndHour && pairMinute >= nextMinutePart.get())
                                    || currentHourPart == sessionEndHour && pairMinute <= nextMinutePart.get());
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
        Optional.of(courtScheduleJudiciaries.stream()
                        .filter(courtScheduleJudiciary -> courtScheduleJudiciary.getCourtScheduleId().equals(courtSchedule.getCourtScheduleId()))
                        .toList())
                .ifPresent(courtSchedule.getJudiciaries()::addAll);
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
                                                                                                           @QueryParam("sessionDate") LocalDate sessionDate,
                                                                                                           @QueryParam(BUSINESS_TYPE) String businessType,
                                                                                                           @QueryParam("courtSession") String courtSession);

    public int deleteRedundantRotaData(final int numberOfDays) {
        return entityManager()
                .createNativeQuery(DELETE_REDUNDANT_ROTA_DATA)
                .setParameter("numberOfDays", numberOfDays)
                .executeUpdate();
    }
}
