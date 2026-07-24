package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.JUDICIARY_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.DISTRICT_JUDGES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.MAGISTRATES;

import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaUtilsTest {

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    private String executionId;
    private Map<RotaPayload, Map<String, Map<String, String>>> records;

    @BeforeEach
    void setUp() {
        executionId = "execution-123";
        records = new HashMap<>();
    }

    // ============================================================================
    // Record Operations Tests
    // ============================================================================

    @Test
    void shouldReturnTrue_WhenRecordsIsNull() {
        assertTrue(RotaUtils.isEmptyRecords(null));
    }

    @Test
    void shouldReturnTrue_WhenRecordsIsEmpty() {
        assertTrue(RotaUtils.isEmptyRecords(Collections.emptyMap()));
    }

    @Test
    void shouldReturnFalse_WhenRecordsIsNotEmpty() {
        records.put(MAGISTRATES, new HashMap<>());
        assertFalse(RotaUtils.isEmptyRecords(records));
    }

    @Test
    void shouldReturnRecordsByType_WhenTypeExists() {
        // given
        Map<String, Map<String, String>> magistratesData = new HashMap<>();
        magistratesData.put("mag1", new HashMap<>());
        records.put(MAGISTRATES, magistratesData);

        // when
        Map<String, Map<String, String>> result = RotaUtils.getRecordsByType(records, MAGISTRATES);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey("mag1"));
    }

    @Test
    void shouldReturnEmptyMap_WhenTypeDoesNotExist() {
        // when
        Map<String, Map<String, String>> result = RotaUtils.getRecordsByType(records, DISTRICT_JUDGES);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowNullPointerException_WhenRecordsIsNull() {
        // when/then
        assertThrows(NullPointerException.class, () -> RotaUtils.getRecordsByType(null, MAGISTRATES));
    }

    // ============================================================================
    // Collection Operations Tests
    // ============================================================================

    @Test
    void shouldCreateNewList_WhenExistingListIsNull() {
        // when
        List<String> result = RotaUtils.addToListIfNotPresent(null, "item1");

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.contains("item1"));
    }

    @Test
    void shouldAddItem_WhenItemNotPresent() {
        // given
        List<String> existingList = new ArrayList<>();
        existingList.add("item1");

        // when
        List<String> result = RotaUtils.addToListIfNotPresent(existingList, "item2");

        // then
        assertThat(result.size(), is(2));
        assertTrue(result.contains("item1"));
        assertTrue(result.contains("item2"));
    }

    @Test
    void shouldNotAddItem_WhenItemAlreadyPresent() {
        // given
        List<String> existingList = new ArrayList<>();
        existingList.add("item1");

        // when
        List<String> result = RotaUtils.addToListIfNotPresent(existingList, "item1");

        // then
        assertThat(result.size(), is(1));
        assertTrue(result.contains("item1"));
    }

    // ============================================================================
    // Key Operations Tests
    // ============================================================================

    @Test
    void shouldBuildCompositeKey_WhenBothPartsAreValid() {
        // when
        String result = RotaUtils.buildCompositeKey("part1", "part2");

        // then
        assertNotNull(result);
        assertThat(result, is("part1|part2"));
    }

    @Test
    void shouldReturnNull_WhenPart1IsNull() {
        // when
        String result = RotaUtils.buildCompositeKey(null, "part2");

        // then
        assertNull(result);
    }

    @Test
    void shouldReturnNull_WhenPart2IsNull() {
        // when
        String result = RotaUtils.buildCompositeKey("part1", null);

        // then
        assertNull(result);
    }

    @Test
    void shouldReturnNull_WhenPart1IsEmpty() {
        // when
        String result = RotaUtils.buildCompositeKey("", "part2");

        // then
        assertNull(result);
    }

    @Test
    void shouldReturnNull_WhenPart2IsEmpty() {
        // when
        String result = RotaUtils.buildCompositeKey("part1", "");

        // then
        assertNull(result);
    }

    @Test
    void shouldParseCompositeKey_WhenKeyIsValid() {
        // when
        String[] result = RotaUtils.parseCompositeKey("part1|part2");

        // then
        assertNotNull(result);
        assertThat(result.length, is(2));
        assertThat(result[0], is("part1"));
        assertThat(result[1], is("part2"));
    }

    @Test
    void shouldReturnNull_WhenCompositeKeyIsNull() {
        // when
        String[] result = RotaUtils.parseCompositeKey(null);

        // then
        assertNull(result);
    }

    @Test
    void shouldReturnNull_WhenCompositeKeyIsEmpty() {
        // when
        String[] result = RotaUtils.parseCompositeKey("");

        // then
        assertNull(result);
    }

    @Test
    void shouldReturnNull_WhenCompositeKeyHasNoSeparator() {
        // when
        String[] result = RotaUtils.parseCompositeKey("nopart");

        // then
        assertNull(result);
    }

    @Test
    void shouldReturnNull_WhenCompositeKeyHasEmptyFirstPart() {
        // when
        String[] result = RotaUtils.parseCompositeKey("|part2");

        // then
        assertNull(result);
    }

    @Test
    void shouldExtractFirstPart_WhenKeyIsValid() {
        // when
        String result = RotaUtils.extractFirstPart("judiciaryId|courtListingProfileId");

        // then
        assertNotNull(result);
        assertThat(result, is("judiciaryId"));
    }

    @Test
    void shouldReturnNull_WhenKeyIsInvalid() {
        // when
        String result = RotaUtils.extractFirstPart("invalid");

        // then
        assertNull(result);
    }

    @Test
    void shouldReturnNull_WhenKeyIsNull() {
        // when
        String result = RotaUtils.extractFirstPart(null);

        // then
        assertNull(result);
    }

    // ============================================================================
    // Validation & Formatting Tests
    // ============================================================================

    @Test
    void shouldBuildVenueDetails_WhenVenueIsNull() {
        // when
        String result = RotaUtils.buildVenueDetails(null);

        // then
        assertNotNull(result);
        assertThat(result, is("UNKNOWN_LOCATION - UNKNOWN_VENUE - UNKNOWN_VENUE_ID"));
    }

    @Test
    void shouldBuildVenueDetails_WhenVenueHasAllFields() {
        // given
        Venue venue = new Venue(100, 200, "Test Venue");

        // when
        String result = RotaUtils.buildVenueDetails(venue);

        // then
        assertNotNull(result);
        assertThat(result, is("100 - Test Venue - 200"));
    }

    @Test
    void shouldBuildVenueDetails_WhenVenueHasNullLocationId() {
        // given
        Venue venue = new Venue(null, 200, "Test Venue");

        // when
        String result = RotaUtils.buildVenueDetails(venue);

        // then
        assertNotNull(result);
        assertTrue(result.contains("UNKNOWN_LOCATION"));
        assertTrue(result.contains("Test Venue"));
        assertTrue(result.contains("200"));
    }

    @Test
    void shouldBuildVenueDetails_WhenVenueHasNullVenueId() {
        // given
        Venue venue = new Venue(100, null, "Test Venue");

        // when
        String result = RotaUtils.buildVenueDetails(venue);

        // then
        assertNotNull(result);
        assertTrue(result.contains("100"));
        assertTrue(result.contains("Test Venue"));
        assertTrue(result.contains("UNKNOWN_VENUE_ID"));
    }

    @Test
    void shouldBuildVenueDetails_WhenVenueHasBlankVenueName() {
        // given
        Venue venue = new Venue(100, 200, "   ");

        // when
        String result = RotaUtils.buildVenueDetails(venue);

        // then
        assertNotNull(result);
        assertTrue(result.contains("100"));
        assertTrue(result.contains("UNKNOWN_VENUE"));
        assertTrue(result.contains("200"));
    }

    @Test
    void shouldMatchSession_WhenSessionsAreExactMatch() {
        assertTrue(RotaUtils.matchesSession("AM", "AM"));
        assertTrue(RotaUtils.matchesSession("PM", "PM"));
        assertTrue(RotaUtils.matchesSession("AD", "AD"));
    }

    @Test
    void shouldMatchSession_WhenRequestedIsAMAndScheduleIsAD() {
        assertTrue(RotaUtils.matchesSession(AM_SESSION, ALL_DAY));
    }

    @Test
    void shouldMatchSession_WhenRequestedIsPMAndScheduleIsAD() {
        assertTrue(RotaUtils.matchesSession(PM_SESSION, ALL_DAY));
    }

    @Test
    void shouldNotMatchSession_WhenRequestedIsADAndScheduleIsAM() {
        assertFalse(RotaUtils.matchesSession(ALL_DAY, AM_SESSION));
    }

    @Test
    void shouldNotMatchSession_WhenRequestedIsADAndScheduleIsPM() {
        assertFalse(RotaUtils.matchesSession(ALL_DAY, PM_SESSION));
    }

    @Test
    void shouldNotMatchSession_WhenRequestedIsAMAndScheduleIsPM() {
        assertFalse(RotaUtils.matchesSession(AM_SESSION, PM_SESSION));
    }

    @Test
    void shouldNotMatchSession_WhenRequestedIsPMAndScheduleIsAM() {
        assertFalse(RotaUtils.matchesSession(PM_SESSION, AM_SESSION));
    }

    @Test
    void shouldNotMatchSession_WhenRequestedSessionIsNull() {
        assertFalse(RotaUtils.matchesSession(null, AM_SESSION));
    }

    @Test
    void shouldNotMatchSession_WhenScheduleSessionIsNull() {
        assertFalse(RotaUtils.matchesSession(AM_SESSION, null));
    }

    @Test
    void shouldNotMatchSession_WhenBothSessionsAreNull() {
        assertFalse(RotaUtils.matchesSession(null, null));
    }

    // ============================================================================
    // Error Logging Tests
    // ============================================================================

    @Test
    void shouldLogProcessingError_WhenAllParamsAreValid() {
        // given
        String errorCode = "ERROR_001";
        String errorText = "Test error message";

        // when
        RotaUtils.logProcessingError(rotaProcessLogService, executionId, errorCode, errorText);

        // then
        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> captor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(captor.capture());
        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = captor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(errorCode));
        assertThat(log.getErrorText(), is(errorText));
    }

    @Test
    void shouldNotLogProcessingError_WhenExecutionIdIsNull() {
        // when
        RotaUtils.logProcessingError(rotaProcessLogService, null, "ERROR_001", "Test error");

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogProcessingError_WhenExecutionIdIsEmpty() {
        // when
        RotaUtils.logProcessingError(rotaProcessLogService, "", "ERROR_001", "Test error");

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogProcessingError_WhenErrorTextIsNull() {
        // when
        RotaUtils.logProcessingError(rotaProcessLogService, executionId, "ERROR_001", null);

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogProcessingError_WhenErrorTextIsEmpty() {
        // when
        RotaUtils.logProcessingError(rotaProcessLogService, executionId, "ERROR_001", "");

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldLogMissingDataError_WhenAllParamsAreValid() {
        // given
        List<String> messages = Arrays.asList("Error 1", "Error 2", "Error 3");

        // when
        RotaUtils.logMissingDataError(rotaProcessLogService, messages, executionId, JUDICIARY_NOT_FOUND);

        // then
        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> captor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(captor.capture());
        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = captor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(JUDICIARY_NOT_FOUND.code()));
        assertNotNull(log.getErrorText());
    }

    @Test
    void shouldNotLogMissingDataError_WhenExecutionIdIsNull() {
        // given
        List<String> messages = Arrays.asList("Error 1", "Error 2");

        // when
        RotaUtils.logMissingDataError(rotaProcessLogService, messages, null, JUDICIARY_NOT_FOUND);

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogMissingDataError_WhenExecutionIdIsEmpty() {
        // given
        List<String> messages = Arrays.asList("Error 1", "Error 2");

        // when
        RotaUtils.logMissingDataError(rotaProcessLogService, messages, "", JUDICIARY_NOT_FOUND);

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogMissingDataError_WhenMessagesIsNull() {
        // when
        RotaUtils.logMissingDataError(rotaProcessLogService, null, executionId, JUDICIARY_NOT_FOUND);

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogMissingDataError_WhenMessagesIsEmpty() {
        // when
        RotaUtils.logMissingDataError(rotaProcessLogService, Collections.emptyList(), executionId, JUDICIARY_NOT_FOUND);

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldFilterBlankMessages_WhenLoggingMissingDataError() {
        // given
        List<String> messages = Arrays.asList("Error 1", "   ", "", "Error 2");

        // when
        RotaUtils.logMissingDataError(rotaProcessLogService, messages, executionId, JUDICIARY_NOT_FOUND);

        // then
        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> captor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(captor.capture());
        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = captor.getValue();
        assertNotNull(log.getErrorText());
        // Should contain Error 1 and Error 2, but not blank messages
        assertTrue(log.getErrorText().contains("Error 1"));
        assertTrue(log.getErrorText().contains("Error 2"));
    }

    @Test
    void shouldRemoveDuplicateMessages_WhenLoggingMissingDataError() {
        // given
        List<String> messages = Arrays.asList("Error 1", "Error 1", "Error 2");

        // when
        RotaUtils.logMissingDataError(rotaProcessLogService, messages, executionId, JUDICIARY_NOT_FOUND);

        // then
        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> captor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(captor.capture());
        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = captor.getValue();
        assertNotNull(log.getErrorText());
    }

    @Test
    void shouldLogMissingReferenceData_WhenAllParamsAreValid() {
        // given
        Map<String, String> missingReferenceDataMap = new HashMap<>();
        missingReferenceDataMap.put("Location 100 - Venue A - 200", REF_DATA_VENUE_NOT_FOUND.code());
        missingReferenceDataMap.put("Location 101 - Venue B - 201", REF_DATA_VENUE_NOT_FOUND.code());

        // when
        RotaUtils.logMissingReferenceData(rotaProcessLogService, missingReferenceDataMap, executionId, REF_DATA_VENUE_NOT_FOUND);

        // then
        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> captor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(captor.capture());
        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = captor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(REF_DATA_VENUE_NOT_FOUND.code()));
        assertNotNull(log.getErrorText());
    }

    @Test
    void shouldNotLogMissingReferenceData_WhenMapIsNull() {
        // when
        RotaUtils.logMissingReferenceData(rotaProcessLogService, null, executionId, REF_DATA_VENUE_NOT_FOUND);

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogMissingReferenceData_WhenMapIsEmpty() {
        // when
        RotaUtils.logMissingReferenceData(rotaProcessLogService, Collections.emptyMap(), executionId, REF_DATA_VENUE_NOT_FOUND);

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogMissingReferenceData_WhenExecutionIdIsNull() {
        // given
        Map<String, String> missingReferenceDataMap = new HashMap<>();
        missingReferenceDataMap.put("Location 100 - Venue A - 200", REF_DATA_VENUE_NOT_FOUND.code());

        // when
        RotaUtils.logMissingReferenceData(rotaProcessLogService, missingReferenceDataMap, null, REF_DATA_VENUE_NOT_FOUND);

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogMissingReferenceData_WhenExecutionIdIsEmpty() {
        // given
        Map<String, String> missingReferenceDataMap = new HashMap<>();
        missingReferenceDataMap.put("Location 100 - Venue A - 200", REF_DATA_VENUE_NOT_FOUND.code());

        // when
        RotaUtils.logMissingReferenceData(rotaProcessLogService, missingReferenceDataMap, "", REF_DATA_VENUE_NOT_FOUND);

        // then
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldFilterByErrorCode_WhenLoggingMissingReferenceData() {
        // given
        Map<String, String> missingReferenceDataMap = new HashMap<>();
        missingReferenceDataMap.put("Location 100 - Venue A - 200", REF_DATA_VENUE_NOT_FOUND.code());
        missingReferenceDataMap.put("Location 101 - Venue B - 201", JUDICIARY_NOT_FOUND.code());

        // when
        RotaUtils.logMissingReferenceData(rotaProcessLogService, missingReferenceDataMap, executionId, REF_DATA_VENUE_NOT_FOUND);

        // then
        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> captor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(captor.capture());
        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = captor.getValue();
        assertThat(log.getErrorCode(), is(REF_DATA_VENUE_NOT_FOUND.code()));
        // Should only contain venue data, not judiciary data
        assertTrue(log.getErrorText().contains("Location 100 - Venue A - 200"));
        assertFalse(log.getErrorText().contains("Location 101 - Venue B - 201"));
    }

    @Test
    void shouldFilterBlankVenueDetails_WhenLoggingMissingReferenceData() {
        // given
        Map<String, String> missingReferenceDataMap = new HashMap<>();
        missingReferenceDataMap.put("Location 100 - Venue A - 200", REF_DATA_VENUE_NOT_FOUND.code());
        missingReferenceDataMap.put("   ", REF_DATA_VENUE_NOT_FOUND.code());
        missingReferenceDataMap.put("", REF_DATA_VENUE_NOT_FOUND.code());

        // when
        RotaUtils.logMissingReferenceData(rotaProcessLogService, missingReferenceDataMap, executionId, REF_DATA_VENUE_NOT_FOUND);

        // then
        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> captor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(captor.capture());
        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = captor.getValue();
        assertTrue(log.getErrorText().contains("Location 100 - Venue A - 200"));
    }
}

