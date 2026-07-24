package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.RotaProcessLogRepository;

import jakarta.persistence.EntityManager;

import io.github.benas.randombeans.api.EnhancedRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaProcessLogServiceTest {

    @InjectMocks
    private RotaProcessLogService rotaProcessLogService;

    @Mock
    private RotaProcessLogRepository rotaProcessLogRepository;

    @Mock
    private EntityManager entityManager;

    @Test
    void shouldSaveRotaProcessLog() {
        final RotaProcessLog rotaProcessLog = EnhancedRandom.random(RotaProcessLog.class);

        final RotaProcessLog rotaProcessLogSaved = rotaProcessLogService.saveRotaProcessLog(rotaProcessLog);

        assertNotNull(rotaProcessLogSaved);
    }

    @Test
    void shouldDeleteRedundantRotaData() {
        final int numberOfPreviousMonths = 6;
        final int numberOfDays = numberOfPreviousMonths * 30;
        final int numberOfDeleted = 15;
        when(rotaProcessLogRepository.deleteRedundantRotaData(eq(numberOfDays))).thenReturn(numberOfDeleted);

        final int expectedNumberOfDeletion = rotaProcessLogService.deleteRedundantRotaData(numberOfPreviousMonths);

        verify(rotaProcessLogRepository, atLeastOnce()).deleteRedundantRotaData(eq(numberOfDays));
        assertThat(expectedNumberOfDeletion, is(numberOfDeleted));
    }
}