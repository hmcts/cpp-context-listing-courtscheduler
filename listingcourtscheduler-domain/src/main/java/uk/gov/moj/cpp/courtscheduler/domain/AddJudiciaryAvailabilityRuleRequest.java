package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public class AddJudiciaryAvailabilityRuleRequest {

    private String judiciaryId;
    private String courtHouseId;
    private LocalDate startDate;
    private LocalDate endDate;
    private RecurringType recurringType; // Weekly or Monthly (optional)
    private List<JudiciaryAvailabilityRuleRepeatDay> repeatDays;
    private SessionType sessionType;
    private List<JudiciaryUnavailabilityRequest> unavailabilities; // Optional array of unavailability periods

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

    public SessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(final SessionType sessionType) {
        this.sessionType = sessionType;
    }

    public List<JudiciaryUnavailabilityRequest> getUnavailabilities() {
        return unavailabilities;
    }

    public void setUnavailabilities(List<JudiciaryUnavailabilityRequest> unavailabilities) {
        this.unavailabilities = unavailabilities;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof final AddJudiciaryAvailabilityRuleRequest that)) return false;
        return Objects.equals(getJudiciaryId(), that.getJudiciaryId()) && Objects.equals(getCourtHouseId(), that.getCourtHouseId()) && Objects.equals(getStartDate(), that.getStartDate()) && Objects.equals(getEndDate(), that.getEndDate()) && getRecurringType() == that.getRecurringType() && Objects.equals(getRepeatDays(), that.getRepeatDays()) && getSessionType() == that.getSessionType() && Objects.equals(getUnavailabilities(), that.getUnavailabilities());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getJudiciaryId(), getCourtHouseId(), getStartDate(), getEndDate(), getRecurringType(), getRepeatDays(), getSessionType(), getUnavailabilities());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AddJudiciaryAvailabilityRuleRequest.class.getSimpleName() + "[", "]")
                .add("judiciaryId='" + getJudiciaryId() + "'")
                .add("courtHouseId='" + getCourtHouseId() + "'")
                .add("startDate=" + getStartDate())
                .add("endDate=" + getEndDate())
                .add("recurringType=" + getRecurringType())
                .add("repeatDays=" + getRepeatDays())
                .add("sessionType=" + getSessionType())
                .add("unavailabilities=" + getUnavailabilities())
                .toString();
    }
}

