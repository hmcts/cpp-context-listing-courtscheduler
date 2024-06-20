package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;
import java.util.List;

public class CreateSessionRequestParam {

    private List<Session> sessionList;
    private RepeatPattern repeatPattern;


    public List<Session> getSessionList() {
        return sessionList;
    }

    public RepeatPattern getRepeatPattern() {
        return repeatPattern;
    }

    public CreateSessionRequestParam(final List<Session> sessionList, final RepeatPattern repeatPattern) {
        this.sessionList = sessionList;
        this.repeatPattern = repeatPattern;
    }




    public static final class CreateSessionRequestParamBuilder {
        private List<Session> sessionList;
        private RepeatPattern repeatPattern;

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

        public CreateSessionRequestParam build() {
            return new CreateSessionRequestParam(sessionList, repeatPattern);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final CreateSessionRequestParam that)) return false;
        return Objects.equals(sessionList, that.sessionList) && Objects.equals(repeatPattern, that.repeatPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSessionList(), getRepeatPattern());
    }


}
