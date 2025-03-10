package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public class CreateSessionRequestParam {

    private List<Session> sessionList;
    private RepeatPattern repeatPattern;

    private Session sessionToBeAdded;

    public List<Session> getSessionList() {
        return sessionList;
    }

    public RepeatPattern getRepeatPattern() {
        return repeatPattern;
    }

    public Session getSessionToBeAdded() {
        return sessionToBeAdded;
    }

    public CreateSessionRequestParam(final List<Session> sessionList, final RepeatPattern repeatPattern, final Session sessionToBeAdded) {
        this.sessionList = sessionList;
        this.repeatPattern = repeatPattern;
        this.sessionToBeAdded = sessionToBeAdded;
    }

    public static final class CreateSessionRequestParamBuilder {
        private List<Session> sessionList;
        private RepeatPattern repeatPattern;

        private Session sessionToBeAdded;

        private CreateSessionRequestParamBuilder() {
        }

        public static CreateSessionRequestParamBuilder createSessionRequestParam() {
            return new CreateSessionRequestParamBuilder();
        }

        public CreateSessionRequestParamBuilder withSessionList(List<Session> sessionList) {
            this.sessionList = sessionList;
            return this;
        }

        public CreateSessionRequestParamBuilder withRepeatPattern(RepeatPattern repeatPattern) {
            this.repeatPattern = repeatPattern;
            return this;
        }

        public CreateSessionRequestParamBuilder withSessionToBeAdded(Session sessionToBeAdded) {
            this.sessionToBeAdded = sessionToBeAdded;
            return this;
        }
        
        public CreateSessionRequestParam build() {
            return new CreateSessionRequestParam(sessionList, repeatPattern, sessionToBeAdded);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final CreateSessionRequestParam that)) return false;
        return Objects.equals(sessionList, that.sessionList) && Objects.equals(repeatPattern, that.repeatPattern) && Objects.equals(sessionToBeAdded, that.sessionToBeAdded);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSessionList(), getRepeatPattern(), getSessionToBeAdded());
    }

    @Override
    public String toString() {
        return "CreateSessionRequestParam{" +
                "sessionList=" + sessionList +
                ", repeatPattern=" + repeatPattern +
                ", sessionToBeAdded=" + sessionToBeAdded +
                '}';
    }
}
