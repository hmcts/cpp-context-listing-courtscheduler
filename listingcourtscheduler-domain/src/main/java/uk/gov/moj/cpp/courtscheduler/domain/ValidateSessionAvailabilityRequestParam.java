package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public class ValidateSessionAvailabilityRequestParam {

    private List<String> courtScheduleIds;
    private Integer slotsOrDuration;

    public List<String> getCourtScheduleIds() {
        return courtScheduleIds;
    }

    public Integer getSlotsOrDuration() {
        return slotsOrDuration;
    }

    public ValidateSessionAvailabilityRequestParam(List<String> courtScheduleIds, Integer duration) {
        this.courtScheduleIds = courtScheduleIds;
        this.slotsOrDuration = duration;
    }

    public static final class ValidateSessionAvailabilityRequestParamBuilder {
        private List<String> courtScheduleIds;
        private Integer slotsOrDuration;

        private ValidateSessionAvailabilityRequestParamBuilder() {
        }

        public static ValidateSessionAvailabilityRequestParamBuilder validateSessionAvailabilityRequestParam() {
            return new ValidateSessionAvailabilityRequestParamBuilder();
        }

        public ValidateSessionAvailabilityRequestParamBuilder withCourtScheduleIds(List<String> courtScheduleIds) {
            this.courtScheduleIds = courtScheduleIds;
            return this;
        }

        public ValidateSessionAvailabilityRequestParamBuilder withSlotsOrDuration(Integer slotsOrDuration) {
            this.slotsOrDuration = slotsOrDuration;
            return this;
        }

        public ValidateSessionAvailabilityRequestParam build() {
            return new ValidateSessionAvailabilityRequestParam(courtScheduleIds, slotsOrDuration);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final ValidateSessionAvailabilityRequestParam that)) return false;
        return Objects.equals(getSlotsOrDuration(), that.getSlotsOrDuration()) &&
                Objects.equals(getCourtScheduleIds(), that.getCourtScheduleIds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCourtScheduleIds(), getSlotsOrDuration());
    }

    @Override
    public String toString() {
        return "ValidateSessionAvailabilityRequestParam{" +
                "courtScheduleIds=" + courtScheduleIds +
                ", slotsOrDuration=" + slotsOrDuration +
                '}';
    }
}
