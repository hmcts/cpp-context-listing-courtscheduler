package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AllocatedListingServiceTest {

    @InjectMocks
    private AllocatedListingService allocatedListingService;

    @Mock
    private AllocatedListingRepository allocatedListingRepository;

    private final Long totalBooked = 100L;

    @Test
    void shouldGetAllocatedListingsByCourtScheduleId() {
        final List<String> courtScheduleIds = List.of(randomUUID().toString(), randomUUID().toString());

        when(allocatedListingRepository.getAllocatedListingsByCourtScheduleId(eq(courtScheduleIds))).thenReturn(getAllocatedListingTotalBooked(courtScheduleIds));

        final Map<String, Integer> allocatedListingTotalBookeds = allocatedListingService.getAllocatedListingsByCourtScheduleId(courtScheduleIds);
        courtScheduleIds.forEach(courtScheduleId -> {
            assertTrue(allocatedListingTotalBookeds.containsKey(courtScheduleId));
            assertEquals(totalBooked.intValue(), allocatedListingTotalBookeds.get(courtScheduleId));
        });
        assertEquals(courtScheduleIds.size(), allocatedListingTotalBookeds.size());

        verify(allocatedListingRepository, atLeastOnce()).getAllocatedListingsByCourtScheduleId(eq(courtScheduleIds));
    }

    private List<AllocatedListingTotalBooked> getAllocatedListingTotalBooked(final List<String> courtScheduleIds) {
        return courtScheduleIds.stream()
                .map(courtScheduleId -> new AllocatedListingTotalBooked(courtScheduleId, totalBooked))
                .toList();
    }

    @Test
    void shouldGetHearingIds() {
        HearingSlotRequestParam hearingIdsReq =
                new HearingSlotRequestParam("panel",
                        "2024-12-09",
                        "2024-12-09",
                        "BA124-L2",
                        "BA124",
                        "10",
                        "1",
                        "Court-Room-Id-1",
                        "Court-Room-Num-1",
                        "buss",
                        "Court-Session-1");
        Set<String> hearingIds = new LinkedHashSet<>();
        hearingIds.add(randomUUID().toString());
        hearingIds.add(randomUUID().toString());
        hearingIds.add(randomUUID().toString());
        Pair pair = Pair.of(3, hearingIds);
        when(allocatedListingRepository.findHearingIdsBy(eq(hearingIdsReq))).thenReturn(pair);
        JsonObject hearingIdsJsonObj = allocatedListingService.getHearingIds(hearingIdsReq);

        verify(allocatedListingRepository, atLeastOnce()).findHearingIdsBy(eq(hearingIdsReq));


        assertEquals(3, hearingIdsJsonObj.getInt(RequestParameterConstant.RESULTS.getLabel()));
        assertEquals(1, hearingIdsJsonObj.getInt(RequestParameterConstant.PAGE_COUNT.getLabel()));

        JsonArray hearingIdsJsonArr = hearingIdsJsonObj.getJsonArray(RequestParameterConstant.HEARING_IDS.getLabel());
        assertEquals(3, hearingIdsJsonArr.size());
        hearingIdsJsonArr.forEach(e -> assertTrue(hearingIds.contains(((JsonString) e).getString())));

    }
}
