package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Embeddable
@SuppressWarnings({"squid:S1948"})
public class ProvisionalBookingKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "court_schedule_id", insertable = false, updatable = false)
    private CourtSchedule courtSchedule;


    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    public ProvisionalBookingKey() {
        //For JPA
    }

    public ProvisionalBookingKey(CourtSchedule courtSchedule, String bookingId) {
        this.courtSchedule = courtSchedule;
        this.bookingId = bookingId;
    }

    public CourtSchedule getCourtSchedule() {
        return courtSchedule;
    }

    public void setCourtSchedule(CourtSchedule courtSchedule) {
        this.courtSchedule = courtSchedule;
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
        return Objects.equals(courtSchedule.getCourtScheduleId(), that.getCourtSchedule().getCourtScheduleId()) && Objects.equals(bookingId, that.bookingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtSchedule.getCourtScheduleId(), bookingId);
    }

    @Override
    public String toString() {
        return "ProvisionalBookingKey{" +
                "courtScheduleId=" + courtSchedule.getCourtScheduleId() +
                ", bookingId=" + bookingId +
                '}';
    }
}