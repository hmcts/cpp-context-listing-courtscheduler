package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import uk.gov.moj.cpp.courtscheduler.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.IdResponse;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.BookingLogRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.springframework.transaction.annotation.Transactional;

import org.apache.commons.lang3.tuple.Pair;

@Service
public class AllocatedListingService {

    @Inject
    private AllocatedListingRepository allocatedListingRepository;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    @Inject
    private BookingLogRepository bookingLogRepository;

    // LPT-2611: how long booking_log keeps a row. Cleared by the nightly purge below.
    @Value("${courtscheduler.booking-log.retention-days:90}")
    private int bookingLogRetentionDays;

    @Transactional
    public Map<String, Integer> getAllocatedListingsByCourtScheduleId(final List<String> courtScheduleIdList) {
        final List<AllocatedListingTotalBooked> allocatedListingTotalBookeds = allocatedListingRepository.getAllocatedListingsByCourtScheduleId(courtScheduleIdList);

        return allocatedListingTotalBookeds.stream()
                .collect(toMap(AllocatedListingTotalBooked::getCourtScheduleId, AllocatedListingTotalBooked::getTotalBooked));
    }

    @Transactional
    public int deleteRedundantRotaData(final int numberOfPreviousMonths) {
        return allocatedListingRepository.deleteRedundantRotaData(numberOfPreviousMonths * 30);
    }

    /**
     * Releases allocated_listings rows whose expiresAt is before today, which gives their
     * sessions the capacity back (it is counted from allocated_listings). ZoneOffset.UTC keeps the cutoff deterministic regardless of the JVM's default
     * zone. Self-healing against a missed daily run, since it isn't scoped to exactly "yesterday".
     */
    @Transactional
    public int purgeExpiredReservedSessions() {
        final int released = courtScheduleRepository.releaseExpiredReservations(LocalDate.now(ZoneOffset.UTC));
        bookingLogRepository.deleteRecordedBefore(Instant.now().minus(bookingLogRetentionDays, ChronoUnit.DAYS));
        return released;
    }


    public JsonObject getHearingIds(final HearingSlotRequestParam hearingIdsRequest) {
        final Pair<Integer, Set<IdResponse>> hearingIdsResult =
                allocatedListingRepository.findHearingIdsBy(hearingIdsRequest);
        final long resultsCount = hearingIdsResult.getKey();
        int pageSize = parseInt(hearingIdsRequest.pageSize());
        if (pageSize <= 0) {
            pageSize = 1;
        }

        final JsonArrayBuilder jsonHearingIdsArrayBuilder = Json.createArrayBuilder();
        hearingIdsResult.getValue().forEach(idResults ->
                jsonHearingIdsArrayBuilder.
                        add(objectToJsonObjectConverter.
                                convert(idResults)));

        final JsonArray hearingIdsJsonArray = jsonHearingIdsArrayBuilder.build();
        final long pageCount = (long) Math.ceil((double) resultsCount / pageSize);

        return Json.createObjectBuilder()
                .add(RequestParameterConstant.RESULTS.getLabel(), resultsCount)
                .add(RequestParameterConstant.PAGE_COUNT.getLabel(), pageCount)
                .add(RequestParameterConstant.HEARING_IDS.getLabel(), hearingIdsJsonArray)
                .build();
    }

    public Map<String, Integer> getTotalBookedPerCourtScheduleIds(final List<String> courtScheduleIds) {
        final Map<String, Integer> totalBookedPerCourtScheduleIds = new HashMap<>();
        final List<AllocatedListingEachBooked> allocatedListingEachBookeds = allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(courtScheduleIds);

        final Map<String, List<AllocatedListingEachBooked>> allocatedListingEachBookedMap = allocatedListingEachBookeds.stream()
                .collect(groupingBy(AllocatedListingEachBooked::getCourtScheduleId));

        allocatedListingEachBookedMap.keySet().forEach(courtScheduleId ->
            totalBookedPerCourtScheduleIds.put(courtScheduleId, allocatedListingEachBookedMap.get(courtScheduleId).stream().mapToInt(AllocatedListingEachBooked::getDuration).sum())
        );

        return totalBookedPerCourtScheduleIds;
    }

    public List<AllocatedListingEachBooked> getAllocatedListingEachBookedByCourtScheduleId(final String courtScheduleId) {
        return allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId));
    }

}
