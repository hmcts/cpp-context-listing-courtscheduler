package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;

import java.util.List;
import java.util.Map;

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
}
