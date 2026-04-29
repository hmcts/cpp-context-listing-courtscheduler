package uk.gov.moj.cpp.courtscheduler.domain;

public class CourtRoomSessionAllocation {
    private String id;
    private Integer courtRoomId;
    private String oucode;
    private Integer maxSlot;
    private Integer maxDurationMins;
    private String rotaBusinessTypeCode;
    private String courtSession;
    private String validFrom;
    private String validTo;
    private String sessionStartTime;
    private String sessionEndTime;

    public CourtRoomSessionAllocation() {
    }

    public CourtRoomSessionAllocation(final String id, final Integer courtRoomId, final String oucode, final Integer maxSlot, final Integer maxDurationMins, final String rotaBusinessTypeCode, final String courtSession) {
        this.id = id;
        this.courtRoomId = courtRoomId;
        this.oucode = oucode;
        this.maxSlot = maxSlot;
        this.maxDurationMins = maxDurationMins;
        this.rotaBusinessTypeCode = rotaBusinessTypeCode;
        this.courtSession = courtSession;
    }

    public CourtRoomSessionAllocation(final String id, final Integer courtRoomId, final String oucode, final Integer maxSlot, final Integer maxDurationMins, final String rotaBusinessTypeCode, final String courtSession, final String validFrom) {
        this.id = id;
        this.courtRoomId = courtRoomId;
        this.oucode = oucode;
        this.maxSlot = maxSlot;
        this.maxDurationMins = maxDurationMins;
        this.rotaBusinessTypeCode = rotaBusinessTypeCode;
        this.courtSession = courtSession;
        this.validFrom = validFrom;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Integer getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(final Integer courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public String getOucode() {
        return oucode;
    }

    public void setOucode(final String oucode) {
        this.oucode = oucode;
    }

    public Integer getMaxSlot() {
        return maxSlot;
    }

    public void setMaxSlot(final Integer maxSlot) {
        this.maxSlot = maxSlot;
    }

    public Integer getMaxDurationMins() {
        return maxDurationMins;
    }

    public void setMaxDurationMins(final Integer maxDurationMins) {
        this.maxDurationMins = maxDurationMins;
    }

    public String getRotaBusinessTypeCode() {
        return rotaBusinessTypeCode;
    }

    public void setRotaBusinessTypeCode(final String rotaBusinessTypeCode) {
        this.rotaBusinessTypeCode = rotaBusinessTypeCode;
    }

    public String getCourtSession() {
        return courtSession;
    }

    public void setCourtSession(final String courtSession) {
        this.courtSession = courtSession;
    }

    public String getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(final String validFrom) {
        this.validFrom = validFrom;
    }

    public String getValidTo() {
        return validTo;
    }

    public void setValidTo(final String validTo) {
        this.validTo = validTo;
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

    @Override
    public String toString() {
        return "CourtRoomSessionAllocation{" +
                "id='" + id + '\'' +
                ", courtRoomId=" + courtRoomId +
                ", oucode='" + oucode + '\'' +
                ", maxSlot=" + maxSlot +
                ", maxDurationMins=" + maxDurationMins +
                ", rotaBusinessTypeCode='" + rotaBusinessTypeCode + '\'' +
                ", courtSession='" + courtSession + '\'' +
                ", validFrom='" + validFrom + '\'' +
                ", validTo='" + validTo + '\'' +
                ", sessionStartTime='" + sessionStartTime + '\'' +
                ", sessionEndTime='" + sessionEndTime + '\'' +
                '}';
    }

    public static final class CourtRoomSessionAllocationBuilder {
        private String id;
        private Integer courtRoomId;
        private String oucode;
        private Integer maxSlot;
        private Integer maxDurationMins;
        private String rotaBusinessTypeCode;
        private String courtSession;
        private String validFrom;
        private String validTo;
        private String sessionStartTime;
        private String sessionEndTime;

        private CourtRoomSessionAllocationBuilder() {}

        public static CourtRoomSessionAllocationBuilder aCourtRoomSessionAllocation() {
            return new CourtRoomSessionAllocationBuilder();
        }

        public CourtRoomSessionAllocationBuilder withId(final String id) {
            this.id = id;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withCourtRoomId(final Integer courtRoomId) {
            this.courtRoomId = courtRoomId;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withOucode(final String oucode) {
            this.oucode = oucode;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withMaxSlot(final Integer maxSlot) {
            this.maxSlot = maxSlot;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withMaxDurationMins(final Integer maxDurationMins) {
            this.maxDurationMins = maxDurationMins;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withRotaBusinessTypeCode(final String rotaBusinessTypeCode) {
            this.rotaBusinessTypeCode = rotaBusinessTypeCode;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withCourtSession(final String courtSession) {
            this.courtSession = courtSession;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withValidFrom(final String validFrom) {
            this.validFrom = validFrom;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withValidTo(final String validTo) {
            this.validTo = validTo;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withSessionStartTime(final String sessionStartTime) {
            this.sessionStartTime = sessionStartTime;
            return this;
        }

        public CourtRoomSessionAllocationBuilder withSessionEndTime(final String sessionEndTime) {
            this.sessionEndTime = sessionEndTime;
            return this;
        }

        public CourtRoomSessionAllocation build() {
            final CourtRoomSessionAllocation courtRoomSessionAllocation = new CourtRoomSessionAllocation();
            courtRoomSessionAllocation.setId(this.id);
            courtRoomSessionAllocation.setCourtRoomId(this.courtRoomId);
            courtRoomSessionAllocation.setOucode(this.oucode);
            courtRoomSessionAllocation.setMaxSlot(this.maxSlot);
            courtRoomSessionAllocation.setMaxDurationMins(this.maxDurationMins);
            courtRoomSessionAllocation.setCourtSession(this.courtSession);
            courtRoomSessionAllocation.setRotaBusinessTypeCode(this.rotaBusinessTypeCode);
            courtRoomSessionAllocation.setValidFrom(this.validFrom);
            courtRoomSessionAllocation.setValidTo(this.validTo);
            courtRoomSessionAllocation.setSessionStartTime(this.sessionStartTime);
            courtRoomSessionAllocation.setSessionEndTime(this.sessionEndTime);
            return courtRoomSessionAllocation;
        }

    }

}
