package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class UpdateCourtSchedule {

    private String courtScheduleId;
    private String courtHouseId;// same as courtCentreId
    private String courtRoomId;
    private String sessionType;
    private String businessType;
    private LocalDate sessionDate;
    private String panel;


    protected UpdateCourtSchedule(final UpdateCourtScheduleBuilder builder) {
        this.courtScheduleId = builder.courtScheduleId;
        this.courtRoomId = builder.courtRoomId;
        this.courtHouseId = builder.courtHouseId;
        this.businessType = builder.businessType;
        this.sessionType = builder.sessionType;
        this.panel = builder.panel;
        this.sessionDate = builder.sessionDate;
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


    public static final class UpdateCourtScheduleBuilder {
        private String courtScheduleId;
        private String courtHouseId;// same as courtCentreId
        private String courtRoomId;
        private String sessionType;
        private String businessType;
        private LocalDate sessionDate;
        private String panel;

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


        public UpdateCourtSchedule build() {
            return new UpdateCourtSchedule(this);
        }
    }
}
