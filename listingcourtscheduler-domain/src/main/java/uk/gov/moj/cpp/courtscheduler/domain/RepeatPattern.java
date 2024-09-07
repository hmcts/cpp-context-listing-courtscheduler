package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class RepeatPattern {

    private RepeatFrequency frequency;
    private Integer repeatFor;
    private LocalDate startDate;
    private LocalDate endDate;

    public RepeatPattern() {
    }

    public RepeatPattern(final RepeatFrequency frequency, final Integer repeatFor, final LocalDate startDate, final LocalDate endDate) {
        this.frequency = frequency;
        this.repeatFor = repeatFor;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public RepeatFrequency getFrequency() {
        return frequency;
    }

    public Integer getRepeatFor() {
        return repeatFor;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }


    public static final class RepeatPatternBuilder {
        private RepeatFrequency frequency;
        private Integer repeatFor;
        private LocalDate startDate;
        private LocalDate endDate;

        private RepeatPatternBuilder() {
        }

        public static RepeatPatternBuilder repeatPattern() {
            return new RepeatPatternBuilder();
        }

        public RepeatPatternBuilder withFrequency(RepeatFrequency frequency) {
            this.frequency = frequency;
            return this;
        }

        public RepeatPatternBuilder withRepeatFor(Integer repeatFor) {
            this.repeatFor = repeatFor;
            return this;
        }

        public RepeatPatternBuilder withStartDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public RepeatPatternBuilder withEndDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public RepeatPattern build() {
            return new RepeatPattern(frequency, repeatFor, startDate, endDate);
        }
    }
}
