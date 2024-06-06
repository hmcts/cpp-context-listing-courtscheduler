package uk.gov.moj.cpp.courtscheduler.repository;

import static java.lang.String.format;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toIsoString;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toRoundedTimestamp;

import uk.gov.moj.cpp.courtscheduler.converter.CourtSchedulerConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.SlotStartTime;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.exception.CourtScheduleIdNotMatchingException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.repository.criteria.CourtScheduleCriteria;

import java.sql.Timestamp;
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
import org.apache.deltaspike.data.api.Repository;
import org.modelmapper.ModelMapper;

@Repository(forEntity = CourtSchedule.class)
public abstract class CourtScheduleRepository extends AbstractEntityRepository<CourtSchedule, String> {

    @Inject
    EntityManager entityManager;
    @Inject
    CourtScheduleCriteria courtScheduleCriteria;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    ProvisionalBookingRepository provisionalBookingRepository;

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
        if (isNotEmpty(updateAllocatedSlots)) {
            updateCourtSchedule(updateAllocatedSlots);

            saveAllocatedListing(updateAllocatedSlots);

            if (isProvisionalSlot) {
                deleteProvisionalBooking(slots.get(0).getBookingId());
            }
        } else {
            throw new CourtScheduleIdNotMatchingException(format("courtScheduleId matching for non-provisional slot(s) has been failed,please check the logs. slots : %s", slots));
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
        courtScheduleList.forEach((e) -> courtScheduleIds.add(e.getCourtScheduleId()));
        int resultSize = courtScheduleList.size();

        final List<CourtScheduleJudiciary> courtScheduleJudiciaryList = getCourtScheduleJudiciaries(courtScheduleList);
        final Map<String, List<SlotStartTime>> slotStartTimeList = getCountBasedAllocatedListing(courtScheduleIds);

        ModelMapper modelMapper = new ModelMapper();
        courtScheduleList.forEach(courtSchedule -> {
            courtSchedules.add(modelMapper.map(courtSchedule, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.class));
        });
        courtScheduleJudiciaryList.forEach(courtScheduleJudiciary -> {
            courtScheduleJudiciaries.add(modelMapper.map(courtScheduleJudiciary, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class));
        });

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
            if(allocatedListings != null && !allocatedListings.isEmpty()) {
                uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule domainCourtSchedule =
                        modelMapper.map(courtSchedule, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.class);
                errorDeleteCourtSchedules.add(domainCourtSchedule);
            } else {
                courtSchedule.setActive(false);
                save(courtSchedule);
            }
        });
        return errorDeleteCourtSchedules;
    }

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

    private void releaseOldAllocatedListings(final String hearingId) {
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
        allocatedListing.forEach((e) -> {
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
}
