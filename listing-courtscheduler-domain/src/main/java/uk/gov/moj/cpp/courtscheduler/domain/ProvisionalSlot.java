package uk.gov.moj.cpp.courtscheduler.domain;

public class ProvisionalSlot {

    private String courtScheduleId;

    private String hearingStartTime;

    public ProvisionalSlot() {
    }

    public ProvisionalSlot(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public ProvisionalSlot(final String courtScheduleId, final String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
        this.courtScheduleId = courtScheduleId;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public String getHearingStartTime() { return hearingStartTime; }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public void setHearingStartTime(final String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    public static final class ProvisionalSlotBuilder {
        private String courtScheduleId;
        private String hearingStartTime;

        private ProvisionalSlotBuilder() {
        }

        public static ProvisionalSlotBuilder aProvisionalSlot() {
            return new ProvisionalSlotBuilder();
        }

        public ProvisionalSlotBuilder withCourtScheduleId(String courtScheduleId) {
            this.courtScheduleId = courtScheduleId;
            return this;
        }

        public ProvisionalSlotBuilder withHearingStartTime(String hearingStartTime) {
            this.hearingStartTime = hearingStartTime;
            return this;
        }

        public ProvisionalSlot build() {
            ProvisionalSlot provisionalSlot = new ProvisionalSlot();
            provisionalSlot.setCourtScheduleId(courtScheduleId);
            provisionalSlot.setHearingStartTime(hearingStartTime);
            return provisionalSlot;
        }
    }
}

