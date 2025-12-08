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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFileUtilityTest {

    @Mock
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    @Mock
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @InjectMocks
    private RotaFileUtility rotaFileUtility;

    @Test
    void shouldConvertNanosToMillis() {
        // given
        long nanos = 5_000_000L; // 5 milliseconds

        // when
        long result = rotaFileUtility.convertNanosToMillis(nanos);

        // then
        assertThat(result, is(5L));
    }

    @Test
    void shouldConvertZeroNanosToZeroMillis() {
        // given
        long nanos = 0L;

        // when
        long result = rotaFileUtility.convertNanosToMillis(nanos);

        // then
        assertThat(result, is(0L));
    }


    @Test
    void shouldIdentifyDummyFile() {
        // given
        String fileName = "dummysupport_file.csv";

        // when
        boolean result = rotaFileUtility.isDummyFile(fileName);

        // then
        assertTrue(result);
    }

    @Test
    void shouldNotIdentifyNonDummyFile() {
        // given
        String fileName = "test_file.csv";

        // when
        boolean result = rotaFileUtility.isDummyFile(fileName);

        // then
        assertFalse(result);
    }

    @Test
    void shouldReturnTrue_WhenNewerSnapshotFileProcessed() {
        // given
        String fileName = "test_snapshot_20240115T120000Z.xml";
        OffsetDateTime fileDateTime = OffsetDateTime.parse("2024-01-15T12:00:00Z");
        String fileNamePrefix = "test_snapshot_";
        Timestamp timestamp = Timestamp.from(fileDateTime.toInstant());

        RotaFileProcessHistory newerFile = new RotaFileProcessHistory();
        newerFile.setExecutionId("newer-execution-id");
        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp)))
                .thenReturn(List.of(newerFile));

        // when
        boolean result = rotaFileUtility.isNewerSnapshotFileProcessed(fileName);

        // then
        assertTrue(result);
        verify(rotaFileProcessHistoryRepository).findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp));
    }

    @Test
    void shouldReturnTrue_WhenMultipleNewerSnapshotFilesProcessed() {
        // given
        String fileName = "test_snapshot_20240115T120000Z.xml";
        OffsetDateTime fileDateTime = OffsetDateTime.parse("2024-01-15T12:00:00Z");
        String fileNamePrefix = "test_snapshot_";
        Timestamp timestamp = Timestamp.from(fileDateTime.toInstant());

        RotaFileProcessHistory newerFile1 = new RotaFileProcessHistory();
        newerFile1.setExecutionId("newer-execution-id-1");
        RotaFileProcessHistory newerFile2 = new RotaFileProcessHistory();
        newerFile2.setExecutionId("newer-execution-id-2");
        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp)))
                .thenReturn(List.of(newerFile1, newerFile2));

        // when
        boolean result = rotaFileUtility.isNewerSnapshotFileProcessed(fileName);

        // then
        assertTrue(result);
    }

    @Test
    void shouldReturnFalse_WhenNoNewerSnapshotFileProcessed() {
        // given
        String fileName = "test_snapshot_20240115T120000Z.xml";
        OffsetDateTime fileDateTime = OffsetDateTime.parse("2024-01-15T12:00:00Z");
        String fileNamePrefix = "test_snapshot_";
        Timestamp timestamp = Timestamp.from(fileDateTime.toInstant());

        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp)))
                .thenReturn(emptyList());

        // when
        boolean result = rotaFileUtility.isNewerSnapshotFileProcessed(fileName);

        // then
        assertFalse(result);
        verify(rotaFileProcessHistoryRepository).findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp));
    }

    @Test
    void shouldReturnTrue_WhenFileDateTimeIsNull() {
        // given
        String fileName = "invalid_file_name.csv";

        // when
        boolean result = rotaFileUtility.isNewerSnapshotFileProcessed(fileName);

        // then
        assertTrue(result); // Should return true when date is invalid
    }

    @Test
    void shouldCreateAndSaveFileProcessHistory_WhenValidFile() {
        // given
        String fileName = "test_snapshot_20240115T120000Z.xml";
        byte[] content = "test content".getBytes();
        OffsetDateTime fileDateTime = OffsetDateTime.parse("2024-01-15T12:00:00Z");
        String fileNamePrefix = "test_snapshot_";

        RotaFileProcessHistory mockHistory = new RotaFileProcessHistory();
        mockHistory.setExecutionId("test-execution-id");
        mockHistory.setFileNamePrefix(fileNamePrefix);
        when(rotaFileProcessHistoryService.save(eq(fileNamePrefix), eq(fileDateTime), eq(content), anyString()))
                .thenReturn(mockHistory);

        // when
        RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
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
        String fileName = "test_rota_20240115T120000Z.xml";
        byte[] content = "rota file content".getBytes();
        OffsetDateTime fileDateTime = OffsetDateTime.parse("2024-01-15T12:00:00Z");
        String fileNamePrefix = "test_rota_";

        RotaFileProcessHistory mockHistory = new RotaFileProcessHistory();
        mockHistory.setExecutionId("rota-execution-id");
        when(rotaFileProcessHistoryService.save(eq(fileNamePrefix), eq(fileDateTime), eq(content), anyString()))
                .thenReturn(mockHistory);

        // when
        RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
                fileName, content, rotaFileProcessHistoryService);

        // then
        assertNotNull(result);
        assertEquals("rota-execution-id", result.getExecutionId());
        verify(rotaFileProcessHistoryService).save(eq(fileNamePrefix), eq(fileDateTime), eq(content), anyString());
    }

    @Test
    void shouldReturnNull_WhenFileTimestampCannotBeExtracted() {
        // given
        String fileName = "test_file.csv";
        byte[] content = "test content".getBytes();

        // when
        RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
                fileName, content, rotaFileProcessHistoryService);

        // then
        assertThat(result, is(org.hamcrest.Matchers.nullValue()));
        verify(rotaFileProcessHistoryService, never()).save(anyString(), any(), any(), anyString());
    }

    @Test
    void shouldReturnNull_WhenFileNameHasNoTimestamp() {
        // given
        String fileName = "invalid_filename_without_timestamp.xml";
        byte[] content = "test content".getBytes();

        // when
        RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
                fileName, content, rotaFileProcessHistoryService);

        // then
        assertThat(result, is(org.hamcrest.Matchers.nullValue()));
        verify(rotaFileProcessHistoryService, never()).save(anyString(), any(), any(), anyString());
    }

    @Test
    void shouldReturnNull_WhenFileNameIsEmpty() {
        // given
        String fileName = "";
        byte[] content = "test content".getBytes();

        // when
        RotaFileProcessHistory result = rotaFileUtility.createAndSaveFileProcessHistory(
                fileName, content, rotaFileProcessHistoryService);

        // then
        assertThat(result, is(org.hamcrest.Matchers.nullValue()));
        verify(rotaFileProcessHistoryService, never()).save(anyString(), any(), any(), anyString());
    }

    @Test
    void shouldHandleCaseSensitiveDummyFileCheck() {
        // given
        String fileName1 = "DUMMYSUPPORT_file.xml";
        String fileName2 = "DummySupport_file.xml";
        String fileName3 = "dummysupport_file.xml";

        // when
        boolean result1 = rotaFileUtility.isDummyFile(fileName1);
        boolean result2 = rotaFileUtility.isDummyFile(fileName2);
        boolean result3 = rotaFileUtility.isDummyFile(fileName3);

        // then
        assertFalse(result1); // Case sensitive - uppercase doesn't match
        assertFalse(result2); // Case sensitive - mixed case doesn't match
        assertTrue(result3); // Case sensitive - lowercase matches
    }

    @Test
    void shouldHandleDummyFileInPath() {
        // given
        String fileName = "path/to/dummysupport/file.xml";

        // when
        boolean result = rotaFileUtility.isDummyFile(fileName);

        // then
        assertTrue(result);
    }

    @Test
    void shouldConvertLargeNanosToMillis() {
        // given
        long nanos = 1_000_000_000L; // 1 second

        // when
        long result = rotaFileUtility.convertNanosToMillis(nanos);

        // then
        assertThat(result, is(1000L));
    }

    @Test
    void shouldConvertNegativeNanosToMillis() {
        // given
        long nanos = -5_000_000L;

        // when
        long result = rotaFileUtility.convertNanosToMillis(nanos);

        // then
        assertThat(result, is(-5L));
    }
}

