package uk.gov.moj.cpp.courtscheduler.api.service;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.sql.Timestamp;
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

        doNothing().when(rotaFileProcessHistoryRepository).deleteByFileNamePrefixAndFileDate(eq(fileNamePrefix), any(Timestamp.class));
        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenReturn(rotaFileProcessHistoryMockEntity);

        rotaFileProcessHistoryService.update(fileNamePrefix, fileDate);

        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(any(RotaFileProcessHistory.class));
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).deleteByFileNamePrefixAndFileDate(eq(fileNamePrefix), any(Timestamp.class));
    }
}
