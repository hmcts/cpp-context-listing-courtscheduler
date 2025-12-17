package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Intermediate base class for judiciary availability rule requests that include
 * recurring type, repeat days, session type, and unavailabilities.
 * Used by Add and Update requests to share common functionality.
 */
public abstract class BaseJudiciaryAvailabilityRuleWithDetailsRequest extends BaseJudiciaryAvailabilityRuleRequest {

    protected RecurringType recurringType;
    protected List<JudiciaryAvailabilityRuleRepeatDay> repeatDays;
    protected SessionType sessionType;
    protected List<JudiciaryUnavailabilityRequest> unavailabilities;

    public RecurringType getRecurringType() {
        return recurringType;
    }

    public void setRecurringType(RecurringType recurringType) {
        this.recurringType = recurringType;
    }

    /**
     * Returns an unmodifiable view of the repeat days list.
     * Use setRepeatDays to modify the list.
     */
    public List<JudiciaryAvailabilityRuleRepeatDay> getRepeatDays() {
        return repeatDays == null ? null : Collections.unmodifiableList(repeatDays);
    }

    /**
     * Sets the repeat days list. Creates a defensive copy to prevent external modification.
     */
    public void setRepeatDays(List<JudiciaryAvailabilityRuleRepeatDay> repeatDays) {
        this.repeatDays = repeatDays == null ? null : new ArrayList<>(repeatDays);
    }

    public SessionType getSessionType() {
        return sessionType;
    }

    public void setSessionType(SessionType sessionType) {
        this.sessionType = sessionType;
    }

    /**
     * Returns an unmodifiable view of the unavailabilities list.
     * Use setUnavailabilities to modify the list.
     */
    public List<JudiciaryUnavailabilityRequest> getUnavailabilities() {
        return unavailabilities == null ? null : Collections.unmodifiableList(unavailabilities);
    }

    /**
     * Sets the unavailabilities list. Creates a defensive copy to prevent external modification.
     */
    public void setUnavailabilities(List<JudiciaryUnavailabilityRequest> unavailabilities) {
        this.unavailabilities = unavailabilities == null ? null : new ArrayList<>(unavailabilities);
    }
}
