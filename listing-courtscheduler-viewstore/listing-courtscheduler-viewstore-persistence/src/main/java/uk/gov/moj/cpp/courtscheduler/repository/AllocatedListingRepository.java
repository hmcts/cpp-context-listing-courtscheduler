package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_;

import java.util.Date;
import java.util.List;

import org.apache.deltaspike.data.api.AbstractFullEntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = AllocatedListing.class)
public abstract class AllocatedListingRepository extends AbstractFullEntityRepository<AllocatedListing, String> {
    abstract List<AllocatedListing> findByHearingId(final String hearingId);

    abstract List<AllocatedListing> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Date fromDate, Date toDate);

    public Integer findTotalAllocatedDurationByCourtScheduleId(final String courtScheduleId) {
        return criteria().select(Integer.class, sum(AllocatedListing_.duration)).eq(AllocatedListing_.courtScheduleId, courtScheduleId).getSingleResult();
    }

    abstract List<AllocatedListing> findByCourtScheduleId(final String courtScheduleId);

    public List<uk.gov.moj.cpp.courtscheduler.domain.AllocatedListing> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria) {
        List<AllocatedListing> allocatedListings = findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
                DateUtils.getDate(miFilterCriteria.getFromLocalDate()),
                DateUtils.getDate(miFilterCriteria.getToLocalDate()));
        return allocatedListings.stream().map(allocatedListingEntity -> {
            uk.gov.moj.cpp.courtscheduler.domain.AllocatedListing allocatedListing = new uk.gov.moj.cpp.courtscheduler.domain.AllocatedListing();
            allocatedListing.setBookingId(allocatedListingEntity.getBookingId());
            allocatedListing.setHearingId(allocatedListingEntity.getHearingId());
            allocatedListing.setCourtScheduleId(allocatedListingEntity.getCourtScheduleId());
            allocatedListing.setUpdatedOn(allocatedListingEntity.getUpdatedOn());
            allocatedListing.setDuration(allocatedListingEntity.getDuration());
            return allocatedListing;
        }).toList();
    }
}