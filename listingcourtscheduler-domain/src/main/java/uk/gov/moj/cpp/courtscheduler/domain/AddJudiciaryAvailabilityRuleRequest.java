package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Request for adding judiciary availability rule.
 */
public class AddJudiciaryAvailabilityRuleRequest extends BaseJudiciaryAvailabilityRuleWithDetailsRequest {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AddJudiciaryAvailabilityRuleRequest that = (AddJudiciaryAvailabilityRuleRequest) o;
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
        return new StringJoiner(", ", AddJudiciaryAvailabilityRuleRequest.class.getSimpleName() + "[", "]")
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

