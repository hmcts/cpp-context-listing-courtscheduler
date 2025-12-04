package uk.gov.moj.cpp.courtscheduler.persist.entity;

import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Embeddable
public class JudiciaryAvailabilityRuleRepeatDay implements Serializable {

    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private AvailabilityDayOfWeek dayOfWeek; // Full name: Monday, Tuesday, etc.

    @Column(name = "day_index", nullable = false)
    private Integer index; // Optional index for recurring patterns (0 means no index)

    public JudiciaryAvailabilityRuleRepeatDay() {
        //For JPA
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

