package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AssignJudiciariesResponse {

    private int requestedAssignments;
    private int successfulAssignments;
    private List<AssignmentFailure> failures = new ArrayList<>();

    public AssignJudiciariesResponse() {
        // default constructor
    }

    public AssignJudiciariesResponse(final int requestedAssignments,
                                     final int successfulAssignments,
                                     final List<AssignmentFailure> failures) {
        this.requestedAssignments = requestedAssignments;
        this.successfulAssignments = successfulAssignments;
        this.failures = failures;
    }

    public int getRequestedAssignments() {
        return requestedAssignments;
    }

    public void setRequestedAssignments(final int requestedAssignments) {
        this.requestedAssignments = requestedAssignments;
    }

    public int getSuccessfulAssignments() {
        return successfulAssignments;
    }

    public void setSuccessfulAssignments(final int successfulAssignments) {
        this.successfulAssignments = successfulAssignments;
    }

    public List<AssignmentFailure> getFailures() {
        return failures;
    }

    public void setFailures(final List<AssignmentFailure> failures) {
        this.failures = failures;
    }

    public int getFailedAssignments() {
        return Math.max(0, requestedAssignments - successfulAssignments);
    }

    public static AssignJudiciariesResponseBuilder builder() {
        return new AssignJudiciariesResponseBuilder();
    }

    public static final class AssignJudiciariesResponseBuilder {
        private int requestedAssignments;
        private int successfulAssignments;
        private final List<AssignmentFailure> failures = new ArrayList<>();

        private AssignJudiciariesResponseBuilder() {
        }

        public AssignJudiciariesResponseBuilder withRequestedAssignments(final int requestedAssignments) {
            this.requestedAssignments = requestedAssignments;
            return this;
        }

        public AssignJudiciariesResponseBuilder withSuccessfulAssignments(final int successfulAssignments) {
            this.successfulAssignments = successfulAssignments;
            return this;
        }

        public AssignJudiciariesResponseBuilder withFailures(final List<AssignmentFailure> failures) {
            this.failures.clear();
            if (Objects.nonNull(failures)) {
                this.failures.addAll(failures);
            }
            return this;
        }

        public AssignJudiciariesResponseBuilder addFailure(final AssignmentFailure failure) {
            if (Objects.nonNull(failure)) {
                this.failures.add(failure);
            }
            return this;
        }

        public AssignJudiciariesResponse build() {
            return new AssignJudiciariesResponse(requestedAssignments, successfulAssignments, new ArrayList<>(failures));
        }
    }
}

