package uk.gov.moj.cpp.courtscheduler.repository;


import static java.util.Arrays.stream;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.LocalDate;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import org.apache.commons.lang3.StringUtils;
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

    public Pair<Integer, Set<String>> findHearingIdsBy(HearingSlotRequestParam hearingIdsReq) {
        final Map<String, Object> params = new HashMap<>();
        final StringBuilder queryStringBuilder = createQueryStringBuilderFrom(hearingIdsReq, params);

        final javax.persistence.Query totalQuery = entityManager.createNativeQuery(queryStringBuilder.toString());
        params.forEach(totalQuery::setParameter);
        final List<String> totalResultList = totalQuery.getResultList();

        queryStringBuilder.append("LIMIT :pageSize ");
        params.put("pageSize", Integer.parseInt(hearingIdsReq.pageSize()));
        queryStringBuilder.append("OFFSET :pageNumber ");
        params.put("pageNumber", Integer.parseInt(hearingIdsReq.pageNumber()) - 1);
        final javax.persistence.Query pageQuery = entityManager.createNativeQuery(queryStringBuilder.toString());
        params.forEach(pageQuery::setParameter);
        final Set<String> pageResultSet = new LinkedHashSet<>(pageQuery.getResultList());

        return Pair.of(totalResultList.size(), pageResultSet);
    }

    private StringBuilder createQueryStringBuilderFrom(HearingSlotRequestParam hearingIdsReq,
                                                       Map<String, Object> params) {
        final StringBuilder queryBuilder = new StringBuilder("select al.hearing_id " +
                "from allocated_listings al, court_schedule cs " +
                "where al.court_schedule_id = cs.id and cs.active = true ");
        queryBuilder.append("and cs.panel in (:panel) ");
        params.put("panel", stream(hearingIdsReq.panel().split(",")).map(String::trim).toList());

        queryBuilder.append("and cs.session_start >= :sessionStartDate ");
        params.put("sessionStartDate", LocalDate.parse(hearingIdsReq.sessionStartDate()));
        queryBuilder.append("AND cs.session_start <= :sessionEndDate ");
        params.put("sessionEndDate", LocalDate.parse(hearingIdsReq.sessionEndDate()));


        if (StringUtils.isNotBlank(hearingIdsReq.oucodeL2Code())) {
            queryBuilder.append("and cs.operational_unit = :oucodeL2Code ");
            params.put("oucodeL2Code", hearingIdsReq.oucodeL2Code());
        }
        if (StringUtils.isNotBlank(hearingIdsReq.ouCode())) {
            queryBuilder.append("and cs.oucode = :ouCode ");
            params.put("ouCode", hearingIdsReq.ouCode());
        }

        if (StringUtils.isNotBlank(hearingIdsReq.courtRoomId())) {
            queryBuilder.append("and cs.court_room_id = :courtRoomId ");
            params.put("courtRoomId", hearingIdsReq.courtRoomId());
        }
        if (StringUtils.isNotBlank(hearingIdsReq.courtRoomNumber())) {
            queryBuilder.append("and cs.court_room_number = :courtRoomNumber ");
            params.put("courtRoomNumber", hearingIdsReq.courtRoomNumber());
        }
        if (StringUtils.isNotBlank(hearingIdsReq.businessType())) {
            queryBuilder.append("and cs.rota_business_type = :businessType ");
            params.put("businessType", hearingIdsReq.businessType());
        }
        if (StringUtils.isNotBlank(hearingIdsReq.courtSession())) {
            queryBuilder.append("and cs.court_session = :courtSession ");
            params.put("courtSession", hearingIdsReq.courtSession());
        }

        queryBuilder.append("order by cs.session_start, cs.court_house_name, cs.court_room_name, cs.court_session, al.hearing_start_time ");

        return queryBuilder;
    }

}