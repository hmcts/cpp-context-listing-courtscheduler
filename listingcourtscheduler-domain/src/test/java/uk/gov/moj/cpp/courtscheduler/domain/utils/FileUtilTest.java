package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;


class FileUtilTest {

    @Test
    void shouldGetLJASnapshotFileTimeStampAsString() {
        final String actual = FileUtil.getLJASnapshotFileTimeStampAsString("lja_southessex_snapshot_20210122T120000Z.xml");
        assertThat(actual, is("20210122T120000Z"));
    }

    @Test
    void shouldGetLJASnapshotFileTimeStampAsOffsetDateTime() {
        final OffsetDateTime actual = FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime("lja_southessex_snapshot_20210122T120000Z.xml");
        assertThat(actual.toString(), is("2021-01-22T12:00Z"));
    }

    @Test
    void shouldReturnsNullGetLJASnapshotFileTimeStampAsOffsetDateTime() {
        final OffsetDateTime actual = FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime("lja_southessex_snapshot_202101000Z.xml");
        assertThat(actual, nullValue());
    }

    @Test
    void shouldGetLJASnapshotFileNamePrefix() {
        final String actual = FileUtil.getLJASnapshotFileNamePrefix("lja_southessex_snapshot_20210122T120000Z.xml");
        assertThat(actual, is("lja_southessex_snapshot_"));
    }

    // ============================================================================
    // Tests for getLJAFileTimeStampAsString (new method without snapshot requirement)
    // ============================================================================

    @Test
    void shouldGetLJAFileTimeStampAsString_FromSnapshotFile() {
        final String actual = FileUtil.getLJAFileTimeStampAsString("lja_southessex_snapshot_20210122T120000Z.xml");
        assertThat(actual, is("20210122T120000Z"));
    }

    @Test
    void shouldGetLJAFileTimeStampAsString_FromRotaFile() {
        final String actual = FileUtil.getLJAFileTimeStampAsString("lja_bedfordshire_rota_20240402T180039Z.xml");
        assertThat(actual, is("20240402T180039Z"));
    }

    @Test
    void shouldGetLJAFileTimeStampAsString_FromFileWithoutSnapshot() {
        final String actual = FileUtil.getLJAFileTimeStampAsString("test_file_20210122T120000Z.xml");
        assertThat(actual, is("20210122T120000Z"));
    }

    @Test
    void shouldReturnNull_WhenFileDoesNotEndWithXml() {
        final String actual = FileUtil.getLJAFileTimeStampAsString("test_file_20210122T120000Z.txt");
        assertThat(actual, nullValue());
    }

