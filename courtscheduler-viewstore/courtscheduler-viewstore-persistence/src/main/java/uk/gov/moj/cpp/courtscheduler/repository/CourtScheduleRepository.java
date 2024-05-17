package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.exception.CourtScheduleIdNotMatchingException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toRoundedTimestamp;

@Repository(forEntity = CourtSchedule.class)
public abstract class CourtScheduleRepository extends AbstractEntityRepository<CourtSchedule, String> {

    @Inject
    private AllocatedListingRepository allocatedListingRepository;

    @Inject
    ProvisionalBookingRepository provisionalBookingRepository;

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
}
