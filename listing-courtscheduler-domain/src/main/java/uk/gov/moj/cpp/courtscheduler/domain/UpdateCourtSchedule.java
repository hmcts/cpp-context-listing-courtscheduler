package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class UpdateCourtSchedule {

    private String courtScheduleId;
    private String courtHouseId;// same as courtCentreId
    private String courtRoomId;
    private String sessionType;
    private String businessType;
    private LocalDate sessionDate;
    private String panel;



    private Integer availableSlots;
    private Integer availableDuration;



    private Integer maxSlots;
    private Integer maxDuration;

    protected UpdateCourtSchedule(final UpdateCourtScheduleBuilder builder) {
        this.courtScheduleId = builder.courtScheduleId;
        this.courtRoomId = builder.courtRoomId;
        this.courtHouseId = builder.courtHouseId;
        this.businessType = builder.businessType;
        this.sessionType = builder.sessionType;
        this.panel = builder.panel;
        this.sessionDate = builder.sessionDate;
        this.availableSlots = builder.availableSlots;
        this.availableDuration = builder.availableDuration;
        this.maxSlots = builder.maxSlots;
        this.maxDuration = builder.maxDuration;
    }

    public UpdateCourtSchedule() {
    }


    public String getPanel() {
        return panel;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }


    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public String getCourtHouseId() {
        return courtHouseId;
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


    public void setCourtHouseId(final String courtHouseId) {
        this.courtHouseId = courtHouseId;
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


    public void setSessionDate(final LocalDate sessionDate) {
        this.sessionDate = sessionDate;
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


    public static final class UpdateCourtScheduleBuilder {
        private String courtScheduleId;
        private String courtHouseId;// same as courtCentreId
        private String courtRoomId;
        private String sessionType;
        private String businessType;
        private LocalDate sessionDate;
        private String panel;

        private Integer availableSlots = 0;
        private Integer availableDuration = 0;
        private Integer maxSlots = 0;
        private Integer maxDuration = 0;

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


        public UpdateCourtScheduleBuilder withCourtHouseId(final String courtHouseId) {
            this.courtHouseId = courtHouseId;
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

        public UpdateCourtScheduleBuilder withSessionDate(final LocalDate sessionDate) {
            this.sessionDate = sessionDate;
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


        public UpdateCourtSchedule build() {
            return new UpdateCourtSchedule(this);
        }
    }
}
