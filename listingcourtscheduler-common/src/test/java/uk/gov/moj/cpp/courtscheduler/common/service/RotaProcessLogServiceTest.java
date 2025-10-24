package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.RotaProcessLogRepository;

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

    @Test
    void shouldSaveRotaProcessLog() {
        final RotaProcessLog rotaProcessLog = EnhancedRandom.random(RotaProcessLog.class);

        when(rotaProcessLogRepository.save(rotaProcessLog)).thenReturn(rotaProcessLog);

        final RotaProcessLog rotaProcessLogSaved = rotaProcessLogService.saveRotaProcessLog(rotaProcessLog);

        assertNotNull(rotaProcessLogSaved);
    }
}