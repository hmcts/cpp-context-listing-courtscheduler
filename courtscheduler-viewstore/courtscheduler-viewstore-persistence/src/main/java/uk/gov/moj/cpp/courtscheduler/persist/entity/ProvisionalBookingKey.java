package uk.gov.moj.cpp.courtscheduler.persist.entity;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProvisionalBookingKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "court_schedule_id", nullable = false)
    private String courtScheduleId;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    public ProvisionalBookingKey() {
        //For JPA
    }

    public ProvisionalBookingKey(String courtScheduleId, String bookingId) {
        this.courtScheduleId = courtScheduleId;
        this.bookingId = bookingId;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProvisionalBookingKey that = (ProvisionalBookingKey) o;
        return Objects.equals(courtScheduleId, that.courtScheduleId) && Objects.equals(bookingId, that.bookingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtScheduleId, bookingId);
    }

    @Override
    public String toString() {
        return "ProvisionalBookingKey{" +
                "courtScheduleId=" + courtScheduleId +
                ", bookingId=" + bookingId +
                '}';
    }
}