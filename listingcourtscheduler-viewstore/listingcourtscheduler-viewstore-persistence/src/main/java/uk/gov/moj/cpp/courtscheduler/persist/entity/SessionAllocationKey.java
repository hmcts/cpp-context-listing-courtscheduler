package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class SessionAllocationKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "oucode", nullable = false)
    private String ouCode;
    @Column(name = "court_room_id", nullable = false)
    private Integer roomId;
    @Column(name = "court_session", nullable = false)
    private String listingSession;
    @Column(name = "rota_business_type", nullable = false)
    private String businessType;

    public SessionAllocationKey() {
        //For JPA
    }

    public SessionAllocationKey(final String ouCode, final Integer roomId, final String listingSession, final String businessType) {
        this.ouCode = ouCode;
        this.roomId = roomId;
        this.listingSession = listingSession;
        this.businessType = businessType;
    }

    public String getOuCode() {
        return ouCode;
    }

    public void setOuCode(final String ouCode) {
        this.ouCode = ouCode;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public void setRoomId(final Integer roomId) {
        this.roomId = roomId;
    }

    public String getListingSession() {
        return listingSession;
    }

    public void setListingSession(final String listingSession) {
        this.listingSession = listingSession;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(final String businessType) {
        this.businessType = businessType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SessionAllocationKey)) {
            return false;
        }
        final SessionAllocationKey that = (SessionAllocationKey) o;
        return roomId == that.roomId &&
                Objects.equals(ouCode, that.ouCode) &&
                Objects.equals(listingSession, that.listingSession) &&
                Objects.equals(businessType, that.businessType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ouCode, roomId, listingSession, businessType);
    }

    @Override
    public String toString() {
        return "SessionAllocationKey{" +
                "ouCode='" + ouCode + '\'' +
                ", roomId=" + roomId +
                ", listingSession='" + listingSession + '\'' +
                ", businessType='" + businessType + '\'' +
                '}';
    }
}
