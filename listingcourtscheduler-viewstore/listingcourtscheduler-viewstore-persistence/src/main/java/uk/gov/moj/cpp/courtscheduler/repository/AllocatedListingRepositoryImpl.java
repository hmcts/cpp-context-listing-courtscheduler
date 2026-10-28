package uk.gov.moj.cpp.courtscheduler.repository;

import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.EXACT_HEARING_START_DATETIME;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.IdResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TemporalType;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Spring Data picks this up by the {@code …Impl} naming convention as the implementation
 * of {@link AllocatedListingRepositoryCustom} — see Spring Data Reference §4.6.
 */
class AllocatedListingRepositoryImpl implements AllocatedListingRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<AllocatedListingTotalBooked> getAllocatedListingsByCourtScheduleId(final List<String> courtScheduleIds) {
        final String sql = """
            SELECT al.court_schedule_id,
                   CASE
                       WHEN cs.is_slot_based = true THEN CAST(COUNT(*) AS BIGINT)
                       ELSE CAST(SUM(al.duration) AS BIGINT)
                   END AS total_booked
            FROM allocated_listings al
            JOIN court_schedule cs ON al.court_schedule_id = cs.id
            WHERE al.court_schedule_id IN :csIds
            GROUP BY al.court_schedule_id, cs.is_slot_based
            """;

        @SuppressWarnings("unchecked")
        final List<Object[]> results = entityManager.createNativeQuery(sql)
                .setParameter("csIds", courtScheduleIds)
                .getResultList();

        return results.stream()
                .map(row -> new AllocatedListingTotalBooked()
                        .courtScheduleId((String) row[0])
                        .totalBooked(((Number) row[1]).intValue()))
                .toList();
    }

    @Override
    public List<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
            final MiFilterCriteria miFilterCriteria) {

        // Re-uses the method-name query Spring Data generates on the main interface, via
        // a lookup so this Custom impl doesn't require the main interface bean.
        final List<AllocatedListing> allocatedListings = entityManager.createQuery(
                        "SELECT al FROM AllocatedListing al "
                                + "WHERE al.updatedOn > :fromDate AND al.updatedOn < :toDate",
                        AllocatedListing.class)
                .setParameter("fromDate", DateUtils.getDate(miFilterCriteria.getFromDate()).toInstant())
                .setParameter("toDate", DateUtils.getDate(miFilterCriteria.getToDate()).toInstant())
                .getResultList();

        return allocatedListings.stream().map(entity -> {
            final uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing mi =
                    new uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing();
            mi.setId(entity.getId());
            mi.setOucode(entity.getOucode());
            mi.setCourtRoomId(entity.getCourtRoomId());
            mi.setCreatedOn(entity.getCreatedOn());
            mi.setBookingId(entity.getBookingId());
            mi.setHearingId(entity.getHearingId());
            mi.setCourtScheduleId(entity.getCourtScheduleId());
            mi.setUpdatedOn(entity.getUpdatedOn());
            mi.setDuration(entity.getDuration());
            mi.setHearingStartTime(entity.getHearingStartTime());
            mi.setRotaBusinessType(entity.getRotaBusinessType());
            return mi;
        }).toList();
    }

    @Override
    public Pair<Integer, Set<IdResponse>> findHearingIdsBy(final HearingSlotRequestParam hearingIdsReq) {
        final AllocatedHearingsQueryBuilder allocatedHearingsQueryCtx = new AllocatedHearingsQueryBuilder(hearingIdsReq);
        final jakarta.persistence.Query pageQuery =
                entityManager.createNativeQuery(allocatedHearingsQueryCtx.getAllocatedHearingsQuery());
        allocatedHearingsQueryCtx.getPagedQueryParamMap().forEach((k, v) -> {
            if (EXACT_HEARING_START_DATETIME.getLabel().equals(k)) {
                final Instant instant = Instant.parse((String) v);
                pageQuery.setParameter(k, Timestamp.from(instant), TemporalType.TIMESTAMP);
            } else {
                pageQuery.setParameter(k, v);
            }
        });
        @SuppressWarnings("unchecked")
        final List<Object[]> resultList = pageQuery.getResultList();
        final Set<IdResponse> pageResultSet = resultList.stream()
                .map(AllocatedListingRepositoryImpl::toIdResponse)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final int totalCount = resultList.isEmpty() ? 0 : ((Number) resultList.get(0)[5]).intValue();

        return Pair.of(totalCount, pageResultSet);
    }

    private static IdResponse toIdResponse(final Object... row) {
        return new IdResponse((String) row[0], (String) row[1], getLocalDate(row[2]), getLong(row[3]), getLong(row[4]));
    }

    private static Long getLong(final Object item) {
        return item == null ? null : ((Number) item).longValue();
    }

    @SuppressWarnings("PMD.ReplaceJavaUtilDate") // defensive fallback for a legacy JDBC driver shape; see comment below
    private static LocalDate getLocalDate(final Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof LocalDate ld) {
            return ld;
        }
        if (item instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        // Older JDBC drivers have been observed returning a bare java.util.Date for DATE columns;
        // convert it the same way we would a java.sql.Date rather than fail the whole projection.
        if (item instanceof java.util.Date) {
            return new java.sql.Date(((java.util.Date) item).getTime()).toLocalDate();
        }
        throw new IllegalArgumentException("Unsupported date shape from native query: " + item.getClass());
    }
}
