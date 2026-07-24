package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AssignJudiciaryToSessionsRequest {

    private List<String> courtScheduleIds = new ArrayList<>();
    private List<SessionJudiciary> judiciary = new ArrayList<>();

    public List<String> getCourtScheduleIds() {
        return courtScheduleIds;
    }

    public void setCourtScheduleIds(final List<String> courtScheduleIds) {
        this.courtScheduleIds = courtScheduleIds != null ? courtScheduleIds : new ArrayList<>();
    }

    public List<SessionJudiciary> getJudiciary() {
        return judiciary;
    }

    public void setJudiciary(final List<SessionJudiciary> judiciary) {
        this.judiciary = judiciary != null ? judiciary : new ArrayList<>();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<String> courtScheduleIds = new ArrayList<>();
        private final List<SessionJudiciary> judiciary = new ArrayList<>();

        public Builder withCourtScheduleIds(final List<String> courtScheduleIds) {
            this.courtScheduleIds.clear();
            if (courtScheduleIds != null) {
                this.courtScheduleIds.addAll(courtScheduleIds);
            }
            return this;
        }

        public Builder addCourtScheduleId(final String courtScheduleId) {
            if (courtScheduleId != null) {
                this.courtScheduleIds.add(courtScheduleId);
            }
            return this;
        }

        public Builder withJudiciary(final List<SessionJudiciary> judiciary) {
            this.judiciary.clear();
            if (judiciary != null) {
                this.judiciary.addAll(judiciary);
            }
            return this;
        }

        public Builder addSessionJudiciary(final SessionJudiciary sessionJudiciary) {
            if (sessionJudiciary != null) {
                this.judiciary.add(sessionJudiciary);
            }
            return this;
        }

        public AssignJudiciaryToSessionsRequest build() {
            final AssignJudiciaryToSessionsRequest r = new AssignJudiciaryToSessionsRequest();
            r.setCourtScheduleIds(new ArrayList<>(courtScheduleIds));
            r.setJudiciary(new ArrayList<>(judiciary));
            return r;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AssignJudiciaryToSessionsRequest that = (AssignJudiciaryToSessionsRequest) o;
        return Objects.equals(courtScheduleIds, that.courtScheduleIds)
                && Objects.equals(judiciary, that.judiciary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtScheduleIds, judiciary);
    }
}
