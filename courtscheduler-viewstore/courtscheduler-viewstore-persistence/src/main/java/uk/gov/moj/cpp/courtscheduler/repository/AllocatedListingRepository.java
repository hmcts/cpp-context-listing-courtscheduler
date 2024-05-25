package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;

import java.util.Date;
import java.util.List;

@Repository(forEntity = AllocatedListing.class)
public abstract class AllocatedListingRepository extends AbstractEntityRepository<AllocatedListing, String> {
    abstract List<AllocatedListing> findByHearingId(final String hearingId);

    abstract List<AllocatedListing> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Date fromDate, Date toDate);

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