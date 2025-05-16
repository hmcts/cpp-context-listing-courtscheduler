package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.transaction.Transactional;

import org.apache.commons.lang3.tuple.Pair;

@ApplicationScoped
public class AllocatedListingService {

    @Inject
    private AllocatedListingRepository allocatedListingRepository;

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


    public JsonObject getHearingIds(HearingSlotRequestParam hearingIdsRequest) {
        final Pair<Integer, Set<String>> hearingIdsResult =
                allocatedListingRepository.findHearingIdsBy(hearingIdsRequest);
        final long resultsCount = hearingIdsResult.getKey();
        int pageSize = parseInt(hearingIdsRequest.pageSize());
        if (pageSize <= 0) {
            pageSize = 1;
        }

        final JsonArrayBuilder jsonHearingIdsArrayBuilder = Json.createArrayBuilder();
        hearingIdsResult.getValue().forEach(jsonHearingIdsArrayBuilder::add);
        final JsonArray hearingIdsJsonArray = jsonHearingIdsArrayBuilder.build();
        final long pageCount = (long) Math.ceil((double) resultsCount / (double) pageSize);

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
