package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class AddJudiciaryAvailabilityRuleRequest {

    private String judiciaryId;
    private String courtHouseId;
    private AvailabilityType availabilityType;
    private LocalDate startDate;
    private LocalDate endDate;
    private RecurringType recurringType; // Weekly or Monthly (optional)
    private List<JudiciaryAvailabilityRuleRepeatDay> repeatDays;
    private String reason; // Optional
    private SessionType sessionType;

    public String getJudiciaryId() {
        return this.judiciaryId;
    }

    public void setJudiciaryId(String judiciaryId) {
        this.judiciaryId = judiciaryId;
    }

    public String getCourtHouseId() {
        return this.courtHouseId;
    }

    public void setCourtHouseId(String courtHouseId) {
        this.courtHouseId = courtHouseId;
    }

    public AvailabilityType getAvailabilityType() {
        return this.availabilityType;
    }

    public void setAvailabilityType(AvailabilityType availabilityType) {
        this.availabilityType = availabilityType;
    }

    public LocalDate getStartDate() {
        return this.startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return this.endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public RecurringType getRecurringType() {
        return this.recurringType;
    }

    public void setRecurringType(RecurringType recurringType) {
        this.recurringType = recurringType;
    }

    public List<JudiciaryAvailabilityRuleRepeatDay> getRepeatDays() {
        return this.repeatDays;
    }

    public void setRepeatDays(List<JudiciaryAvailabilityRuleRepeatDay> repeatDays) {
        this.repeatDays = repeatDays;
    }

    public String getReason() {
        return this.reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(final SessionType sessionType) {
        this.sessionType = sessionType;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof final AddJudiciaryAvailabilityRuleRequest that)) return false;
        return Objects.equals(getJudiciaryId(), that.getJudiciaryId()) && Objects.equals(getCourtHouseId(), that.getCourtHouseId()) && getAvailabilityType() == that.getAvailabilityType() && Objects.equals(getStartDate(), that.getStartDate()) && Objects.equals(getEndDate(), that.getEndDate()) && getRecurringType() == that.getRecurringType() && Objects.equals(getRepeatDays(), that.getRepeatDays()) && Objects.equals(getReason(), that.getReason()) && getSessionType() == that.getSessionType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getJudiciaryId(), getCourtHouseId(), getAvailabilityType(), getStartDate(), getEndDate(), getRecurringType(), getRepeatDays(), getReason(), getSessionType());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AddJudiciaryAvailabilityRuleRequest.class.getSimpleName() + "[", "]")
                .add("judiciaryId='" + getJudiciaryId() + "'")
                .add("courtHouseId='" + getCourtHouseId() + "'")
                .add("availabilityType=" + getAvailabilityType())
                .add("startDate=" + getStartDate())
                .add("endDate=" + getEndDate())
                .add("recurringType=" + getRecurringType())
                .add("repeatDays=" + getRepeatDays())
                .add("reason='" + getReason() + "'")
                .add("sessionType=" + getSessionType())
                .toString();
    }
}

