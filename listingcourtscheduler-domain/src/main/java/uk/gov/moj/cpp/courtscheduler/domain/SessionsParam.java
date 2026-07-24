package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public class SessionsParam {
    List<String> sessions;

    public List<String> getSessions() {
        return sessions;
    }

    public void setSessions(final List<String> sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SessionsParam that = (SessionsParam) o;
        return Objects.equals(sessions, that.sessions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessions);
    }
}
