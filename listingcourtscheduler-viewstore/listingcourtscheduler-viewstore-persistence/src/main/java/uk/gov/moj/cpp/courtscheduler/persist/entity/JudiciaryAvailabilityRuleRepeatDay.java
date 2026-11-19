package uk.gov.moj.cpp.courtscheduler.persist.entity;

import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

@Embeddable
public class JudiciaryAvailabilityRuleRepeatDay implements Serializable {

    private static final long serialVersionUID = 1L;

    @Convert(converter = AvailabilityDayOfWeekConverter.class)
    @Column(name = "day_of_week", nullable = false)
    private AvailabilityDayOfWeek dayOfWeek; // Full name: Monday, Tuesday, etc.

    public JudiciaryAvailabilityRuleRepeatDay() {
        //For JPA
    }

    public JudiciaryAvailabilityRuleRepeatDay(final AvailabilityDayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public AvailabilityDayOfWeek getDayOfWeek() {
        return this.dayOfWeek;
    }

    public void setDayOfWeek(final AvailabilityDayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final JudiciaryAvailabilityRuleRepeatDay that = (JudiciaryAvailabilityRuleRepeatDay) o;
        return Objects.equals(this.dayOfWeek, that.dayOfWeek);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.dayOfWeek);
    }

    @Override
    public String toString() {
        return "JudiciaryAvailabilityRuleRepeatDay{" +
                "dayOfWeek='" + this.dayOfWeek + '\'' +
                '}';
    }
}

