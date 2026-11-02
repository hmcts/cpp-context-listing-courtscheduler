package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleServiceTest {

    @InjectMocks
    private CourtScheduleService courtScheduleService;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Test
    void shouldDeleteRedundantRotaData() {
        final int numberOfPreviousMonthsAndOlder = 6;
        final int numberOfDeleted = 20;
        when(courtScheduleRepository.deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder * 30))).thenReturn(numberOfDeleted);

        final int expectedNumberOfDeletion = courtScheduleService.deleteRedundantRotaData(numberOfPreviousMonthsAndOlder);
        verify(courtScheduleRepository, atLeastOnce()).deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder * 30));
        assertThat(expectedNumberOfDeletion, is(numberOfDeleted));
    }
}
