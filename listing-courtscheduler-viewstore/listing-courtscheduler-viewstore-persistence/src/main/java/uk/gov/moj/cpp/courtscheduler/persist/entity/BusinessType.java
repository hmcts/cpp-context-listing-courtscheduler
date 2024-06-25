package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.sql.Timestamp;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@SuppressWarnings({"squid:S1845"})
@Entity
@Table(name = "business_type")
public class BusinessType {

    @Id
    private BusinessTypeKey id;

    @Column(name = "seq_num", nullable = false)
    private Integer seqNum;

    @Column(name = "type_code", nullable = false)
    private String typeCode;

    @Column(name = "type_description", nullable = false)
    private String typeDescription;

    @Column(name = "slot", nullable = false)
    private boolean slot;
    @Column(name = "duration", nullable = false)
    private boolean duration;

    @Column(name = "created_on", nullable = false)
    private Timestamp createdOn;

    public BusinessType() {
        //For JPA
    }



    public BusinessTypeKey getId() {
        return id;
    }

    public void setId(final BusinessTypeKey id) {
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

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (null == o || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(this.id, ((BusinessType) o).id);
    }

    @Override
    public String toString() {
        return "BusinessType{" +
                "id='" + id + '\'' +
                ", seqNum=" + seqNum +
                ", typeCode='" + typeCode + '\'' +
                ", typeDescription='" + typeDescription + '\'' +
                ", slot=" + slot +
                ", duration=" + duration +
                '}';
    }
}
