package uk.gov.moj.cpp.courtscheduler.common.service;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFileProcessHistoryServiceTest {

    @Mock
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    @InjectMocks
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @Mock
    private RotaFileProcessHistory rotaFileProcessHistoryMockEntity;

    @Test
    void shouldUpdate() {
        final String fileNamePrefix = "lja_merseyside_snapshot_";
        final OffsetDateTime fileDate = OffsetDateTime.now();
        final byte[] content = "test content".getBytes();

        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenReturn(rotaFileProcessHistoryMockEntity);

        rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content);

        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(any(RotaFileProcessHistory.class));
    }
}
