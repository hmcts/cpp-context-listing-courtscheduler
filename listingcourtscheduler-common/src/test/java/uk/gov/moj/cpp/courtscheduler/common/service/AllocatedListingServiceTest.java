package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void shouldDeleteRedundantRotaData() {
        final int numberOfPreviousMonthsAndOlder = 6;
        final int numberOfDeleted = 20;
        when(allocatedListingRepository.deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder * 30))).thenReturn(numberOfDeleted);

        final int expectedNumberOfDeletion = allocatedListingService.deleteRedundantRotaData(numberOfPreviousMonthsAndOlder);
        verify(allocatedListingRepository, atLeastOnce()).deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder * 30));
        assertThat(expectedNumberOfDeletion, is(numberOfDeleted));
    }

    private List<AllocatedListingTotalBooked> getAllocatedListingTotalBooked(final List<String> courtScheduleIds) {
        return courtScheduleIds.stream()
                .map(courtScheduleId -> new AllocatedListingTotalBooked(courtScheduleId, totalBooked))
                .toList();
    }
}
