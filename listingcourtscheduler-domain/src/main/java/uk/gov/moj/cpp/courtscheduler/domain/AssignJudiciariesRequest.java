package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AssignJudiciariesRequest {

    private List<JudiciaryAssignment> judiciaries = new ArrayList<>();
    private boolean skipValidations = false;

    public AssignJudiciariesRequest() {
        // default constructor
    }

    public AssignJudiciariesRequest(final List<JudiciaryAssignment> judiciaries) {
        this.judiciaries = judiciaries;
    }

    public List<JudiciaryAssignment> getJudiciaries() {
        return judiciaries;
    }

    public void setJudiciaries(final List<JudiciaryAssignment> judiciaries) {
        this.judiciaries = judiciaries;
    }

    public boolean isSkipValidations() {
        return skipValidations;
    }

    public void setSkipValidations(final boolean skipValidations) {
        this.skipValidations = skipValidations;
    }

    public static AssignJudiciariesRequestBuilder builder() {
        return new AssignJudiciariesRequestBuilder();
    }

    public static final class AssignJudiciariesRequestBuilder {
        private final List<JudiciaryAssignment> judiciaries = new ArrayList<>();
        private boolean skipValidations = false;

        private AssignJudiciariesRequestBuilder() {
        }

        public AssignJudiciariesRequestBuilder withJudiciaries(final List<JudiciaryAssignment> judiciaries) {
            if (Objects.nonNull(judiciaries)) {
                this.judiciaries.clear();
                this.judiciaries.addAll(judiciaries);
            }
            return this;
        }

        public AssignJudiciariesRequestBuilder addJudiciary(final JudiciaryAssignment assignment) {
            if (Objects.nonNull(assignment)) {
                this.judiciaries.add(assignment);
            }
            return this;
        }

        public AssignJudiciariesRequestBuilder withSkipValidations(final boolean skipValidations) {
            this.skipValidations = skipValidations;
            return this;
        }

        public AssignJudiciariesRequest build() {
            final AssignJudiciariesRequest request = new AssignJudiciariesRequest(new ArrayList<>(judiciaries));
            request.setSkipValidations(skipValidations);
            return request;
        }
    }
}

