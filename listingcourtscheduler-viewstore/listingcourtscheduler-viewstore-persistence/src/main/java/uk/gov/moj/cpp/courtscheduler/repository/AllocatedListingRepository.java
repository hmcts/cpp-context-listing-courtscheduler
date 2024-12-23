package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_;

import java.util.Date;
import java.util.List;

import org.apache.deltaspike.data.api.AbstractFullEntityRepository;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = AllocatedListing.class)
public abstract class AllocatedListingRepository extends AbstractFullEntityRepository<AllocatedListing, String> {

    private static final String DELETE_REDUNDANT_ROTA_DATA = "DELETE FROM allocated_listings WHERE court_schedule_id IN (SELECT cs.id FROM court_schedule cs WHERE cs.session_start < (CURRENT_DATE - :numberOfDays))";

    abstract List<AllocatedListing> findByHearingId(final String hearingId);

    abstract List<AllocatedListing> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Date fromDate, Date toDate);

    public Integer findTotalAllocatedDurationByCourtScheduleId(final String courtScheduleId) {
        return criteria().select(Integer.class, sum(AllocatedListing_.duration)).eq(AllocatedListing_.courtScheduleId, courtScheduleId).getSingleResult();
    }

    abstract List<AllocatedListing> findByCourtScheduleId(final String courtScheduleId);

    public List<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria) {
        List<AllocatedListing> allocatedListings = findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
                DateUtils.getDate(miFilterCriteria.getFromLocalDate()),
                DateUtils.getDate(miFilterCriteria.getToLocalDate()));
        return allocatedListings.stream().map(allocatedListingEntity -> {
            uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing allocatedListing = new uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing();
            allocatedListing.setId(allocatedListingEntity.getId());
            allocatedListing.setOucode(allocatedListingEntity.getOucode());
            allocatedListing.setCourtRoomId(allocatedListingEntity.getCourtRoomId());
            allocatedListing.setCreatedOn(allocatedListingEntity.getCreatedOn());
            allocatedListing.setBookingId(allocatedListingEntity.getBookingId());
            allocatedListing.setHearingId(allocatedListingEntity.getHearingId());
            allocatedListing.setCourtScheduleId(allocatedListingEntity.getCourtScheduleId());
            allocatedListing.setUpdatedOn(allocatedListingEntity.getUpdatedOn());
            allocatedListing.setDuration(allocatedListingEntity.getDuration());
            allocatedListing.setHearingStartTime(allocatedListingEntity.getHearingStartTime());
            allocatedListing.setRotaBusinessType(allocatedListingEntity.getRotaBusinessType());
            return allocatedListing;
        }).toList();
    }

    @Query(value = "SELECT new uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked(al.courtScheduleId, sum(duration) AS totalbooked) FROM AllocatedListing al WHERE al.courtScheduleId IN :courtScheduleIds group by al.courtScheduleId")
    public abstract List<AllocatedListingTotalBooked> getAllocatedListingsByCourtScheduleId(@QueryParam("courtScheduleIds") final List<String> courtScheduleIds);

    public int deleteRedundantRotaData(final int numberOfDays) {
        return entityManager()
                .createNativeQuery(DELETE_REDUNDANT_ROTA_DATA)
                .setParameter("numberOfDays", numberOfDays)
                .executeUpdate();
    }
}