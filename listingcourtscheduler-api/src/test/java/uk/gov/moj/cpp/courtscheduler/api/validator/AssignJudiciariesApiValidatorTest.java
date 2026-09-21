package uk.gov.moj.cpp.courtscheduler.api.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Judiciary;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerAssignJudiciary;
import uk.gov.moj.cpp.courtscheduler.openapi.model.JudiciaryAssignment;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignJudiciariesApiValidatorTest {

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @InjectMocks
    private AssignJudiciariesApiValidator validator;

    @Test
    void shouldReturnEmptyWhenValid() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = UUID.randomUUID().toString();
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId(judiciaryId)
                .sessionIds(List.of(sessionId))
                .isDeputy(false)
                .isBenchChairman(true);
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment));

        final Judiciary judiciary = new Judiciary().id(judiciaryId);
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(sessionId);

        when(referenceDataMapperService.findById(eq(judiciaryId)))
                .thenReturn(Optional.of(judiciary));
        when(courtScheduleRepository.findByCourtScheduleIds(any()))
                .thenReturn(List.of(session));

        final JsonObject result = validator.validate(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorsWhenMissingData() {
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId("")
                .sessionIds(List.of("not-a-uuid"))
                .isDeputy(false)
                .isBenchChairman(true);
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment));

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("Judiciary id is mandatory"));
        assertTrue(message.contains("not-a-uuid"));
    }

    @Test
    void shouldRequireAssignments() {
        final JsonObject result = validator.validate(null);
        assertEquals("At least one judiciary assignment must be supplied", result.getString("errorMessage"));
    }

    @Test
    void shouldSkipValidationWhenSkipValidationsIsTrue() {
        // Even with invalid assignment (empty judiciaryId), validation should pass when skipValidations is true
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId("")
                .sessionIds(List.of("not-a-uuid"));
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment))
                .skipValidations(true);

        final JsonObject result = validator.validate(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPerformValidationWhenSkipValidationsIsFalse() {
        // With invalid assignment and skipValidations false, validation should fail
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId("")
                .sessionIds(List.of("not-a-uuid"))
                .isDeputy(false)
                .isBenchChairman(true);
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment))
                .skipValidations(false);

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("Judiciary id is mandatory"));
    }

    @Test
    void shouldPerformValidationWhenSkipValidationsIsNotSet() {
        // When skipValidations is not set, should default to false and perform validation
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId("")
                .sessionIds(List.of("not-a-uuid"))
                .isDeputy(false)
                .isBenchChairman(true);
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment));

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("Judiciary id is mandatory"));
    }

    @Test
    void shouldAcceptNullIsDeputy() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = UUID.randomUUID().toString();
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId(judiciaryId)
                .sessionIds(List.of(sessionId))
                .isBenchChairman(true);
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment));

        final Judiciary judiciary = new Judiciary().id(judiciaryId);
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(sessionId);

        when(referenceDataMapperService.findById(eq(judiciaryId)))
                .thenReturn(Optional.of(judiciary));
        when(courtScheduleRepository.findByCourtScheduleIds(any()))
                .thenReturn(List.of(session));

        final JsonObject result = validator.validate(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldAcceptNullIsBenchChairman() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = UUID.randomUUID().toString();
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId(judiciaryId)
                .sessionIds(List.of(sessionId))
                .isDeputy(false);
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment));

        final Judiciary judiciary = new Judiciary().id(judiciaryId);
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(sessionId);

        when(referenceDataMapperService.findById(eq(judiciaryId)))
                .thenReturn(Optional.of(judiciary));
        when(courtScheduleRepository.findByCourtScheduleIds(any()))
                .thenReturn(List.of(session));

        final JsonObject result = validator.validate(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldAcceptNullIsDeputyAndIsBenchChairman() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = UUID.randomUUID().toString();
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId(judiciaryId)
                .sessionIds(List.of(sessionId));
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment));

        final Judiciary judiciary = new Judiciary().id(judiciaryId);
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(sessionId);

        when(referenceDataMapperService.findById(eq(judiciaryId)))
                .thenReturn(Optional.of(judiciary));
        when(courtScheduleRepository.findByCourtScheduleIds(any()))
                .thenReturn(List.of(session));

        final JsonObject result = validator.validate(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenJudiciaryNotFound() {
        final String judiciaryId = "non-existent-judiciary";
        final String sessionId = UUID.randomUUID().toString();
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId(judiciaryId)
                .sessionIds(List.of(sessionId));
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment));

        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(sessionId);

        when(referenceDataMapperService.findById(eq(judiciaryId)))
                .thenReturn(Optional.empty());
        when(courtScheduleRepository.findByCourtScheduleIds(any()))
                .thenReturn(List.of(session));

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("Judiciary not found: " + judiciaryId));
    }

    @Test
    void shouldReturnErrorWhenSessionNotFound() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = UUID.randomUUID().toString();
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId(judiciaryId)
                .sessionIds(List.of(sessionId));
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment));

        final Judiciary judiciary = new Judiciary().id(judiciaryId);

        when(referenceDataMapperService.findById(eq(judiciaryId)))
                .thenReturn(Optional.of(judiciary));
        when(courtScheduleRepository.findByCourtScheduleIds(any()))
                .thenReturn(new ArrayList<>()); // Empty list means session not found

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("Session not found: " + sessionId));
    }

    @Test
    void shouldReturnErrorsForBothJudiciaryAndSessionNotFound() {
        final String judiciaryId = "non-existent-judiciary";
        final String sessionId = UUID.randomUUID().toString();
        final JudiciaryAssignment assignment = new JudiciaryAssignment()
                .judiciaryId(judiciaryId)
                .sessionIds(List.of(sessionId));
        final CourtschedulerAssignJudiciary request = new CourtschedulerAssignJudiciary()
                .judiciaries(List.of(assignment));

        when(referenceDataMapperService.findById(eq(judiciaryId)))
                .thenReturn(Optional.empty());
        when(courtScheduleRepository.findByCourtScheduleIds(any()))
                .thenReturn(new ArrayList<>()); // Empty list means session not found

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("Judiciary not found: " + judiciaryId));
        assertTrue(message.contains("Session not found: " + sessionId));
    }
}
