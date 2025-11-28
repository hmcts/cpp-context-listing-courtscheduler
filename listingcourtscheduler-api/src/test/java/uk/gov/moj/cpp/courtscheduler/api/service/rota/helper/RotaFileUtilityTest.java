package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaFileUtility;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
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
    void shouldIdentifySnapshotFile() {
        // given
        String fileName = "test_snapshot_20240115.csv";

        // when
        boolean result = rotaFileUtility.isSnapshotFile(fileName);

        // then
        assertTrue(result);
    }

    @Test
    void shouldNotIdentifyNonSnapshotFile() {
        // given
        String fileName = "test_file_20240115.csv";

        // when
        boolean result = rotaFileUtility.isSnapshotFile(fileName);

        // then
        assertFalse(result);
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

        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp)))
                .thenReturn(List.of(new uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory()));

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
    void shouldProcessSnapshotFile_WhenValidSnapshotFile() {
        // given
        String fileName = "test_snapshot_20240115T120000Z.xml";
        byte[] content = "test content".getBytes();
        OffsetDateTime fileDateTime = OffsetDateTime.parse("2024-01-15T12:00:00Z");
        String fileNamePrefix = "test_snapshot_";
        Timestamp timestamp = Timestamp.from(fileDateTime.toInstant());

        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp)))
                .thenReturn(emptyList());

        // when
        String executionId = rotaFileUtility.processSnapshotFileIfNeeded(fileName, content, rotaFileProcessHistoryService);

        // then
        assertNotNull(executionId);
        assertFalse(executionId.isEmpty());
        verify(rotaFileProcessHistoryService).save(eq(fileNamePrefix), eq(fileDateTime), eq(content), anyString());
    }

    @Test
    void shouldReturnEmptyString_WhenNotSnapshotFile() {
        // given
        String fileName = "test_file.csv";
        byte[] content = "test content".getBytes();

        // when
        String executionId = rotaFileUtility.processSnapshotFileIfNeeded(fileName, content, rotaFileProcessHistoryService);

        // then
        assertNotNull(executionId);
        assertTrue(executionId.isEmpty());
        verify(rotaFileProcessHistoryService, never()).save(anyString(), any(), any(), anyString());
    }

    @Test
    void shouldThrowException_WhenNewerSnapshotFileAlreadyProcessed() {
        // given
        String fileName = "test_snapshot_20240115T120000Z.xml";
        byte[] content = "test content".getBytes();
        OffsetDateTime fileDateTime = OffsetDateTime.parse("2024-01-15T12:00:00Z");
        String fileNamePrefix = "test_snapshot_";
        Timestamp timestamp = Timestamp.from(fileDateTime.toInstant());

        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(
                eq(fileNamePrefix), eq(timestamp)))
                .thenReturn(List.of(new uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory()));

        // when & then
        assertThrows(IllegalStateException.class, () ->
                rotaFileUtility.processSnapshotFileIfNeeded(fileName, content, rotaFileProcessHistoryService));
    }
}

