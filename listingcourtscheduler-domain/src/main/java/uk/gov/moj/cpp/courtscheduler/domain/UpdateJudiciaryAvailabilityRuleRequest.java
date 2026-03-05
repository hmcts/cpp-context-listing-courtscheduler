package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Request for updating judiciary availability rule.
 */
public class UpdateJudiciaryAvailabilityRuleRequest extends BaseJudiciaryAvailabilityRuleWithDetailsRequest {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        UpdateJudiciaryAvailabilityRuleRequest that = (UpdateJudiciaryAvailabilityRuleRequest) o;
        return Objects.equals(repeatDays, that.repeatDays) &&
                sessionType == that.sessionType &&
                Objects.equals(unavailabilities, that.unavailabilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), repeatDays, sessionType, unavailabilities);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", UpdateJudiciaryAvailabilityRuleRequest.class.getSimpleName() + "[", "]")
                .add("ruleId='" + ruleId + "'")
                .add("judiciaryId='" + judiciaryId + "'")
                .add("courtHouseId='" + courtHouseId + "'")
                .add("startDate=" + startDate)
                .add("endDate=" + endDate)
                .add("repeatDays=" + repeatDays)
                .add("sessionType=" + sessionType)
                .add("unavailabilities=" + unavailabilities)
                .toString();
    }
}




