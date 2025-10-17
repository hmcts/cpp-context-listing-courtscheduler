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
    private Boolean allDaySplit;
    private Integer maxDurationForMorning;
    private Integer maxDurationForAfternoon;
    private String sessionStartTime;
    private String sessionEndTime;
    private Boolean isOverbookingAllowed;
    private Boolean isDraft;
    private String jurisdiction;
    private Integer index;

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

    public Boolean isAllDaySplit() {
        return allDaySplit;
    }

    public Integer getMaxDurationForMorning() {
        return maxDurationForMorning;
    }

    public Integer getMaxDurationForAfternoon() {
        return maxDurationForAfternoon;
    }

    public String getSessionStartTime() {
        return sessionStartTime;
    }

    public String getSessionEndTime() {
        return sessionEndTime;
    }

    public Boolean isOverbookingAllowed() {
        return isOverbookingAllowed;
    }

    public Boolean isDraft() {
        return isDraft;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public Integer getIndex() {
        return index;
    }

    public static final class SessionBuilder {
        private String courtCentreId;
        private String courtRoomId;
        private String sessionType;
        private String businessType;
        private Integer slotsOrDuration;
        private String panelType;
        private Set<DayOfWeek> repeatDays;
        private Boolean allDaySplit;
        private Integer maxDurationForMorning;
        private Integer maxDurationForAfternoon;
        private String sessionStartTime;
        private String sessionEndTime;
        private Boolean isOverbookingAllowed;
        private Boolean isDraft;
        private String jurisdiction;
        private Integer index;

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

        public SessionBuilder withAllDaySplit(final Boolean allDaySplit) {
            this.allDaySplit = allDaySplit;
            return this;
        }

        public SessionBuilder withMaxDurationForMorning(final Integer maxDurationForMorning) {
            this.maxDurationForMorning = maxDurationForMorning;
            return this;
        }

        public SessionBuilder withMaxDurationForAfternoon(final Integer maxDurationForAfternoon) {
            this.maxDurationForAfternoon = maxDurationForAfternoon;
            return this;
        }

        public SessionBuilder withSessionStartTime(final String sessionStartTime) {
            this.sessionStartTime = sessionStartTime;
            return this;
        }

        public SessionBuilder withSessionEndTime(final String sessionEndTime) {
            this.sessionEndTime = sessionEndTime;
            return this;
        }

        public SessionBuilder withIsOverbookingAllowed(final Boolean isOverbookingAllowed) {
            this.isOverbookingAllowed = isOverbookingAllowed;
            return this;
        }

        public SessionBuilder withIsDraft(final Boolean isDraft) {
            this.isDraft = isDraft;
            return this;
        }

        public SessionBuilder withJurisdiction(final String jurisdiction) {
            this.jurisdiction = jurisdiction;
            return this;
        }

        public SessionBuilder withIndex(final Integer index) {
            this.index = index;
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
            session.allDaySplit = this.allDaySplit;
            session.maxDurationForMorning = this.maxDurationForMorning;
            session.maxDurationForAfternoon = this.maxDurationForAfternoon;
            session.sessionStartTime = this.sessionStartTime;
            session.sessionEndTime = this.sessionEndTime;
            session.isOverbookingAllowed = this.isOverbookingAllowed;
            session.isDraft = this.isDraft;
            session.jurisdiction = this.jurisdiction;
            session.index = this.index;
            return session;
        }
    }
}
