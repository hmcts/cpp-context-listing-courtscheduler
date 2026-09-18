package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class JudiciaryAvailabilityRuleResponse {

    private String id;
    private String judiciaryId;
    private String courtHouseId;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<AvailabilityDayOfWeek> repeatDays;
    private SessionType sessionType;
    private List<JudiciaryUnavailabilityResponse> unavailabilities;

    public JudiciaryAvailabilityRuleResponse() {
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public List<AvailabilityDayOfWeek> getRepeatDays() {
        return this.repeatDays;
    }

    public void setRepeatDays(List<AvailabilityDayOfWeek> repeatDays) {
        this.repeatDays = repeatDays;
    }

    public SessionType getSessionType() {
        return this.sessionType;
    }

    public void setSessionType(SessionType sessionType) {
        this.sessionType = sessionType;
    }

    public List<JudiciaryUnavailabilityResponse> getUnavailabilities() {
        return unavailabilities;
    }

    public void setUnavailabilities(List<JudiciaryUnavailabilityResponse> unavailabilities) {
        this.unavailabilities = unavailabilities;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final JudiciaryAvailabilityRuleResponse that = (JudiciaryAvailabilityRuleResponse) o;
        return Objects.equals(this.id, that.id) &&
                Objects.equals(this.judiciaryId, that.judiciaryId) &&
                Objects.equals(this.courtHouseId, that.courtHouseId) &&
                Objects.equals(this.startDate, that.startDate) &&
                Objects.equals(this.endDate, that.endDate) &&
                Objects.equals(this.repeatDays, that.repeatDays) &&
                this.sessionType == that.sessionType &&
                Objects.equals(this.unavailabilities, that.unavailabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.judiciaryId, this.courtHouseId, this.startDate, this.endDate, this.repeatDays, this.sessionType, this.unavailabilities);
    }

    @Override
    public String toString() {
        return "JudiciaryAvailabilityRuleResponse{" +
                "id='" + this.id + '\'' +
                ", judiciaryId='" + this.judiciaryId + '\'' +
                ", courtHouseId='" + this.courtHouseId + '\'' +
                ", startDate=" + this.startDate +
                ", endDate=" + this.endDate +
                ", repeatDays=" + this.repeatDays +
                ", sessionType=" + this.sessionType +
                ", unavailabilities=" + this.unavailabilities +
                '}';
    }
}

