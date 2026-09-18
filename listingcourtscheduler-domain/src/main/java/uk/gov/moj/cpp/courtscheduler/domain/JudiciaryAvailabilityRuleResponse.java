package uk.gov.moj.cpp.courtscheduler.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Setter
public class JudiciaryAvailabilityRuleResponse {

    @Getter
    private String id;
    @Getter
    private String judiciaryId;
    @Getter
    private String courtHouseId;
    @Getter
    private LocalDate startDate;
    @Getter
    private LocalDate endDate;
    private List<AvailabilityDayOfWeek> repeatDays;
    @Getter
    private SessionType sessionType;
    @Getter
    private List<JudiciaryUnavailabilityResponse> unavailabilities;

    public JudiciaryAvailabilityRuleResponse() {
    }

    public List<AvailabilityDayOfWeek> getRepeatDays() {
        return this.repeatDays == null ? Collections.emptyList() : this.repeatDays;
    }

    @Override
    public boolean equals(final Object o) {
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