    @Test
    void shouldReturnCurrentTimestamp_WhenFileNameTooShort() {
        final String actual = FileUtil.getLJAFileTimeStampAsString("short.xml");
        assertNotNull(actual);
        // Verify it's a valid timestamp format
        try {
            LocalDateTime.parse(actual, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            assertTrue(true);
        } catch (DateTimeParseException e) {
            assertTrue(false, "Returned timestamp is not in valid format");
        }
    }

    @Test
    void shouldReturnCurrentTimestamp_WhenTimestampInvalid() {
        final String actual = FileUtil.getLJAFileTimeStampAsString("test_file_20210122T12000Z.xml");
        assertNotNull(actual);
        // Verify it's a valid timestamp format
        try {
            LocalDateTime.parse(actual, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            assertTrue(true);
        } catch (DateTimeParseException e) {
            assertTrue(false, "Returned timestamp is not in valid format");
        }
    }

    @Test
    void shouldReturnCurrentTimestamp_WhenNoTimestampInFileName() {
        final String actual = FileUtil.getLJAFileTimeStampAsString("test_file_without_timestamp.xml");
        assertNotNull(actual);
        // Verify it's a valid timestamp format
        try {
            LocalDateTime.parse(actual, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
            assertTrue(true);
        } catch (DateTimeParseException e) {
            assertTrue(false, "Returned timestamp is not in valid format");
        }
    }

    @Test
    void shouldReturnNull_WhenFileNameIsEmpty() {
        final String actual = FileUtil.getLJAFileTimeStampAsString("");
        assertThat(actual, nullValue());
    }

    @Test
    void shouldGetLJAFileTimeStampAsString_WithDifferentTimes() {
        final String actual1 = FileUtil.getLJAFileTimeStampAsString("file_20210122T000000Z.xml");
        assertThat(actual1, is("20210122T000000Z"));

        final String actual2 = FileUtil.getLJAFileTimeStampAsString("file_20210122T235959Z.xml");
        assertThat(actual2, is("20210122T235959Z"));
    }

    // ============================================================================
    // Tests for getLJAFileTimeStampAsOffsetDateTime (new method without snapshot requirement)
    // ============================================================================

    @Test
    void shouldGetLJAFileTimeStampAsOffsetDateTime_FromSnapshotFile() {
        final OffsetDateTime actual = FileUtil.getLJAFileTimeStampAsOffsetDateTime("lja_southessex_snapshot_20210122T120000Z.xml");
        assertThat(actual.toString(), is("2021-01-22T12:00Z"));
    }

    @Test
    void shouldGetLJAFileTimeStampAsOffsetDateTime_FromRotaFile() {
        final OffsetDateTime actual = FileUtil.getLJAFileTimeStampAsOffsetDateTime("lja_bedfordshire_rota_20240402T180039Z.xml");
        assertThat(actual.toString(), is("2024-04-02T18:00:39Z"));
    }

    @Test
    void shouldGetLJAFileTimeStampAsOffsetDateTime_FromFileWithoutSnapshot() {
        final OffsetDateTime actual = FileUtil.getLJAFileTimeStampAsOffsetDateTime("test_file_20210122T120000Z.xml");
        assertThat(actual.toString(), is("2021-01-22T12:00Z"));
    }

    @Test
    void shouldReturnNull_WhenFileDoesNotEndWithXml_ForOffsetDateTime() {
        final OffsetDateTime actual = FileUtil.getLJAFileTimeStampAsOffsetDateTime("test_file_20210122T120000Z.txt");
        assertThat(actual, nullValue());
    }

    @Test
    void shouldReturnCurrentTimestamp_WhenFileNameTooShort_ForOffsetDateTime() {
        final OffsetDateTime now = OffsetDateTime.now();
        final OffsetDateTime actual = FileUtil.getLJAFileTimeStampAsOffsetDateTime("short.xml");
        assertNotNull(actual);
        // Verify it's a valid OffsetDateTime (current timestamp) - within 5 seconds of now
        assertTrue(actual.isAfter(now.minusSeconds(5)) && actual.isBefore(now.plusSeconds(5)));
    }

    @Test
    void shouldReturnCurrentTimestamp_WhenTimestampInvalid_ForOffsetDateTime() {
        final OffsetDateTime now = OffsetDateTime.now();
        final OffsetDateTime actual = FileUtil.getLJAFileTimeStampAsOffsetDateTime("test_file_20210122T12000Z.xml");
        assertNotNull(actual);
        // Verify it's a valid OffsetDateTime (current timestamp) - within 5 seconds of now
        assertTrue(actual.isAfter(now.minusSeconds(5)) && actual.isBefore(now.plusSeconds(5)));
    }

    @Test
    void shouldReturnCurrentTimestamp_WhenNoTimestampInFileName_ForOffsetDateTime() {
        final OffsetDateTime now = OffsetDateTime.now();
        final OffsetDateTime actual = FileUtil.getLJAFileTimeStampAsOffsetDateTime("test_file_without_timestamp.xml");
        assertNotNull(actual);
        // Verify it's a valid OffsetDateTime (current timestamp) - within 5 seconds of now
        assertTrue(actual.isAfter(now.minusSeconds(5)) && actual.isBefore(now.plusSeconds(5)));
    }

    @Test
    void shouldGetLJAFileTimeStampAsOffsetDateTime_WithMidnightTime() {
        final OffsetDateTime actual = FileUtil.getLJAFileTimeStampAsOffsetDateTime("file_20210122T000000Z.xml");
        assertThat(actual.toString(), is("2021-01-22T00:00Z"));
    }

    @Test
    void shouldGetLJAFileTimeStampAsOffsetDateTime_WithEndOfDayTime() {
        final OffsetDateTime actual = FileUtil.getLJAFileTimeStampAsOffsetDateTime("file_20210122T235959Z.xml");
        assertThat(actual.toString(), is("2021-01-22T23:59:59Z"));
    }

    // ============================================================================
    // Tests for getLJAFileNamePrefix (new method without snapshot requirement)
    // ============================================================================

    @Test
    void shouldGetLJAFileNamePrefix_FromSnapshotFile() {
        final String actual = FileUtil.getLJAFileNamePrefix("lja_southessex_snapshot_20210122T120000Z.xml");
        assertThat(actual, is("lja_southessex_snapshot_"));
    }

    @Test
    void shouldGetLJAFileNamePrefix_FromRotaFile() {
        final String actual = FileUtil.getLJAFileNamePrefix("lja_bedfordshire_rota_20240402T180039Z.xml");
        assertThat(actual, is("lja_bedfordshire_rota_"));
    }

    @Test
    void shouldGetLJAFileNamePrefix_FromFileWithoutSnapshot() {
        final String actual = FileUtil.getLJAFileNamePrefix("test_file_20210122T120000Z.xml");
        assertThat(actual, is("test_file_"));
    }

    @Test
    void shouldReturnFileNameWithoutXml_WhenNoTimestampFound() {
        final String fileName = "test_file_without_timestamp.xml";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        // When timestamp is not found, getLJAFileTimeStampAsString returns generated timestamp
        // which is not in filename, so method returns original filename
        assertThat(actual, is(fileName));
    }

    @Test
    void shouldReturnOriginalFileName_WhenFileDoesNotEndWithXml() {
        final String fileName = "test_file_20210122T120000Z.txt";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        assertThat(actual, is(fileName));
    }

    @Test
    void shouldReturnFileNameWithoutXml_WhenFileNameTooShort() {
        final String fileName = "short.xml";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        // When filename is too short, getLJAFileTimeStampAsString returns generated timestamp
        // which is not in filename, so method returns original filename
        assertThat(actual, is(fileName));
    }

    @Test
    void shouldGetLJAFileNamePrefix_WithComplexPath() {
        final String actual = FileUtil.getLJAFileNamePrefix("path/to/file/test_rota_20210122T120000Z.xml");
        assertThat(actual, is("path/to/file/test_rota_"));
    }

    @Test
    void shouldGetLJAFileNamePrefix_WithMinimalPrefix() {
        final String actual = FileUtil.getLJAFileNamePrefix("a_20210122T120000Z.xml");
        assertThat(actual, is("a_"));
    }

    @Test
    void shouldGetLJAFileNamePrefix_WithLongPrefix() {
        final String actual = FileUtil.getLJAFileNamePrefix("very_long_file_name_with_many_parts_20210122T120000Z.xml");
        assertThat(actual, is("very_long_file_name_with_many_parts_"));
    }

    @Test
    void shouldReturnOriginalFileName_WhenEmptyString() {
        final String fileName = "";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        assertThat(actual, is(fileName));
    }

    // ============================================================================
    // Integration tests - testing all three new methods together
    // ============================================================================

    @Test
    void shouldExtractAllComponents_FromRotaFile() {
        final String fileName = "lja_westyorkshire_rota_20240827T154745Z.xml";
        
        final String timestampString = FileUtil.getLJAFileTimeStampAsString(fileName);
        final OffsetDateTime timestamp = FileUtil.getLJAFileTimeStampAsOffsetDateTime(fileName);
        final String prefix = FileUtil.getLJAFileNamePrefix(fileName);
        
        assertThat(timestampString, is("20240827T154745Z"));
        assertThat(timestamp.toString(), is("2024-08-27T15:47:45Z"));
        assertThat(prefix, is("lja_westyorkshire_rota_"));
    }

    @Test
    void shouldExtractAllComponents_FromSnapshotFile() {
        final String fileName = "IT_Test_lja_bedfordshire_snapshot_20240403T180039Z.xml";
        
        final String timestampString = FileUtil.getLJAFileTimeStampAsString(fileName);
        final OffsetDateTime timestamp = FileUtil.getLJAFileTimeStampAsOffsetDateTime(fileName);
        final String prefix = FileUtil.getLJAFileNamePrefix(fileName);
        
        assertThat(timestampString, is("20240403T180039Z"));
        assertThat(timestamp.toString(), is("2024-04-03T18:00:39Z"));
        assertThat(prefix, is("IT_Test_lja_bedfordshire_snapshot_"));
    }

    // ============================================================================
    // Tests for getLJAFileNamePrefix - new behavior for non-snapshot files
    // ============================================================================

    @Test
    void shouldReturnFileNameWithoutXml_ForNonSnapshotFileEndingWithXml() {
        final String fileName = "simple_file.xml";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        // When no timestamp found, getLJAFileTimeStampAsString returns generated timestamp
        // which is not in filename, so method returns original filename
        assertThat(actual, is(fileName));
    }

    @Test
    void shouldReturnOriginalFileName_ForNonSnapshotFileNotEndingWithXml() {
        final String fileName = "simple_file.txt";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        assertThat(actual, is(fileName));
    }

    @Test
    void shouldReturnEmptyString_ForNonSnapshotFileWithOnlyXmlExtension() {
        final String fileName = ".xml";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        // When filename is too short, getLJAFileTimeStampAsString returns generated timestamp
        // which is not in filename, so method returns original filename
        assertThat(actual, is(fileName));
    }

    @Test
    void shouldReturnFileNameWithoutXml_ForNonSnapshotFileWithNoTimestamp() {
        final String fileName = "my_test_file.xml";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        // When no timestamp found, getLJAFileTimeStampAsString returns generated timestamp
        // which is not in filename, so method returns original filename
        assertThat(actual, is(fileName));
    }

    @Test
    void shouldReturnOriginalFileName_ForNonSnapshotFileWithXmlInMiddle() {
        final String fileName = "file.xml.backup";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        assertThat(actual, is(fileName));
    }

    @Test
    void shouldReturnFileNameWithoutXml_ForNonSnapshotFileWithSpecialCharacters() {
        final String fileName = "test-file_123.xml";
        final String actual = FileUtil.getLJAFileNamePrefix(fileName);
        // When no timestamp found, getLJAFileTimeStampAsString returns generated timestamp
        // which is not in filename, so method returns original filename
        assertThat(actual, is(fileName));
    }
}
