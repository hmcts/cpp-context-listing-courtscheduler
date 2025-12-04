package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public class JudiciaryAvailabilityRuleRepeatDay {

    private AvailabilityDayOfWeek dayOfWeek; // Full name: Monday, Tuesday, etc.
    private Integer index; // Optional index for recurring patterns

    public JudiciaryAvailabilityRuleRepeatDay() {
    }

    public JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek dayOfWeek, Integer index) {
        this.dayOfWeek = dayOfWeek;
        this.index = index;
    }

    public AvailabilityDayOfWeek getDayOfWeek() {
        return this.dayOfWeek;
    }

    public void setDayOfWeek(AvailabilityDayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public Integer getIndex() {
        return this.index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final JudiciaryAvailabilityRuleRepeatDay that = (JudiciaryAvailabilityRuleRepeatDay) o;
        return Objects.equals(this.dayOfWeek, that.dayOfWeek) && Objects.equals(this.index, that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.dayOfWeek, this.index);
    }

    @Override
    public String toString() {
        return "JudiciaryAvailabilityRuleRepeatDay{" +
                "dayOfWeek='" + this.dayOfWeek + '\'' +
                ", index=" + this.index +
                '}';
    }
}

