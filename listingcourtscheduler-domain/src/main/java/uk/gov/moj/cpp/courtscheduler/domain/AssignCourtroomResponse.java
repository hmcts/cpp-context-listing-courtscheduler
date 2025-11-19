package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class AssignCourtroomResponse {

    private List<CourtScheduleView> eligibleSessions = new ArrayList<>();
    private List<IneligibleSession> ineligibleSessions = new ArrayList<>();
    private List<FailedSession> failedSessions = new ArrayList<>();

    public AssignCourtroomResponse() {
    }

    public List<CourtScheduleView> getEligibleSessions() {
        return eligibleSessions;
    }

    public void setEligibleSessions(final List<CourtScheduleView> eligibleSessions) {
        this.eligibleSessions = eligibleSessions;
    }

    public List<IneligibleSession> getIneligibleSessions() {
        return ineligibleSessions;
    }

    public void setIneligibleSessions(final List<IneligibleSession> ineligibleSessions) {
        this.ineligibleSessions = ineligibleSessions;
    }

    public List<FailedSession> getFailedSessions() {
        return failedSessions;
    }

    public void setFailedSessions(final List<FailedSession> failedSessions) {
        this.failedSessions = failedSessions;
    }
}


