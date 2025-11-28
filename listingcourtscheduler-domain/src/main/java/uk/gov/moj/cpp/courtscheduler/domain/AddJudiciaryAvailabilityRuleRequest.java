package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class AddJudiciaryAvailabilityRuleRequest {

    private String judiciaryId;
    private String courtHouseId;
    private String group; // Available or Unavailable
    private LocalDate startDate;
    private LocalDate endDate;
    private String recurringType; // Weekly or Monthly (optional)
    private List<JudiciaryAvailabilityRuleRepeatDay> repeatDays;
    private String reason; // Optional

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

    public String getGroup() {
        return this.group;
    }

    public void setGroup(String group) {
        this.group = group;
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

    public String getRecurringType() {
        return this.recurringType;
    }

    public void setRecurringType(String recurringType) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final AddJudiciaryAvailabilityRuleRequest that = (AddJudiciaryAvailabilityRuleRequest) o;
        return Objects.equals(this.judiciaryId, that.judiciaryId) &&
                Objects.equals(this.courtHouseId, that.courtHouseId) &&
                Objects.equals(this.group, that.group) &&
                Objects.equals(this.startDate, that.startDate) &&
                Objects.equals(this.endDate, that.endDate) &&
                Objects.equals(this.recurringType, that.recurringType) &&
                Objects.equals(this.repeatDays, that.repeatDays) &&
                Objects.equals(this.reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.judiciaryId, this.courtHouseId, this.group, this.startDate, this.endDate, this.recurringType, this.repeatDays, this.reason);
    }

    @Override
    public String toString() {
        return "AddJudiciaryAvailabilityRuleRequest{" +
                "judiciaryId='" + this.judiciaryId + '\'' +
                ", courtHouseId='" + this.courtHouseId + '\'' +
                ", group='" + this.group + '\'' +
                ", startDate=" + this.startDate +
                ", endDate=" + this.endDate +
                ", recurringType='" + this.recurringType + '\'' +
                ", repeatDays=" + this.repeatDays +
                ", reason='" + this.reason + '\'' +
                '}';
    }
}

