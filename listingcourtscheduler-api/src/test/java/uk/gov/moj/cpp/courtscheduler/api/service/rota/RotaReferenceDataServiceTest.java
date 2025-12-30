package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.ROTA_PROCESSING_ERROR;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaReferenceDataServiceTest {

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @Mock
    private Requester requester;

    @InjectMocks
    private RotaReferenceDataService rotaReferenceDataService;

    private String executionId;
    private String email;
    private Judiciary judiciary;
    private Venue venue;
    private CourtRoom courtRoom;
    private Map<String, String> exceptionMessages;

    @BeforeEach
    void setUp() {
        executionId = "execution-123";
        email = "judge@example.com";
        exceptionMessages = new HashMap<>();

        judiciary = Judiciary.JudiciaryBuilder.aJudiciary()
                .withId(randomUUID().toString())
                .withEmailAddress(email)
                .withForenames("John")
                .withSurname("Doe")
                .withTitlePrefix("Mr")
                .withJudiciaryType("Judge")
                .build();

        venue = new Venue(100, 200, "Test Venue");

        courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId("courtroom-1")
                .withOucode("OU001")
                .withRotaLocationId(100)
                .withRotaVenueId(200)
                .withRotaVenueName("Test Venue")
                .build();
    }

    // ============================================================================
    // Tests for validateAndFindJudiciaryByEmail
    // ============================================================================

    @Test
    void shouldReturnJudiciary_WhenFoundByEmail() {
        // given
        when(referenceDataMapperService.findByEmail(requester, email)).thenReturn(Optional.of(judiciary));

        // when
        Optional<Judiciary> result = rotaReferenceDataService.validateAndFindJudiciaryByEmail(requester, email, executionId);

        // then
        assertTrue(result.isPresent());
        assertThat(result.get().getEmailAddress(), is(email));
        verify(referenceDataMapperService).findByEmail(requester, email);
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldReturnEmpty_WhenEmailIsEmpty() {
        // when
        Optional<Judiciary> result = rotaReferenceDataService.validateAndFindJudiciaryByEmail(requester, "", executionId);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService, never()).findByEmail(any(), anyString());
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldReturnEmpty_WhenEmailIsNull() {
        // when
        Optional<Judiciary> result = rotaReferenceDataService.validateAndFindJudiciaryByEmail(requester, null, executionId);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService, never()).findByEmail(any(), anyString());
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldReturnEmptyWithoutLogging_WhenJudiciaryNotFound() {
        // given
        when(referenceDataMapperService.findByEmail(requester, email)).thenReturn(Optional.empty());

        // when
        Optional<Judiciary> result = rotaReferenceDataService.validateAndFindJudiciaryByEmail(requester, email, executionId);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService).findByEmail(requester, email);
        // Should not log here - will be aggregated and logged by caller
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogError_WhenJudiciaryNotFoundAndExecutionIdIsNull() {
        // given
        when(referenceDataMapperService.findByEmail(requester, email)).thenReturn(Optional.empty());

        // when
        Optional<Judiciary> result = rotaReferenceDataService.validateAndFindJudiciaryByEmail(requester, email, null);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService).findByEmail(requester, email);
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldReturnEmptyAndLogError_WhenExceptionOccurs() {
        // given
        RuntimeException exception = new RuntimeException("Database error");
        when(referenceDataMapperService.findByEmail(requester, email)).thenThrow(exception);

        // when
        Optional<Judiciary> result = rotaReferenceDataService.validateAndFindJudiciaryByEmail(requester, email, executionId);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService).findByEmail(requester, email);

        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> logCaptor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(ROTA_PROCESSING_ERROR.code()));
    }

    // ============================================================================
    // Tests for validateAndFindVenue
    // ============================================================================

    @Test
    void shouldReturnCourtRoom_WhenVenueFound() {
        // given
        when(referenceDataMapperService.findByVenue(eq(venue), anyMap(), eq(requester)))
                .thenReturn(Optional.of(courtRoom));

        // when
        Optional<CourtRoom> result = rotaReferenceDataService.validateAndFindVenue(
                venue, exceptionMessages, requester, executionId);

        // then
        assertTrue(result.isPresent());
        assertThat(result.get().getCourtroomId(), is("courtroom-1"));
        verify(referenceDataMapperService).findByVenue(eq(venue), anyMap(), eq(requester));
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldPopulateMapWhenVenueIsNull() {
        // when
        Optional<CourtRoom> result = rotaReferenceDataService.validateAndFindVenue(
                null, exceptionMessages, requester, executionId);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService, never()).findByVenue(any(), anyMap(), any());
        // Should populate map instead of logging directly
        assertThat(exceptionMessages.isEmpty(), is(false));
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldLogDirectlyWhenVenueIsNullAndNoMapProvided() {
        // when
        Optional<CourtRoom> result = rotaReferenceDataService.validateAndFindVenue(
                null, null, requester, executionId);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService, never()).findByVenue(any(), anyMap(), any());

        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> logCaptor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(REF_DATA_VENUE_NOT_FOUND.code()));
    }

    @Test
    void shouldNotLogError_WhenVenueIsNullAndExecutionIdIsNull() {
        // when
        Optional<CourtRoom> result = rotaReferenceDataService.validateAndFindVenue(
                null, exceptionMessages, requester, null);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService, never()).findByVenue(any(), anyMap(), any());
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldPopulateMapWhenVenueNotFound() {
        // given
        when(referenceDataMapperService.findByVenue(eq(venue), anyMap(), eq(requester)))
                .thenReturn(Optional.empty());

        // when
        Optional<CourtRoom> result = rotaReferenceDataService.validateAndFindVenue(
                venue, exceptionMessages, requester, executionId);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService).findByVenue(eq(venue), anyMap(), eq(requester));
        // Should populate map instead of logging directly
        assertThat(exceptionMessages.isEmpty(), is(false));
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldLogDirectlyWhenVenueNotFoundAndNoMapProvided() {
        // given
        when(referenceDataMapperService.findByVenue(eq(venue), anyMap(), eq(requester)))
                .thenReturn(Optional.empty());

        // when
        Optional<CourtRoom> result = rotaReferenceDataService.validateAndFindVenue(
                venue, null, requester, executionId);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService).findByVenue(eq(venue), anyMap(), eq(requester));

        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> logCaptor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(REF_DATA_VENUE_NOT_FOUND.code()));
    }

    @Test
    void shouldReturnEmptyAndLogError_WhenExceptionOccursDuringVenueValidation() {
        // given
        RuntimeException exception = new RuntimeException("Database error");
        when(referenceDataMapperService.findByVenue(eq(venue), anyMap(), eq(requester)))
                .thenThrow(exception);

        // when
        Optional<CourtRoom> result = rotaReferenceDataService.validateAndFindVenue(
                venue, exceptionMessages, requester, executionId);

        // then
        assertFalse(result.isPresent());
        verify(referenceDataMapperService).findByVenue(eq(venue), anyMap(), eq(requester));

        ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> logCaptor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(ROTA_PROCESSING_ERROR.code()));
    }

}