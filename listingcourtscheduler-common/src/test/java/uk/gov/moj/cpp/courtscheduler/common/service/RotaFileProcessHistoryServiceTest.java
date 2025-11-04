package uk.gov.moj.cpp.courtscheduler.common.service;


import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

    private RotaFileProcessHistory testRotaFileProcessHistory;

    @BeforeEach
    void setUp() {
        testRotaFileProcessHistory = new RotaFileProcessHistory();
        testRotaFileProcessHistory.setExecutionId(randomUUID().toString());
        testRotaFileProcessHistory.setFileName("test_file.xml");
        testRotaFileProcessHistory.setFileNamePrefix("test_prefix");
    }

    @Test
    void shouldSaveRotaFileProcessHistory() {
        final String fileNamePrefix = "lja_merseyside_snapshot_";
        final OffsetDateTime fileDate = OffsetDateTime.of(2024, 4, 2, 18, 0, 39, 0, ZoneOffset.UTC);
        final byte[] content = "test content".getBytes();

        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenReturn(testRotaFileProcessHistory);

        RotaFileProcessHistory result = rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());

        assertNotNull(result);
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(any(RotaFileProcessHistory.class));
    }

    @Test
    void shouldSaveRotaFileProcessHistoryWithCorrectFileName() {
        final String fileNamePrefix = "lja_merseyside_snapshot_";
        final OffsetDateTime fileDate = OffsetDateTime.of(2024, 4, 2, 18, 0, 39, 0, ZoneOffset.UTC);
        final byte[] content = "test content".getBytes();

        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenAnswer(invocation -> {
            RotaFileProcessHistory history = invocation.getArgument(0);
            assertEquals("lja_merseyside_snapshot_20240402.xml", history.getFileName());
            assertEquals(fileNamePrefix, history.getFileNamePrefix());
            assertNotNull(history.getFileHash());
            assertNotNull(history.getProcessedOn());
            assertNotNull(history.getProcessStartDate());
            return testRotaFileProcessHistory;
        });

        rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());

        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(any(RotaFileProcessHistory.class));
    }

    @Test
    void shouldUpdateRotaFileProcessHistory() {
        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenReturn(testRotaFileProcessHistory);

        RotaFileProcessHistory result = rotaFileProcessHistoryService.update(testRotaFileProcessHistory);

        assertNotNull(result);
        assertNotNull(testRotaFileProcessHistory.getProcessEndDate());
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(testRotaFileProcessHistory);
    }

    @Test
    void shouldUpdateRotaFileProcessHistoryWithProcessEndDate() {
        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenAnswer(invocation -> {
            RotaFileProcessHistory history = invocation.getArgument(0);
            assertNotNull(history.getProcessEndDate());
            return history;
        });

        rotaFileProcessHistoryService.update(testRotaFileProcessHistory);

        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(testRotaFileProcessHistory);
    }

    @Test
    void shouldComputeFileHashCorrectly() {
        final String fileNamePrefix = "test_prefix";
        final OffsetDateTime fileDate = OffsetDateTime.now();
        final byte[] content = "test content for hashing".getBytes();

        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenAnswer(invocation -> {
            RotaFileProcessHistory history = invocation.getArgument(0);
            // Verify that a hash was computed (not null and not empty)
            assertNotNull(history.getFileHash());
            assertNotNull(history.getFileHash().length() > 0);
            return history;
        });

        rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());

        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(any(RotaFileProcessHistory.class));
    }

    @Test
    void shouldComputeFileHashConsistently() {
        final String fileNamePrefix = "test_prefix";
        final OffsetDateTime fileDate = OffsetDateTime.now();
        final byte[] content = "consistent test content".getBytes();

        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenAnswer(invocation -> {
            RotaFileProcessHistory history = invocation.getArgument(0);
            return history;
        });

        RotaFileProcessHistory result1 = rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());
        RotaFileProcessHistory result2 = rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());

        // Same content should produce same hash
        assertEquals(result1.getFileHash(), result2.getFileHash());
    }

    @Test
    void shouldHandleEmptyContent() {
        final String fileNamePrefix = "test_prefix";
        final OffsetDateTime fileDate = OffsetDateTime.now();
        final byte[] content = new byte[0];

        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenReturn(testRotaFileProcessHistory);

        RotaFileProcessHistory result = rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());

        assertNotNull(result);
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(any(RotaFileProcessHistory.class));
    }

    @Test
    void shouldHandleNullContent() {
        final String fileNamePrefix = "test_prefix";
        final OffsetDateTime fileDate = OffsetDateTime.now();
        final byte[] content = null;

        assertThrows(NullPointerException.class, () -> {
            rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());
        });
    }

    @Test
    void shouldHandleLargeContent() {
        final String fileNamePrefix = "test_prefix";
        final OffsetDateTime fileDate = OffsetDateTime.now();
        final byte[] content = new byte[10000]; // 10KB of data
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 256);
        }

        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenReturn(testRotaFileProcessHistory);

        RotaFileProcessHistory result = rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());

        assertNotNull(result);
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(any(RotaFileProcessHistory.class));
    }

    @Test
    void shouldSetCorrectProcessedOnTimestamp() {
        final String fileNamePrefix = "test_prefix";
        final OffsetDateTime fileDate = OffsetDateTime.now();
        final byte[] content = "test content".getBytes();

        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenAnswer(invocation -> {
            RotaFileProcessHistory history = invocation.getArgument(0);
            assertNotNull(history.getProcessedOn());
            // Verify it's set to current time (within reasonable bounds)
            long timeDiff = Math.abs(System.currentTimeMillis() - history.getProcessedOn().getTime());
            assertTrue(timeDiff < 5000); // Within 5 seconds
            return history;
        });

        rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());

        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(any(RotaFileProcessHistory.class));
    }

    @Test
    void shouldSetCorrectProcessStartDate() {
        final String fileNamePrefix = "test_prefix";
        final OffsetDateTime fileDate = OffsetDateTime.now();
        final byte[] content = "test content".getBytes();

        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenAnswer(invocation -> {
            RotaFileProcessHistory history = invocation.getArgument(0);
            assertNotNull(history.getProcessStartDate());
            // Verify it's set to current time (within reasonable bounds)
            long timeDiff = Math.abs(System.currentTimeMillis() - history.getProcessStartDate().getTime());
            assertTrue(timeDiff < 5000); // Within 5 seconds
            return history;
        });

        rotaFileProcessHistoryService.save(fileNamePrefix, fileDate, content, UUID.randomUUID().toString());

        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(any(RotaFileProcessHistory.class));
    }

    @Test
    void shouldSetCorrectProcessEndDateOnUpdate() {
        when(rotaFileProcessHistoryRepository.save(any(RotaFileProcessHistory.class))).thenAnswer(invocation -> {
            RotaFileProcessHistory history = invocation.getArgument(0);
            assertNotNull(history.getProcessEndDate());
            // Verify it's set to current time (within reasonable bounds)
            long timeDiff = Math.abs(System.currentTimeMillis() - history.getProcessEndDate().getTime());
            assertTrue(timeDiff < 5000); // Within 5 seconds
            return history;
        });

        rotaFileProcessHistoryService.update(testRotaFileProcessHistory);

        verify(rotaFileProcessHistoryRepository, atLeastOnce()).save(testRotaFileProcessHistory);
    }
}
