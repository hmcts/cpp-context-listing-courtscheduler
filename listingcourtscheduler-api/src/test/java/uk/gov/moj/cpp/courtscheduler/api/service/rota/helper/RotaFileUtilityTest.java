package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class RotaFileUtilityTest {
    private static final String VALUE_2024_01_15_T12_00_00_Z = "2024-01-15T12:00:00Z";
    private static final String PRF_PROCESSING_AND_PARSING_COMPLETED_FOR_BLOB_IN = "PRF: Processing and parsing completed for blob {} in {} ms";
    private static final String UPDATED_FILE_PROCESS_HISTORY_WITH_END_DATE_FOR_B = "Updated file process history with end date for blob: {}";
    private static final String TEST_CONTENT = "test content";
    private static final String TEST_BLOB_XML = "test_blob.xml";
    private static final String TEST_SNAPSHOT = "test_snapshot_";
    private static final String TEST_SNAPSHOT_20240115_T120000_Z_XML = "test_snapshot_20240115T120000Z.xml";


    @Mock
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    @Mock
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @InjectMocks
    private RotaFileUtility rotaFileUtility;

    @Test
    void shouldConvertNanosToMillis() {
        // given
        final long nanos = 5_000_000L; // 5 milliseconds

        // when
        final long result = rotaFileUtility.convertNanosToMillis(nanos);

        // then
        assertThat(result, is(5L));
    }

    @Test
    void shouldConvertZeroNanosToZeroMillis() {
        // given
        final long nanos = 0L;

        // when
        final long result = rotaFileUtility.convertNanosToMillis(nanos);

        // then
        assertThat(result, is(0L));
    }


    @Test
    void shouldIdentifyDummyFile() {
        // given
        final String fileName = "dummysupport_file.csv";

        // when
        final boolean result = rotaFileUtility.isDummyFile(fileName);

        // then
        assertTrue(result);
    }

    @Test
    void shouldNotIdentifyNonDummyFile() {
        // given
        final String fileName = "test_file.csv";

        // when
        final boolean result = rotaFileUtility.isDummyFile(fileName);

        // then
        assertFalse(result);
    }

    @Test
    void shouldReturnTrue_WhenNewerSnapshotFileProcessed() {
        // given
        final String fileName = TEST_SNAPSHOT_20240115_T120000_Z_XML;
        final OffsetDateTime fileDateTime = OffsetDateTime.parse(VALUE_2024_01_15_T12_00_00_Z);
        final String fileNamePrefix = TEST_SNAPSHOT;
        final Instant timestamp = fileDateTime.toInstant();

        final RotaFileProcessHistory newerFile = new RotaFileProcessHistory();
        newerFile.setExecutionId("newer-execution-id");
        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp)))
                .thenReturn(List.of(newerFile));

        // when
        final boolean result = rotaFileUtility.isNewerSnapshotFileProcessed(fileName);

        // then
        assertTrue(result);
        verify(rotaFileProcessHistoryRepository).findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp));
    }

    @Test
    void shouldReturnTrue_WhenMultipleNewerSnapshotFilesProcessed() {
        // given
        final String fileName = TEST_SNAPSHOT_20240115_T120000_Z_XML;
        final OffsetDateTime fileDateTime = OffsetDateTime.parse(VALUE_2024_01_15_T12_00_00_Z);
        final String fileNamePrefix = TEST_SNAPSHOT;
        final Instant timestamp = fileDateTime.toInstant();

        final RotaFileProcessHistory newerFile1 = new RotaFileProcessHistory();
        newerFile1.setExecutionId("newer-execution-id-1");
        final RotaFileProcessHistory newerFile2 = new RotaFileProcessHistory();
        newerFile2.setExecutionId("newer-execution-id-2");
        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp)))
                .thenReturn(List.of(newerFile1, newerFile2));

        // when
        final boolean result = rotaFileUtility.isNewerSnapshotFileProcessed(fileName);

        // then
        assertTrue(result);
    }

    @Test
    void shouldReturnFalse_WhenNoNewerSnapshotFileProcessed() {
        // given
        final String fileName = TEST_SNAPSHOT_20240115_T120000_Z_XML;
        final OffsetDateTime fileDateTime = OffsetDateTime.parse(VALUE_2024_01_15_T12_00_00_Z);
        final String fileNamePrefix = TEST_SNAPSHOT;
        final Instant timestamp = fileDateTime.toInstant();

        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp)))
                .thenReturn(emptyList());

        // when
        final boolean result = rotaFileUtility.isNewerSnapshotFileProcessed(fileName);

        // then
        assertFalse(result);
        verify(rotaFileProcessHistoryRepository).findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp));
    }

    @Test
    void shouldReturnTrue_WhenFileDateTimeIsNull() {
        // given
        final String fileName = "invalid_file_name.csv";

        // when
        final boolean result = rotaFileUtility.isNewerSnapshotFileProcessed(fileName);

        // then
        assertTrue(result); // Should return true when date is invalid
    }

    @Test
    void shouldCreateAndSaveFileProcessHistory_WhenValidFile() {
        // given
        final String fileName = TEST_SNAPSHOT_20240115_T120000_Z_XML;
        final byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        final OffsetDateTime fileDateTime = OffsetDateTime.parse(VALUE_2024_01_15_T12_00_00_Z);
        final String fileNamePrefix = TEST_SNAPSHOT;

        final RotaFileProcessHistory mockHistory = new RotaFileProcessHistory();
        mockHistory.setExecutionId("test-execution-id");
        mockHistory.setFileNamePrefix(fileNamePrefix);
        when(rotaFileProcessHistoryService.save(eq(fileNamePrefix), eq(fileDateTime), eq(content), anyString()))
                .thenReturn(mockHistory);

        // when
        final RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
                fileName, content, rotaFileProcessHistoryService);

        // then
        assertNotNull(result);
        assertNotNull(result.getExecutionId());
        assertFalse(result.getExecutionId().isEmpty());
        assertEquals("test-execution-id", result.getExecutionId());
        verify(rotaFileProcessHistoryService).save(eq(fileNamePrefix), eq(fileDateTime), eq(content), anyString());
    }

    @Test
    void shouldCreateAndSaveFileProcessHistory_WhenValidRotaFile() {
        // given
        final String fileName = "test_rota_20240115T120000Z.xml";
        final byte[] content = "rota file content".getBytes(StandardCharsets.UTF_8);
        final OffsetDateTime fileDateTime = OffsetDateTime.parse(VALUE_2024_01_15_T12_00_00_Z);
        // For non-snapshot files, getLJAFileNamePrefix returns prefix (everything before timestamp)
        final String fileNamePrefix = "test_rota_";

        final RotaFileProcessHistory mockHistory = new RotaFileProcessHistory();
        mockHistory.setExecutionId("rota-execution-id");
        when(rotaFileProcessHistoryService.save(eq(fileNamePrefix), eq(fileDateTime), eq(content), anyString()))
                .thenReturn(mockHistory);

        // when
        final RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
                fileName, content, rotaFileProcessHistoryService);

        // then
        assertNotNull(result);
        assertEquals("rota-execution-id", result.getExecutionId());
        verify(rotaFileProcessHistoryService).save(eq(fileNamePrefix), eq(fileDateTime), eq(content), anyString());
    }

    @Test
    void shouldReturnNull_WhenFileTimestampCannotBeExtracted() {
        // given
        final String fileName = "test_file.csv";
        final byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);

        // when
        final RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
                fileName, content, rotaFileProcessHistoryService);

        // then
        assertThat(result, is(org.hamcrest.Matchers.nullValue()));
        verify(rotaFileProcessHistoryService, never()).save(anyString(), any(), any(), anyString());
    }

    @Test
    void shouldCreateAndSaveFileProcessHistory_WhenFileNameHasNoTimestamp() {
        // given
        final String fileName = "invalid_filename_without_timestamp.xml";
        final byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        // For files without timestamp, getLJAFileNamePrefix returns original filename
        // (because generated timestamp is not in filename)
        final String fileNamePrefix = "invalid_filename_without_timestamp.xml";
        // getLJAFileTimeStampAsString returns current timestamp when no timestamp found in filename
        // So we need to use any(OffsetDateTime.class) to match the dynamically generated timestamp

        final RotaFileProcessHistory mockHistory = new RotaFileProcessHistory();
        mockHistory.setExecutionId("generated-execution-id");
        when(rotaFileProcessHistoryService.save(eq(fileNamePrefix), any(OffsetDateTime.class), eq(content), anyString()))
                .thenReturn(mockHistory);

        // when
        final RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
                fileName, content, rotaFileProcessHistoryService);

        // then
        assertNotNull(result);
        assertNotNull(result.getExecutionId());
        verify(rotaFileProcessHistoryService).save(eq(fileNamePrefix), any(OffsetDateTime.class), eq(content), anyString());
    }

    @Test
    void shouldReturnNull_WhenFileNameIsEmpty() {
        // given
        final String fileName = "";
        final byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);

        // when
        final RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
                fileName, content, rotaFileProcessHistoryService);

        // then
        assertThat(result, is(org.hamcrest.Matchers.nullValue()));
        verify(rotaFileProcessHistoryService, never()).save(anyString(), any(), any(), anyString());
    }

    @Test
    void shouldHandleCaseSensitiveDummyFileCheck() {
        // given
        final String fileName1 = "DUMMYSUPPORT_file.xml";
        final String fileName2 = "DummySupport_file.xml";
        final String fileName3 = "dummysupport_file.xml";

        // when
        final boolean result1 = rotaFileUtility.isDummyFile(fileName1);
        final boolean result2 = rotaFileUtility.isDummyFile(fileName2);
        final boolean result3 = rotaFileUtility.isDummyFile(fileName3);

        // then
        assertFalse(result1); // Case sensitive - uppercase doesn't match
        assertFalse(result2); // Case sensitive - mixed case doesn't match
        assertTrue(result3); // Case sensitive - lowercase matches
    }

    @Test
    void shouldHandleDummyFileInPath() {
        // given
        final String fileName = "path/to/dummysupport/file.xml";

        // when
        final boolean result = rotaFileUtility.isDummyFile(fileName);

        // then
        assertTrue(result);
    }

    @Test
    void shouldConvertLargeNanosToMillis() {
        // given
        final long nanos = 1_000_000_000L; // 1 second

        // when
        final long result = rotaFileUtility.convertNanosToMillis(nanos);

        // then
        assertThat(result, is(1000L));
    }

    @Test
    void shouldConvertNegativeNanosToMillis() {
        // given
        final long nanos = -5_000_000L;

        // when
        final long result = rotaFileUtility.convertNanosToMillis(nanos);

        // then
        assertThat(result, is(-5L));
    }

    // ============================================================================
    // Tests for isSnapshotFile
    // ============================================================================

    /* default */
    @Nested
    @DisplayName("Snapshot File Detection Tests")
    class SnapshotFileDetectionTests {

        @Test
        @DisplayName("Should identify snapshot file")
        void shouldIdentifySnapshotFile() {
            // given
            final String fileName = TEST_SNAPSHOT_20240115_T120000_Z_XML;

            // when
            final boolean result = rotaFileUtility.isSnapshotFile(fileName);

            // then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should not identify non-snapshot file")
        void shouldNotIdentifyNonSnapshotFile() {
            // given
            final String fileName = "test_file.xml";

            // when
            final boolean result = rotaFileUtility.isSnapshotFile(fileName);

            // then
            assertFalse(result);
        }

        @Test
        @DisplayName("Should identify snapshot file with different extension")
        void shouldIdentifySnapshotFileWithDifferentExtension() {
            // given
            final String fileName = "data_snapshot_20240115.csv";

            // when
            final boolean result = rotaFileUtility.isSnapshotFile(fileName);

            // then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should identify snapshot file in path")
        void shouldIdentifySnapshotFileInPath() {
            // given
            final String fileName = "path/to/test_snapshot_20240115T120000Z.xml";

            // when
            final boolean result = rotaFileUtility.isSnapshotFile(fileName);

            // then
            assertTrue(result);
        }

        @Test
        @DisplayName("Should handle case sensitive snapshot file check")
        void shouldHandleCaseSensitiveSnapshotFileCheck() {
            // given
            final String fileName1 = "test_SNAPSHOT_20240115T120000Z.xml";
            final String fileName2 = "test_Snapshot_20240115T120000Z.xml";
            final String fileName3 = TEST_SNAPSHOT_20240115_T120000_Z_XML;

            // when
            final boolean result1 = rotaFileUtility.isSnapshotFile(fileName1);
            final boolean result2 = rotaFileUtility.isSnapshotFile(fileName2);
            final boolean result3 = rotaFileUtility.isSnapshotFile(fileName3);

            // then
            assertFalse(result1); // Case sensitive - uppercase doesn't match
            assertFalse(result2); // Case sensitive - mixed case doesn't match
            assertTrue(result3); // Case sensitive - lowercase matches
        }
    }

    // ============================================================================
    // Tests for logProcessingTime
    // ============================================================================

    /* default */
    @Nested
    @DisplayName("Processing Time Logging Tests")
    class ProcessingTimeLoggingTests {

        @Test
        @DisplayName("Should log processing time correctly")
        void shouldLogProcessingTimeCorrectly() {
            // given
            final Logger logger = mock(Logger.class);
            final String blobName = TEST_BLOB_XML;
            final long processStart = 1_000_000_000L; // 1 second in nanos
            final long processEnd = 2_500_000_000L; // 2.5 seconds in nanos
            // Expected: (2.5 - 1.0) seconds = 1.5 seconds = 1500 milliseconds

            // when
            rotaFileUtility.logProcessingTime(logger, blobName, processStart, processEnd);

            // then
            verify(logger).info(eq(PRF_PROCESSING_AND_PARSING_COMPLETED_FOR_BLOB_IN),
                    eq(blobName), eq(1500L));
        }

        @Test
        @DisplayName("Should log zero processing time")
        void shouldLogZeroProcessingTime() {
            // given
            final Logger logger = mock(Logger.class);
            final String blobName = TEST_BLOB_XML;
            final long processStart = 1_000_000_000L;
            final long processEnd = 1_000_000_000L; // Same time

            // when
            rotaFileUtility.logProcessingTime(logger, blobName, processStart, processEnd);

            // then
            verify(logger).info(eq(PRF_PROCESSING_AND_PARSING_COMPLETED_FOR_BLOB_IN),
                    eq(blobName), eq(0L));
        }

        @Test
        @DisplayName("Should log processing time for large duration")
        void shouldLogProcessingTimeForLargeDuration() {
            // given
            final Logger logger = mock(Logger.class);
            final String blobName = "large_file.xml";
            final long processStart = 0L;
            final long processEnd = 10_000_000_000L; // 10 seconds in nanos

            // when
            rotaFileUtility.logProcessingTime(logger, blobName, processStart, processEnd);

            // then
            verify(logger).info(eq(PRF_PROCESSING_AND_PARSING_COMPLETED_FOR_BLOB_IN),
                    eq(blobName), eq(10_000L));
        }

        @Test
        @DisplayName("Should log processing time with fractional milliseconds")
        void shouldLogProcessingTimeWithFractionalMilliseconds() {
            // given
            final Logger logger = mock(Logger.class);
            final String blobName = TEST_BLOB_XML;
            final long processStart = 0L;
            final long processEnd = 1_500_000L; // 1.5 milliseconds in nanos

            // when
            rotaFileUtility.logProcessingTime(logger, blobName, processStart, processEnd);

            // then
            verify(logger).info(eq(PRF_PROCESSING_AND_PARSING_COMPLETED_FOR_BLOB_IN),
                    eq(blobName), eq(1L)); // Should round down to 1ms
        }
    }

    // ============================================================================
    // Tests for updateFileProcessHistory
    // ============================================================================

    /* default */
    @Nested
    @DisplayName("File Process History Update Tests")
    class FileProcessHistoryUpdateTests {

        @Test
        @DisplayName("Should update file process history when history exists")
        void shouldUpdateFileProcessHistoryWhenHistoryExists() {
            // given
            final Logger logger = mock(Logger.class);
            final String blobName = TEST_BLOB_XML;
            final RotaFileProcessHistory rotaFileProcessHistory = new RotaFileProcessHistory();
            rotaFileProcessHistory.setExecutionId("test-execution-id");
            when(rotaFileProcessHistoryService.update(eq(rotaFileProcessHistory)))
                    .thenReturn(rotaFileProcessHistory);

            // when
            rotaFileUtility.updateFileProcessHistory(logger, rotaFileProcessHistory, blobName, rotaFileProcessHistoryService);

            // then
            verify(rotaFileProcessHistoryService).update(eq(rotaFileProcessHistory));
            verify(logger).info(eq(UPDATED_FILE_PROCESS_HISTORY_WITH_END_DATE_FOR_B), eq(blobName));
        }

        @Test
        @DisplayName("Should not update file process history when history is null")
        void shouldNotUpdateFileProcessHistoryWhenHistoryIsNull() {
            // given
            final Logger logger = mock(Logger.class);
            final String blobName = TEST_BLOB_XML;
            final RotaFileProcessHistory rotaFileProcessHistory = null;

            // when
            rotaFileUtility.updateFileProcessHistory(logger, rotaFileProcessHistory, blobName, rotaFileProcessHistoryService);

            // then
            verify(rotaFileProcessHistoryService, never()).update(any(RotaFileProcessHistory.class));
            verify(logger, never()).info(anyString(), anyString());
        }

        @Test
        @DisplayName("Should update file process history with different blob names")
        void shouldUpdateFileProcessHistoryWithDifferentBlobNames() {
            // given
            final Logger logger = mock(Logger.class);
            final String blobName1 = "test_blob_1.xml";
            final String blobName2 = "test_blob_2.xml";
            final RotaFileProcessHistory rotaFileProcessHistory1 = new RotaFileProcessHistory();
            rotaFileProcessHistory1.setExecutionId("execution-id-1");
            final RotaFileProcessHistory rotaFileProcessHistory2 = new RotaFileProcessHistory();
            rotaFileProcessHistory2.setExecutionId("execution-id-2");
            when(rotaFileProcessHistoryService.update(any(RotaFileProcessHistory.class)))
                    .thenReturn(rotaFileProcessHistory1)
                    .thenReturn(rotaFileProcessHistory2);

            // when
            rotaFileUtility.updateFileProcessHistory(logger, rotaFileProcessHistory1, blobName1, rotaFileProcessHistoryService);
            rotaFileUtility.updateFileProcessHistory(logger, rotaFileProcessHistory2, blobName2, rotaFileProcessHistoryService);

            // then
            verify(rotaFileProcessHistoryService).update(eq(rotaFileProcessHistory1));
            verify(rotaFileProcessHistoryService).update(eq(rotaFileProcessHistory2));
            verify(logger).info(eq(UPDATED_FILE_PROCESS_HISTORY_WITH_END_DATE_FOR_B), eq(blobName1));
            verify(logger).info(eq(UPDATED_FILE_PROCESS_HISTORY_WITH_END_DATE_FOR_B), eq(blobName2));
        }

        @Test
        @DisplayName("Should handle update service returning updated history")
        void shouldHandleUpdateServiceReturningUpdatedHistory() {
            // given
            final Logger logger = mock(Logger.class);
            final String blobName = TEST_BLOB_XML;
            final RotaFileProcessHistory originalHistory = new RotaFileProcessHistory();
            originalHistory.setExecutionId("original-execution-id");
            final RotaFileProcessHistory updatedHistory = new RotaFileProcessHistory();
            updatedHistory.setExecutionId("updated-execution-id");
            when(rotaFileProcessHistoryService.update(eq(originalHistory)))
                    .thenReturn(updatedHistory);

            // when
            rotaFileUtility.updateFileProcessHistory(logger, originalHistory, blobName, rotaFileProcessHistoryService);

            // then
            verify(rotaFileProcessHistoryService).update(eq(originalHistory));
            verify(logger).info(eq(UPDATED_FILE_PROCESS_HISTORY_WITH_END_DATE_FOR_B), eq(blobName));
        }
    }
}

