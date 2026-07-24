package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for building judiciary assignment requests.
 */
@Service
public class JudiciaryAssignmentRequestHelper {

    private static final Logger logger = LoggerFactory.getLogger(JudiciaryAssignmentRequestHelper.class);

    /**
     * Converts a list of JudiciaryScheduleAssignment into an AssignJudiciariesRequest.
     *
     * @param assignmentList list of JudiciaryScheduleAssignment containing judiciary IDs and schedule data
     * @return AssignJudiciariesRequest containing the judiciary assignments
     */
    public AssignJudiciariesRequest buildAssignJudiciariesRequest(
            final List<JudiciaryScheduleAssignment> assignmentList) {
        if (assignmentList == null || assignmentList.isEmpty()) {
            logger.debug("Building AssignJudiciariesRequest from empty list");
            return AssignJudiciariesRequest.builder()
                    .withJudiciaries(List.of())
                    .withSkipValidations(true)
                    .build();
        }

        logger.info("Building AssignJudiciariesRequest from list with {} entries", assignmentList.size());

        final List<JudiciaryAssignment> assignments = assignmentList.stream()
                .map(assignment -> buildJudiciaryAssignment(assignment.judiciaryId(), assignment.scheduleData()))
                .toList();

        final int totalSessionIds = assignments.stream()
                .mapToInt(assignment -> assignment.getSessionIds().size())
                .sum();

        logger.info("Built AssignJudiciariesRequest with {} judiciary assignments and {} total session IDs", 
                assignments.size(), totalSessionIds);

        return AssignJudiciariesRequest.builder()
                .withJudiciaries(assignments)
                .withSkipValidations(true)
                .build();
    }

    /**
     * Builds a JudiciaryAssignment from judiciary ID and court schedule data.
     *
     * @param judiciaryId the judiciary ID
     * @param data the court schedule data
     * @return JudiciaryAssignment object
     */
    private JudiciaryAssignment buildJudiciaryAssignment(final String judiciaryId, final JudiciaryCourtScheduleData data) {
        final List<String> sessionIds = data.courtScheduleIds().stream()
                .map(UUID::toString)
                .toList();
        return JudiciaryAssignment.builder()
                .withJudiciaryId(judiciaryId)
                .withRotaJudiciaryId(data.rotaJudiciaryId())
                .withSessionIds(sessionIds)
                .withPosition(data.position())
                .withIsBenchChairman(data.isBenchChairman())
                .withIsDeputy(data.isDeputy())
                .build();
    }
}

