package uk.gov.moj.cpp.courtscheduler.domain;

@SuppressWarnings({"squid:S1845"})
public class BusinessType {

    private String id;
    private Integer seqNum;
    private String typeCode;
    private String typeDescription;
    private boolean slot;
    private boolean duration;
    private String jurisdiction;



    public BusinessType() {
    }

    public BusinessType(final String id,
                        final Integer seqNum,
                        final String typeCode,
                        final String typeDescription,
                        final boolean slot,
                        final boolean duration,
                        final String jurisdiction) {
        this.id = id;
        this.seqNum = seqNum;
        this.typeCode = typeCode;
        this.typeDescription = typeDescription;
        this.slot = slot;
        this.duration = duration;
        this.jurisdiction = jurisdiction;
    }

    public String getId() {
        return this.id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Integer getSeqNum() {
        return this.seqNum;
    }

    public void setSeqNum(final Integer seqNum) {
        this.seqNum = seqNum;
    }

    public String getTypeCode() {
        return this.typeCode;
    }

    public void setTypeCode(final String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeDescription() {
        return this.typeDescription;
    }

    public void setTypeDescription(final String typeDescription) {
        this.typeDescription = typeDescription;
    }

    public boolean isSlot() {
        return this.slot;
    }

    public void setSlot(final boolean slot) {
        this.slot = slot;
    }

    public boolean isDuration() {
        return this.duration;
    }

    public void setDuration(final boolean duration) {
        this.duration = duration;
    }

    public String getJurisdiction() {
        return this.jurisdiction;
    }

    public void setJurisdiction(final String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public static final class BusinessTypeBuilder {
        private String id;
        private Integer seqNum;
        private String typeCode;
        private String typeDescription;
        private boolean slot;
        private boolean duration;
        private String jurisdiction;

        private BusinessTypeBuilder() {
        }

        public static BusinessTypeBuilder aBusinessType() {
            return new BusinessTypeBuilder();
        }

        public BusinessTypeBuilder withId(String id) {
            this.id = id;
            return this;
        }

        public BusinessTypeBuilder withSeqNum(Integer seqNum) {
            this.seqNum = seqNum;
            return this;
        }

        public BusinessTypeBuilder withTypeCode(String typeCode) {
            this.typeCode = typeCode;
            return this;
        }

        public BusinessTypeBuilder withTypeDescription(String typeDescription) {
            this.typeDescription = typeDescription;
            return this;
        }

        public BusinessTypeBuilder withSlot(boolean slot) {
            this.slot = slot;
            return this;
        }

        public BusinessTypeBuilder withDuration(boolean duration) {
            this.duration = duration;
            return this;
        }

        public BusinessTypeBuilder withJurisdiction(String jurisdiction) {
            this.jurisdiction = jurisdiction;
            return this;
        }

        public BusinessType build() {
            BusinessType businessType = new BusinessType();
            businessType.setId(this.id);
            businessType.setSeqNum(this.seqNum);
            businessType.setTypeCode(this.typeCode);
            businessType.setTypeDescription(this.typeDescription);
            businessType.setSlot(this.slot);
            businessType.setDuration(this.duration);
            businessType.setJurisdiction(this.jurisdiction);
            return businessType;
        }
    }
}
