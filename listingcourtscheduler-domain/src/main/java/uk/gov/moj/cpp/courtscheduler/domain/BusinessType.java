package uk.gov.moj.cpp.courtscheduler.domain;

@SuppressWarnings({"squid:S1845"})
public class BusinessType {

    private String id;
    private Integer seqNum;
    private String typeCode;
    private String typeDescription;
    private boolean slot;
    private boolean duration;



    public BusinessType() {
    }

    public BusinessType(final String id,
                        final Integer seqNum,
                        final String typeCode,
                        final String typeDescription,
                        final boolean slot,
                        final boolean duration) {
        this.id = id;
        this.seqNum = seqNum;
        this.typeCode = typeCode;
        this.typeDescription = typeDescription;
        this.slot = slot;
        this.duration = duration;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Integer getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(final Integer seqNum) {
        this.seqNum = seqNum;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(final String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeDescription() {
        return typeDescription;
    }

    public void setTypeDescription(final String typeDescription) {
        this.typeDescription = typeDescription;
    }

    public boolean isSlot() {
        return slot;
    }

    public void setSlot(final boolean slot) {
        this.slot = slot;
    }

    public boolean isDuration() {
        return duration;
    }

    public void setDuration(final boolean duration) {
        this.duration = duration;
    }

    public static final class BusinessTypeBuilder {
        private String id;
        private Integer seqNum;
        private String typeCode;
        private String typeDescription;
        private boolean slot;
        private boolean duration;

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

        public BusinessType build() {
            BusinessType businessType = new BusinessType();
            businessType.setId(id);
            businessType.setSeqNum(seqNum);
            businessType.setTypeCode(typeCode);
            businessType.setTypeDescription(typeDescription);
            businessType.setSlot(slot);
            businessType.setDuration(duration);
            return businessType;
        }
    }
}
