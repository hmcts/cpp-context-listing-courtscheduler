package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerAssignJudiciary;
import uk.gov.moj.cpp.courtscheduler.openapi.model.JudiciaryAssignment;

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
    public CourtschedulerAssignJudiciary buildAssignJudiciariesRequest(
            final List<JudiciaryScheduleAssignment> assignmentList) {
        if (assignmentList == null || assignmentList.isEmpty()) {
            logger.debug("Building AssignJudiciariesRequest from empty list");
            return new CourtschedulerAssignJudiciary()
                    .judiciaries(List.of())
                    .skipValidations(true);
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

        return new CourtschedulerAssignJudiciary()
                .judiciaries(assignments)
                .skipValidations(true);
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
        return new JudiciaryAssignment()
                .judiciaryId(judiciaryId)
                .rotaJudiciaryId(data.rotaJudiciaryId())
                .sessionIds(sessionIds)
                .position(data.position())
                .isBenchChairman(data.isBenchChairman())
                .isDeputy(data.isDeputy());
    }
}

