package uk.gov.moj.cpp.courtscheduler.domain;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class UpdateCourtSchedule {

    private String courtScheduleId;
    private String courtRoomId;
    private String sessionType;
    private String businessType;
    private String panel;

    private Integer availableSlots;
    private Integer availableDuration;

    private Integer maxSlots;
    private Integer maxDuration;
    private Integer maxDurationForMorning;
    private Integer maxDurationForAfternoon;
    private boolean allDaySplit;
    private String sessionStartTime;
    private String sessionEndTime;
    private boolean isOverbookingAllowed;
    private String jurisdiction;
    private Boolean isDraft;

    protected UpdateCourtSchedule(final UpdateCourtScheduleBuilder builder) {
        this.courtScheduleId = builder.courtScheduleId;
        this.courtRoomId = builder.courtRoomId;
        this.businessType = builder.businessType;
        this.sessionType = builder.sessionType;
        this.panel = builder.panel;
        this.availableSlots = builder.availableSlots;
        this.availableDuration = builder.availableDuration;
        this.maxSlots = builder.maxSlots;
        this.maxDuration = builder.maxDuration;
        this.maxDurationForMorning = builder.maxDurationForMorning;
        this.maxDurationForAfternoon = builder.maxDurationForAfternoon;
        this.allDaySplit = builder.allDaySplit;
        this.sessionStartTime = builder.sessionStartTime;
        this.sessionEndTime = builder.sessionEndTime;
        this.isOverbookingAllowed = builder.isOverbookingAllowed;
        this.jurisdiction = builder.jurisdiction;
        this.isDraft = builder.isDraft;
    }

    public UpdateCourtSchedule() {
    }


    public String getPanel() {
        return panel;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }


    public String getCourtRoomId() {
        return courtRoomId;
    }

    public String getBusinessType() {
        return businessType;
    }

    public String getSessionType() {
        return sessionType;
    }

    public Integer getMaxDuration() {
        return maxDuration;
    }

    public Integer getMaxSlots() {
        return maxSlots;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }


    public void setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
    }


    public void setBusinessType(final String businessType) {
        this.businessType = businessType;
    }

    public void setSessionType(final String sessionType) {
        this.sessionType = sessionType;
    }

    public void setPanel(final String panel) {
        this.panel = panel;
    }


    public Integer getAvailableSlots() {
        return availableSlots;
    }

    public Integer getAvailableDuration() {
        return availableDuration;
    }

    public void setAvailableSlots(final Integer availableSlots) {
        this.availableSlots = availableSlots;
    }

    public void setAvailableDuration(final Integer availableDuration) {
        this.availableDuration = availableDuration;
    }

    public UpdateCourtSchedule setMaxSlots(final Integer maxSlots) {
        this.maxSlots = maxSlots;
        return this;
    }

    public UpdateCourtSchedule setMaxDuration(final Integer maxDuration) {
        this.maxDuration = maxDuration;
        return this;
    }

    public UpdateCourtSchedule setMaxDurationForMorning(final Integer maxDurationForMorning) {
        this.maxDurationForMorning = maxDurationForMorning;
        return this;
    }

    public UpdateCourtSchedule setMaxDurationForAfternoon(final Integer maxDurationForAfternoon) {
        this.maxDurationForAfternoon = maxDurationForAfternoon;
        return this;
    }

    public Integer getMaxDurationForMorning() {
        return maxDurationForMorning;
    }

    public Integer getMaxDurationForAfternoon() {
        return maxDurationForAfternoon;
    }

    public boolean isAllDaySplit() {
        return allDaySplit;
    }

    public void setAllDaySplit(final boolean allDaySplit) {
        this.allDaySplit = allDaySplit;
    }

    public String getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(final String sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
    }

    public String getSessionEndTime() {
        return sessionEndTime;
    }

    public void setSessionEndTime(final String sessionEndTime) {
        this.sessionEndTime = sessionEndTime;
    }

    public boolean isOverbookingAllowed() {
        return isOverbookingAllowed;
    }

    public void setIsOverbookingAllowed(final boolean isOverbookingAllowed) {
        this.isOverbookingAllowed = isOverbookingAllowed;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(final String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public Boolean getIsDraft() {
        return isDraft;
    }

    public void setIsDraft(final Boolean isDraft) {
        this.isDraft = isDraft;
    }

    public static final class UpdateCourtScheduleBuilder {
        private String courtScheduleId;
        private String courtRoomId;
        private String sessionType;
        private String businessType;
        private String panel;

        private Integer availableSlots = 0;
        private Integer availableDuration = 0;
        private Integer maxSlots = 0;
        private Integer maxDuration = 0;
        private Integer maxDurationForMorning = 0;
        private Integer maxDurationForAfternoon = 0;
        private boolean allDaySplit = false;
        private String sessionStartTime;
        private String sessionEndTime;
        private boolean isOverbookingAllowed;
        private String jurisdiction;
        private Boolean isDraft;

        public static UpdateCourtSchedule.UpdateCourtScheduleBuilder courtSchedule() {
            return new UpdateCourtSchedule.UpdateCourtScheduleBuilder();
        }


        public UpdateCourtScheduleBuilder withCourtScheduleId(final String courtScheduleId) {
            this.courtScheduleId = courtScheduleId;
            return this;
        }

        public UpdateCourtScheduleBuilder withPanel(final String panel) {
            this.panel = panel;
            return this;
        }


        public UpdateCourtScheduleBuilder withCourtRoomId(final String courtRoomId) {
            this.courtRoomId = courtRoomId;
            return this;
        }


        public UpdateCourtScheduleBuilder withBusinessType(final String businessType) {
            this.businessType = businessType;
            return this;
        }

        public UpdateCourtScheduleBuilder withSessionType(final String sessionType) {
            this.sessionType = sessionType;
            return this;
        }

        public UpdateCourtScheduleBuilder withAvailableSlots(final Integer availableSlot) {
            this.availableSlots = availableSlot;
            return this;
        }

        public UpdateCourtScheduleBuilder withAvailableDuration(final Integer availableDuration) {
            this.availableDuration = availableDuration;
            return this;
        }

        public UpdateCourtScheduleBuilder withMaxSlots(final Integer maxSlots) {
            this.maxSlots = maxSlots;
            return this;
        }

        public UpdateCourtScheduleBuilder withMaxDuration(final Integer maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }

        public UpdateCourtScheduleBuilder withMaxDurationForMorning(final Integer maxDurationForMorning) {
            this.maxDurationForMorning = maxDurationForMorning;
            return this;
        }

        public UpdateCourtScheduleBuilder withMaxDurationForAfternoon(final Integer maxDurationForAfternoon) {
            this.maxDurationForAfternoon = maxDurationForAfternoon;
            return this;
        }

        public UpdateCourtScheduleBuilder withAllDaySplit(final boolean allDaySplit) {
            this.allDaySplit = allDaySplit;
            return this;
        }

        public UpdateCourtScheduleBuilder withSessionStartTime(final String sessionStartTime) {
            this.sessionStartTime = sessionStartTime;
            return this;
        }

        public UpdateCourtScheduleBuilder withSessionEndTime(final String sessionEndTime) {
            this.sessionEndTime = sessionEndTime;
            return this;
        }

        public UpdateCourtScheduleBuilder withIsOverbookingAllowed(final boolean isOverbookingAllowed) {
            this.isOverbookingAllowed = isOverbookingAllowed;
            return this;
        }

        public UpdateCourtScheduleBuilder withJurisdiction(final String jurisdiction) {
            this.jurisdiction = jurisdiction;
            return this;
        }

        public UpdateCourtScheduleBuilder withIsDraft(final Boolean isDraft) {
            this.isDraft = isDraft;
            return this;
        }

        public UpdateCourtSchedule build() {
            return new UpdateCourtSchedule(this);
        }
    }
}
