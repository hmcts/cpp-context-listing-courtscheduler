package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public record ValidateSessionAvailabilityRequestParam(List<String> courtScheduleIds,
                                                      Integer slotsOrDuration) {

    public boolean isListMode() {
        return courtScheduleIds != null && !courtScheduleIds.isEmpty();
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
        return Objects.equals(slotsOrDuration(), that.slotsOrDuration())
                && Objects.equals(courtScheduleIds(), that.courtScheduleIds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtScheduleIds(), slotsOrDuration());
    }

    @Override
    public String toString() {
        return "ValidateSessionAvailabilityRequestParam{"
                + "courtScheduleIds=" + courtScheduleIds
                + ", slotsOrDuration=" + slotsOrDuration
                + '}';
    }
}
