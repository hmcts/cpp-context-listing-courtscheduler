package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.DayOfWeek;
import java.util.Set;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class Session {

    private String courtCentreId;
    private String courtRoomId;
    private String sessionType;
    private String businessType;
    private Integer slotsOrDuration;
    private String panel;
    private Set<DayOfWeek> repeatDays;


    public String getCourtCentreId() {
        return courtCentreId;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public String getSessionType() {
        return sessionType;
    }

    public String getBusinessType() {
        return businessType;
    }

    public Integer getSlotsOrDuration() {
        return slotsOrDuration;
    }

    public String getPanelType() {
        return panel;
    }

    public Set<DayOfWeek> getRepeatDays() {
        return repeatDays;
    }


    public static final class SessionBuilder {
        private String courtCentreId;
        private String courtRoomId;
        private String sessionType;
        private String businessType;
        private Integer slotsOrDuration;
        private String panelType;
        private Set<DayOfWeek> repeatDays;

        private SessionBuilder() {
        }

        public static SessionBuilder session() {
            return new SessionBuilder();
        }

        public SessionBuilder withCourtCentreId(String courtCentreId) {
            this.courtCentreId = courtCentreId;
            return this;
        }

        public SessionBuilder withCourtRoomId(String courtRoomId) {
            this.courtRoomId = courtRoomId;
            return this;
        }

        public SessionBuilder withSessionType(String sessionType) {
            this.sessionType = sessionType;
            return this;
        }

        public SessionBuilder withBusinessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        public SessionBuilder withSlotsOrDuration(Integer slotsOrDuration) {
            this.slotsOrDuration = slotsOrDuration;
            return this;
        }

        public SessionBuilder withPanelType(String panelType) {
            this.panelType = panelType;
            return this;
        }

        public SessionBuilder withRepeatDays(Set<DayOfWeek> repeatDays) {
            this.repeatDays = repeatDays;
            return this;
        }

        public Session build() {
            Session session = new Session();
            session.slotsOrDuration = this.slotsOrDuration;
            session.sessionType = this.sessionType;
            session.businessType = this.businessType;
            session.repeatDays = this.repeatDays;
            session.courtCentreId = this.courtCentreId;
            session.courtRoomId = this.courtRoomId;
            session.panel = this.panelType;
            return session;
        }
    }
}
