package uk.gov.moj.cpp.courtscheduler.repository;


import static java.util.Arrays.stream;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.IdResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_;

import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.data.api.AbstractFullEntityRepository;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = AllocatedListing.class)
public abstract class AllocatedListingRepository extends AbstractFullEntityRepository<AllocatedListing, String> {

    private static final String DELETE_REDUNDANT_ROTA_DATA = "DELETE FROM allocated_listings WHERE court_schedule_id IN (SELECT cs.id FROM court_schedule cs WHERE cs.session_start < (CURRENT_DATE - :numberOfDays))";

    @Inject
    private EntityManager entityManager;

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

    @Query(value = "SELECT new uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked(al.courtScheduleId, al.duration, al.hearingStartTime) FROM AllocatedListing al WHERE al.courtScheduleId IN :courtScheduleIds")
    public abstract List<AllocatedListingEachBooked> getAllocatedListingsEachBookedByCourtScheduleId(@QueryParam("courtScheduleIds") final List<String> courtScheduleIds);

    public int deleteRedundantRotaData(final int numberOfDays) {
        return entityManager()
                .createNativeQuery(DELETE_REDUNDANT_ROTA_DATA)
                .setParameter("numberOfDays", numberOfDays)
                .executeUpdate();
    }

    public Pair<Integer, Set<IdResponse>> findHearingIdsBy(HearingSlotRequestParam hearingIdsReq) {
        final AllocatedHearingsQueryBuilder allocatedHearingsQueryCtx = new AllocatedHearingsQueryBuilder(hearingIdsReq);
        final javax.persistence.Query pageQuery =
                entityManager.createNativeQuery(allocatedHearingsQueryCtx.getAllocatedHearingsQuery());
        allocatedHearingsQueryCtx.getPagedQueryParamMap().forEach(pageQuery::setParameter);
        List<Object[]> resultList = pageQuery.getResultList();
        final Set<IdResponse> pageResultSet = resultList.stream()
                .map(row -> toIdResponse(row))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final int totalCount = resultList.isEmpty() ? 0 : ((Number) resultList.get(0)[5]).intValue();

        return Pair.of(totalCount, pageResultSet);
    }

    private static IdResponse toIdResponse(final Object[] row) {
        return new IdResponse((String) row[0], (String) row[1], getLocalDate(row[2]), getLong(row[3]), getLong(row[4]));
    }

    private static Long getLong(final Object item) {
        return item == null ? null : ((Number) item).longValue();
    }

    private static LocalDate getLocalDate(final Object item) {
        return item == null ? null : ((java.sql.Date) item).toLocalDate();
    }

    @Query("SELECT al FROM AllocatedListing al WHERE al.courtScheduleId = :courtScheduleId AND al.hearingId = :hearingId")
    abstract List<AllocatedListing> findByCourtScheduleIdAndHearingId(
            @QueryParam("courtScheduleId") String courtScheduleId, @QueryParam("hearingId") String hearingId);

}