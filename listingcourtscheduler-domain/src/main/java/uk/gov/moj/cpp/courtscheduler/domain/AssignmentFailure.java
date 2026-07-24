package uk.gov.moj.cpp.courtscheduler.domain;

public class AssignmentFailure {

    private String judiciaryId;
    private String sessionId;
    private AssignmentFailureReason reason;

    public AssignmentFailure() {
        // default constructor
    }

    public AssignmentFailure(final String judiciaryId,
                             final String sessionId,
                             final AssignmentFailureReason reason) {
        this.judiciaryId = judiciaryId;
        this.sessionId = sessionId;
        this.reason = reason;
    }

    public String getJudiciaryId() {
        return judiciaryId;
    }

    public void setJudiciaryId(final String judiciaryId) {
        this.judiciaryId = judiciaryId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public AssignmentFailureReason getReason() {
        return reason;
    }

    public void setReason(final AssignmentFailureReason reason) {
        this.reason = reason;
    }

    public static AssignmentFailureBuilder builder() {
        return new AssignmentFailureBuilder();
    }

    public static final class AssignmentFailureBuilder {
        private String judiciaryId;
        private String sessionId;
        private AssignmentFailureReason reason;

        private AssignmentFailureBuilder() {
        }

        public AssignmentFailureBuilder withJudiciaryId(final String judiciaryId) {
            this.judiciaryId = judiciaryId;
            return this;
        }

        public AssignmentFailureBuilder withSessionId(final String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public AssignmentFailureBuilder withReason(final AssignmentFailureReason reason) {
            this.reason = reason;
            return this;
        }

        public AssignmentFailure build() {
            return new AssignmentFailure(judiciaryId, sessionId, reason);
        }
    }
}

