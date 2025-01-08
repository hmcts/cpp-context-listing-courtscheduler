package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.List;

import io.github.benas.randombeans.api.EnhancedRandom;
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
    void shouldDeleteUnAllocatedCourtScheduleEntriesForRotaPeriod() {
        final LocalDate startDate = LocalDate.of(2024, 10, 1);
        final LocalDate endDate = LocalDate.of(2025, 3, 31);
        final List<String> ouCodes = List.of("B01LY00", "B01LY01", "B01LY02") ;

        when(courtScheduleRepository.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(startDate, endDate, ouCodes)).thenReturn(100);

        final int numberOfDeleted = courtScheduleService.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(startDate, endDate, ouCodes);

        assertThat(numberOfDeleted, is(100));
    }

    @Test
    void shouldSaveSlot() {
        final CourtSchedule courtScheduleEntity = EnhancedRandom.random(CourtSchedule.class);

        when(courtScheduleRepository.save(courtScheduleEntity)).thenReturn(courtScheduleEntity);

        final CourtSchedule courtScheduleSaved = courtScheduleService.saveSlot(courtScheduleEntity);

        assertNotNull(courtScheduleSaved);
    }

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
