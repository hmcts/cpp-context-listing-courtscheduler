package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class CourtScheduleJudiciaryKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "court_schedule_id", nullable = false)
    private String courtScheduleId;

    @Column(name = "judiciary_id", nullable = false)
    private String judiciaryId;

    public CourtScheduleJudiciaryKey() {
        //For JPA
    }

    public CourtScheduleJudiciaryKey(String courtScheduleId, String judiciaryId) {
        this.courtScheduleId = courtScheduleId;
        this.judiciaryId = judiciaryId;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getJudiciaryId() {
        return judiciaryId;
    }

    public void setJudiciaryId(String judiciaryId) {
        this.judiciaryId = judiciaryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CourtScheduleJudiciaryKey that = (CourtScheduleJudiciaryKey) o;
        return Objects.equals(courtScheduleId, that.courtScheduleId) && Objects.equals(judiciaryId, that.judiciaryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtScheduleId, judiciaryId);
    }

    @Override
    public String toString() {
        return "ProvisionalBookingKey{" +
                "courtScheduleId=" + courtScheduleId +
                ", bookingId=" + judiciaryId +
                '}';
    }
}