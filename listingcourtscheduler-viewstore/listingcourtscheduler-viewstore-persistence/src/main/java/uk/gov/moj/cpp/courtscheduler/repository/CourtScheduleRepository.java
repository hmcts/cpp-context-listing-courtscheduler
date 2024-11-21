package uk.gov.moj.cpp.courtscheduler.repository;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Optional.of;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toIsoString;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toMeridian;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toRoundedTimestamp;
import static uk.gov.moj.cpp.courtscheduler.utils.QueryConstants.EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE;

import uk.gov.moj.cpp.courtscheduler.converter.CourtSchedulerConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.SlotStartTime;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.exception.CourtScheduleIdNotMatchingException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.repository.criteria.CourtScheduleCriteria;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

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

    public static final String GET_ALLOCATED_SLOTS_FOR_COURT_SCHEDULE_QUERY = """
            SELECT * FROM court_schedule cs WHERE cs.oucode in (:ouCodes)
            AND cs.session_start between :startDate AND :endDate
            AND EXISTS (SELECT 1 FROM allocated_listings al WHERE al.court_schedule_id = cs.id
            AND al.hearing_start_time BETWEEN :startDate AND :hearingStartTimeEndBoundary);
            """;

    private static final int SLOT_DEFAULT = 1;


    //update on Create when needed
    public CourtSchedule update(final CourtSchedule courtSchedule, final boolean isForRotaFile) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        courtScheduleCriteria.createMultipleSessionsCourtScheduleCriteria(courtSchedule, criteriaBuilder, criteriaQuery);
        final List<CourtSchedule> persistedCourtSchedules = entityManager.createQuery(criteriaQuery).getResultList();
        if (persistedCourtSchedules.size() > 1) {
            LOGGER.info("having more than one persisted court schedule: {}", courtSchedule);
        }
        if (isNotEmpty(persistedCourtSchedules)) {
            final CourtSchedule persistedCourtSchedule = getCourtScheduleToBeUpdated(courtSchedule, isForRotaFile, persistedCourtSchedules);

            if (isForRotaFile || (persistedCourtSchedule.getMaxSlots() > 0
                    && persistedCourtSchedule.getMaxSlots().intValue() != courtSchedule.getMaxSlots().intValue())
                    || (persistedCourtSchedule.getMaxDuration() > 0
                    && persistedCourtSchedule.getMaxDuration().intValue() != courtSchedule.getMaxDuration().intValue())
                    || (courtSchedule.getMaxSlots() > 0 || courtSchedule.getMaxDuration() > 0)) {
                persistedCourtSchedule.setMaxSlots(courtSchedule.getMaxSlots());
                persistedCourtSchedule.setMaxDuration(courtSchedule.getMaxDuration());
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
                            && courtScheduleFound.getPanel().equals(courtSchedule.getPanel()))
                    .findAny().orElse(persistedCourtSchedule);
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
        persistedCourtSchedule.setUpdatedOn(new Date());

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
        final javax.persistence.Query query = entityManager.createNativeQuery(queryString.toString(), "CourtScheduleEntityMapping");
        query.setParameter("courtScheduleId", courtScheduleId);
        return (CourtSchedule) query.getSingleResult();
    }

    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedulesBy(final CourtScheduleRequestParam courtScheduleRequestParam) {
        StringBuilder queryString = new StringBuilder("SELECT distinct s.*, case when al.id is not null then true else false end as hasHearingsBooked FROM court_schedule s left outer join  allocated_listings al on(s.id = al.court_schedule_id)  WHERE active = true ");
        Map<String, Object> params = new HashMap<>();
        if (courtScheduleRequestParam.courtCentreId() != null) {
            {
                queryString.append("AND s.court_house_id = :courtHouseId ");
                params.put("courtHouseId", courtScheduleRequestParam.courtCentreId());
            }
            if (StringUtils.isNotBlank(courtScheduleRequestParam.courtRoomId())) {
                queryString.append("AND s.court_room_id = :courtRoomId ");
                params.put("courtRoomId", courtScheduleRequestParam.courtRoomId());
            }
            if (courtScheduleRequestParam.businessType() != null) {
                queryString.append("AND s.rota_business_type = :businessType ");
                params.put("businessType", courtScheduleRequestParam.businessType());
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
                params.put("pageNumber", Integer.parseInt(courtScheduleRequestParam.pageNumber())-1);
            }
        }
        final javax.persistence.Query query = entityManager.createNativeQuery(queryString.toString(), "CourtScheduleEntityMapping");
        params.forEach((key, value) -> {
            if (value != null) {
                query.setParameter(key, value);
            }
        });
        final List<CourtSchedule> resultList = query.getResultList();
        return resultList.stream().map(CourtSchedulerConverter::convert).toList();
    }

    public List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria) {
        List<CourtSchedule> courtScheduleList = findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
                DateUtils.getDate(miFilterCriteria.getFromLocalDate()),
                DateUtils.getDate(miFilterCriteria.getToLocalDate()));
        return courtScheduleList.stream().map(CourtSchedulerConverter::convertToMi).toList();
    }

    public void saveBookedSlots(final List<AllocatedSlot> slots, final boolean isProvisionalSlot) {
        final Optional<String> hearingId = getHearingId(slots);

        hearingId.ifPresent(this::releaseOldAllocatedListings);

        final List<AllocatedSlot> updateAllocatedSlots = getUpdatedAllocatedSlots(slots);
        //check if any of the slots has courtScheduleId
        final boolean isSPIOrFirstTimeAllocation = updateAllocatedSlots.stream().noneMatch(slot -> nonNull(slot.getCourtScheduleId()));

        if (isNotEmpty(updateAllocatedSlots)) {
            updateCourtSchedule(updateAllocatedSlots);
            saveAllocatedListing(updateAllocatedSlots);
            if (isProvisionalSlot) {
                deleteProvisionalBooking(slots.get(0).getBookingId());
            }
        } else {
            if (isSPIOrFirstTimeAllocation) {
                LOGGER.error(format("courtScheduleId matching for non-provisional slot(s) has been failed,please check the logs. hearingid : %s", slots.get(0).getHearingId()));
            } else {
                throw new CourtScheduleIdNotMatchingException(format("courtScheduleId matching for non-provisional slot(s) has been failed,please check the logs. hearingId : %s", slots.get(0).getHearingId()));
            }
        }
    }

    public Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> getCourtSchedules(final HearingSlotRequestParam hearingSlotRequestParam) {
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules = new ArrayList<>();
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> courtScheduleJudiciaries = new ArrayList<>();
        final Set<String> courtScheduleIds = new TreeSet<>();
        final int pageSize = Integer.parseInt(hearingSlotRequestParam.pageSize());
        final int pageNumber = Integer.parseInt(hearingSlotRequestParam.pageNumber());

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        courtScheduleCriteria.createHearingSlotsCourtScheduleCriteria(hearingSlotRequestParam, criteriaBuilder, criteriaQuery);
        List<CourtSchedule> courtScheduleList = entityManager.createQuery(criteriaQuery).setFirstResult((pageNumber - 1) * pageSize).setMaxResults(pageSize).getResultList();
        List<CourtSchedule> totalCourtScheduleList = entityManager.createQuery(criteriaQuery).getResultList();
        final long criteriaQuerystartTime = System.nanoTime();
        courtScheduleList.forEach(e -> courtScheduleIds.add(e.getCourtScheduleId()));
        final long criteriaQueryendTime = System.nanoTime();
        LOGGER.info("BRS: Time taken for criteriaQuery : {} resultsize {}", (criteriaQueryendTime - criteriaQuerystartTime) / 1000000, totalCourtScheduleList.size());
        int resultSize = totalCourtScheduleList.size();

        if (resultSize > 0) {
            final Map<String, List<SlotStartTime>> slotStartTimeList = getCountBasedAllocatedListing(courtScheduleIds);
            ModelMapper modelMapper = new ModelMapper();
            final long mappingStartTime = System.nanoTime();
            courtScheduleList.forEach(courtSchedule -> courtSchedules.add(modelMapper.map(courtSchedule, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.class)));
            //judiciary details are not required for provisional bookings without listing profile(ghost rota)
            final List<CourtSchedule> courtSchedulesWithProfileId = courtScheduleList.stream().filter(courtSchedule -> courtSchedule.getListingProfileId() != null).toList();
            if (isNotEmpty(courtSchedulesWithProfileId)) {
                final List<CourtScheduleJudiciary> courtScheduleJudiciaryList = getCourtScheduleJudiciaries(courtSchedulesWithProfileId);
                courtScheduleJudiciaryList.forEach(courtScheduleJudiciary -> courtScheduleJudiciaries.add(modelMapper.map(courtScheduleJudiciary, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class)));
            }
            courtSchedules.forEach(courtSchedule -> {
                        addJudiciaries(courtScheduleJudiciaries, courtSchedule);
                        addSlotStartTimes(slotStartTimeList, courtSchedule);
                    }
            );
            final long mappingEndTime = System.nanoTime();
            LOGGER.info("BRS: Time taken for mapping : {}", (mappingEndTime - mappingStartTime) / 1000000);
        }
        return Pair.of(resultSize, courtSchedules);
    }

    public List<CourtScheduleJudiciary> getCourtScheduleJudiciaries(List<CourtSchedule> courtScheduleList) {
        final long startjudiciaryquery = System.nanoTime();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryList = entityManager.createNativeQuery("select * from court_schedule_judiciary s where s.active = true and s.court_schedule_id in (:courtScheduleIdList) and court_listing_profile_id in (:courtListingProfileIdList)", CourtScheduleJudiciary.class)
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
            if (allocatedListings != null && !allocatedListings.isEmpty()) {
                uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule domainCourtSchedule =
                        modelMapper.map(courtSchedule, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.class);
                errorDeleteCourtSchedules.add(domainCourtSchedule);
            } else {
                List<CourtScheduleJudiciary> courtScheduleJudiciaries = courtScheduleJudiciaryRepository.findByCourtScheduleId(courtScheduleId);
                courtScheduleJudiciaries.forEach(courtScheduleJudiciary -> courtScheduleJudiciaryRepository.remove(courtScheduleJudiciary));
                remove(courtSchedule);
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
    public abstract List<CourtSchedule> getSimilarSessions(@QueryParam("courtCentreId") final String courtCentreId, @QueryParam("courtRoomId") final String courtRoomId, @QueryParam("businessType") final String businessType, @QueryParam("startDate") LocalDate startDate, @QueryParam("endDate") LocalDate endDate);

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

    private List<AllocatedSlot> getUpdatedAllocatedSlots(final List<AllocatedSlot> allocatedSlots) {

        final List<AllocatedSlot> matchedSlots = new ArrayList<>();

        for (final AllocatedSlot allocatedSlot : allocatedSlots) {

            if (isBlank(allocatedSlot.getCourtScheduleId())) {
                allocatedSlot.setCourtScheduleId(null);
            }
            final String sessionFromHearingStartTime = allocatedSlot.getHearingStartTime() == null ? allocatedSlot.getSession() : toMeridian(allocatedSlot.getHearingStartTime());
            final Pair<Optional<String>, Boolean> pair = getCourtScheduleIdAndSlotBased(allocatedSlot.getOuCode(), LocalDate.parse(allocatedSlot.getSessionDate()), sessionFromHearingStartTime, allocatedSlot.getCourtRoomId(), allocatedSlot.getCourtScheduleId());

            if (pair != null) {
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

    private Pair<Optional<String>, Boolean> getCourtScheduleIdAndSlotBased(final String ouCode,
                                                                           final LocalDate sessionDate,
                                                                           final String session,
                                                                           final String courtRoomNumber,
                                                                           final String courtScheduleId) {

        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        courtScheduleCriteria.createFetchCourtScheduleEitherByidOrFiltersCriteria(courtScheduleId, ouCode, sessionDate, session, courtRoomNumber, criteriaBuilder, criteriaQuery);
        LOGGER.info(format("Trying to find a match with these params : ouCode: %s sessionDate: %s session: %s courtRoomNumber:%s courtScheduleId: %s  ", ouCode, sessionDate, session, courtRoomNumber, courtScheduleId));
        List<CourtSchedule> courtScheduleList =
                entityManager.createQuery(criteriaQuery).getResultList();
        LOGGER.info(format("found %d matches", courtScheduleList.size()));

        if (CollectionUtils.isNotEmpty(courtScheduleList)) {
            return Pair.of(of(courtScheduleList.get(0).getCourtScheduleId()), courtScheduleList.get(0).isSlotBased());
        }
        return null;
    }

    protected void updateCourtSchedule(final List<AllocatedSlot> allocatedSlots) {
        allocatedSlots.forEach(allocatedSlot -> {
            CourtSchedule courtSchedule = this.findBy(allocatedSlot.getCourtScheduleId());
            if (courtSchedule.isSlotBased()) {
                Integer availableSlots = courtSchedule.getAvailableSlots();
                courtSchedule.setAvailableSlots(availableSlots - 1);
            } else {
                courtSchedule.setAvailableDuration(courtSchedule.getAvailableDuration() - allocatedSlot.getDuration());
            }
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

    private Map<String, List<SlotStartTime>> getCountBasedAllocatedListing(final Set<String> courtScheduleIds) {
        final Map<String, List<SlotStartTime>> resultStringListMap = new HashMap<>();
        LOGGER.info("number of courtScheduleIds : {}", courtScheduleIds.size());

        javax.persistence.Query query = entityManager
                .createNativeQuery("select court_schedule_id , hearing_start_time, count(*) as count from allocated_listings where court_schedule_id IN :courtScheduleId group by court_schedule_id , hearing_start_time");
        query.setParameter("courtScheduleId", courtScheduleIds);

        final long allocatedListingsStartTime = System.nanoTime();
        List<Object[]> queryResultList = query.getResultList();
        final long allocatedListingsEndTime = System.nanoTime();
        LOGGER.info("BRS: Time taken for allocatedListings : {} ", (allocatedListingsEndTime - allocatedListingsStartTime) / 1000000);


        queryResultList.forEach(response -> {
            final List<SlotStartTime> slotStartTimes = resultStringListMap.computeIfAbsent((String) response[0], k -> new ArrayList<>());
            slotStartTimes.add(new SlotStartTime(toIsoString((Timestamp) response[1]), ((BigInteger) response[2]).longValue()));
        });

        return resultStringListMap;
    }

    private void addJudiciaries(final List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> courtScheduleJudiciaries,
                                final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule courtSchedule) {
        Optional.of(courtScheduleJudiciaries.stream()
                        .filter(courtScheduleJudiciary -> courtScheduleJudiciary.getCourtListingProfileId().equals(courtSchedule.getListingProfileId()))
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
                                                                                                           @QueryParam("businessType") String businessType,
                                                                                                           @QueryParam("courtSession") String courtSession);

    public List<CourtSchedule> getAllocatedCourtSchedules(final LocalDate startDate, final LocalDate endDate, final LocalDate hearingStartTimeEndBoundary, final List<String> ouCodes) {
        final long startAllocatedCourtSchedulesQuery = System.nanoTime();
        final List<CourtSchedule> allocatedCourtSchedules = entityManager.createNativeQuery(GET_ALLOCATED_SLOTS_FOR_COURT_SCHEDULE_QUERY, CourtSchedule.class)
                .setParameter("ouCodes", ouCodes)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setParameter("hearingStartTimeEndBoundary", hearingStartTimeEndBoundary)
                .getResultList();
        final long endAllocatedCourtSchedulesQuery = System.nanoTime();
        LOGGER.info("BRS: Time taken for getAllocatedCourtSchedules : {} - ouCode: {}", (endAllocatedCourtSchedulesQuery - startAllocatedCourtSchedulesQuery) / 1000000);
        return allocatedCourtSchedules;
    }
}
