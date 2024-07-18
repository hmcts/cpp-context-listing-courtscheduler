package uk.gov.moj.cpp.courtscheduler.repository;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toIsoString;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toRoundedTimestamp;
import static uk.gov.moj.cpp.courtscheduler.utils.QueryConstants.NOT_EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE;

import uk.gov.moj.cpp.courtscheduler.converter.CourtSchedulerConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
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

import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Modifying;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;
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

    private static final String DELETE_UNALLOCATED_COURT_SCHEDULE_QUERY = "DELETE FROM COURT_SCHEDULE " +
            "WHERE court_listing_profile_id is not null AND max_slot = available_slot AND max_duration_mins = available_duration_mins and " +
            "session_start BETWEEN :startDate AND :endDate AND oucode IN :ouCodes AND active =true AND NOT EXISTS( " + NOT_EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + ")";

    public static final String DELETE_UNALLOCATED_FORECAST_SLOT_QUERY = "DELETE FROM COURT_SCHEDULE " +
            "WHERE court_listing_profile_id is null AND max_slot = available_slot AND max_duration_mins = available_duration_mins AND oucode IN :ouCodes " +
            "AND active =true and not exists( " + NOT_EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + ")";

    public static final String DELETE_SLOTS_BY_IDS_QUERY = "DELETE FROM COURT_SCHEDULE WHERE id IN :courtScheduleIds AND " +
            "not exists(" + NOT_EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE.getQuery() + ")";



    //update on Create when needed
    public CourtSchedule update(CourtSchedule courtSchedule) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        courtScheduleCriteria.createMultipleSessionsCourtScheduleCriteria(courtSchedule, criteriaBuilder, criteriaQuery);
        CourtSchedule persistedCourtSchedule = entityManager.createQuery(criteriaQuery).getSingleResult();

        if ((persistedCourtSchedule.getMaxSlots() > 0
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

            this.save(persistedCourtSchedule);
        }
        return courtSchedule;
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

    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria) {
        List<CourtSchedule> courtScheduleList = findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
                DateUtils.getDate(miFilterCriteria.getFromLocalDate()),
                DateUtils.getDate(miFilterCriteria.getToLocalDate()));
        return courtScheduleList.stream().map(CourtSchedulerConverter::convert).toList();
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
        List<CourtSchedule> courtScheduleList =
                entityManager.createQuery(criteriaQuery).setFirstResult((pageNumber - 1) * pageSize).setMaxResults(pageSize).getResultList();
        List<CourtSchedule> totalCourtScheduleList = entityManager.createQuery(criteriaQuery).getResultList();
        courtScheduleList.forEach(e -> courtScheduleIds.add(e.getCourtScheduleId()));
        int resultSize = totalCourtScheduleList.size();

        final List<CourtScheduleJudiciary> courtScheduleJudiciaryList = getCourtScheduleJudiciaries(courtScheduleList);
        final Map<String, List<SlotStartTime>> slotStartTimeList = getCountBasedAllocatedListing(courtScheduleIds);

        ModelMapper modelMapper = new ModelMapper();
        courtScheduleList.forEach(courtSchedule -> courtSchedules.add(modelMapper.map(courtSchedule, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.class)));
        courtScheduleJudiciaryList.forEach(courtScheduleJudiciary -> courtScheduleJudiciaries.add(modelMapper.map(courtScheduleJudiciary, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class)));

        courtSchedules.forEach(courtSchedule -> {
                    addJudiciaries(courtScheduleJudiciaries, courtSchedule);
                    addSlotStartTimes(slotStartTimeList, courtSchedule);
                }
        );

        return Pair.of(resultSize, courtSchedules);
    }

    public List<CourtScheduleJudiciary> getCourtScheduleJudiciaries(List<CourtSchedule> courtScheduleList) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtScheduleJudiciary> criteriaQuery = criteriaBuilder.createQuery(CourtScheduleJudiciary.class);
        courtScheduleCriteria.createCourtScheduleJudiciaryCriteria(courtScheduleList, criteriaBuilder, criteriaQuery);
        return entityManager.createQuery(criteriaQuery).getResultList();
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

    public int deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final String ouCodes) {
        return entityManager()
                .createNativeQuery(DELETE_UNALLOCATED_COURT_SCHEDULE_QUERY)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setParameter("ouCodes", ouCodes)
                .executeUpdate();
    }

    public int deleteUnAllocatedProvisionalEntries(final String ouCodes) {
        return entityManager()
                .createNativeQuery(DELETE_UNALLOCATED_FORECAST_SLOT_QUERY)
                .setParameter("ouCodes", ouCodes)
                .executeUpdate();
    }

    public int deleteSlots(final String courtScheduleIds) {
        return entityManager()
                .createNativeQuery(DELETE_SLOTS_BY_IDS_QUERY)
                .setParameter("courtScheduleIds", courtScheduleIds)
                .executeUpdate();
    }

    @Query(value = "SELECT cs FROM CourtSchedule cs WHERE cs.ouCode IN :ouCodes AND cs.active = true AND cs.sessionDate BETWEEN :startDate AND :endDate")
    public abstract List<CourtSchedule> getExtractedCourtSchedules(@QueryParam("ouCodes") final String ouCodes, @QueryParam("startDate") LocalDate startDate, @QueryParam("endDate") LocalDate endDate);

    @Query(value = "SELECT cs FROM CourtSchedule cs WHERE cs.ouCode IN :ouCodes AND cs.sessionDate BETWEEN :startDate AND :endDate")
    public abstract List<CourtSchedule> getExtractedCourtSchedulesForGhostRota(@QueryParam("ouCodes") final String ouCodes, @QueryParam("startDate") LocalDate startDate, @QueryParam("endDate") LocalDate endDate);

    @Modifying
    @Query(value = "UPDATE CourtSchedule cs SET cs.active = false, cs.updatedOn = :updatedOn WHERE cs.courtScheduleId IN :courtScheduleIds")
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


            Optional<CourtSchedule> optionalBy = this.findOptionalBy(allocatedSlot.getCourtScheduleId());

            if (optionalBy.isPresent()) {
                allocatedSlot.setCourtScheduleId(optionalBy.get().getCourtScheduleId());
                allocatedSlot.setSlotBased(optionalBy.get().isSlotBased());
                matchedSlots.add(allocatedSlot);

            }
        }

        return matchedSlots;
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

    protected void saveAllocatedListing(final List<AllocatedSlot> allocatedSlots) {
        allocatedSlots.forEach(allocatedSlot -> {
            AllocatedListing allocatedListing = new AllocatedListing();
            allocatedListing.setId(UUID.randomUUID().toString());
            allocatedListing.setCourtScheduleId(allocatedSlot.getCourtScheduleId());
            allocatedListing.setBookingId(allocatedSlot.getBookingId());
            allocatedListing.setHearingId(allocatedSlot.getHearingId());
            allocatedListing.setOucode(allocatedSlot.getOuCode());
            allocatedListing.setCourtRoomId(Integer.parseInt(allocatedSlot.getCourtRoomId()));
            allocatedListing.setDuration(allocatedSlot.getDuration());
            allocatedListing.setHearingStartTime(toRoundedTimestamp(allocatedSlot.getHearingStartTime()));
            this.allocatedListingRepository.save(allocatedListing);
        });
    }

    protected void deleteProvisionalBooking(final String bookingId) {
        Optional<ProvisionalBooking> byBookingId = this.provisionalBookingRepository.findByBookingId(bookingId);
        if (byBookingId.isPresent()) {
            ProvisionalBooking provisionalBooking = byBookingId.get();
            provisionalBooking.setActive(false);
            this.provisionalBookingRepository.save(provisionalBooking);
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
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AllocatedListing> criteriaQuery = criteriaBuilder.createQuery(AllocatedListing.class);
        courtScheduleCriteria.createAllocatedListingCriteria(courtScheduleIds, criteriaQuery);
        List<AllocatedListing> allocatedListing = entityManager.createQuery(criteriaQuery).getResultList();
        final long count = allocatedListing.size();
        allocatedListing.forEach(e -> {
            final List<SlotStartTime> slotStartTimes = resultStringListMap.computeIfAbsent(e.getCourtScheduleId(), k -> new ArrayList<>());
            slotStartTimes.add(new SlotStartTime(toIsoString((Timestamp) e.getHearingStartTime()), count));
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

    @Query(value = "SELECT entity.courtScheduleId from CourtSchedule entity where entity.courtRoomId = :courtRoomId " +
            "and entity.sessionDate = :sessionDate and entity.businessType = :businessType and entity.courtSession = :courtSession")
    public abstract String findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(@QueryParam("courtRoomId") String courtRoomId,
                                                                                @QueryParam("sessionDate") LocalDate sessionDate,
                                                                                @QueryParam("businessType") String businessType,
                                                                                @QueryParam("courtSession") String courtSession);
}
