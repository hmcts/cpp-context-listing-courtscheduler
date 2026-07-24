package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class AssignCourtroomErrorGroup {

    private List<CourtScheduleView> sessions = new ArrayList<>();
    private String error;

    public AssignCourtroomErrorGroup() {
        // Intentionally empty - fields are initialized at declaration
    }

    public AssignCourtroomErrorGroup(final List<CourtScheduleView> sessions, final String error) {
        this.sessions = sessions != null ? sessions : new ArrayList<>();
        this.error = error;
    }

    public List<CourtScheduleView> getSessions() {
        return sessions;
    }

    public void setSessions(final List<CourtScheduleView> sessions) {
        this.sessions = sessions != null ? sessions : new ArrayList<>();
    }

    public String getError() {
        return error;
    }

    public void setError(final String error) {
        this.error = error;
    }
}



















