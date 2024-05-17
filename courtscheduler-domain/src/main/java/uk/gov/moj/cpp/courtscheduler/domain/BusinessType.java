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

}
