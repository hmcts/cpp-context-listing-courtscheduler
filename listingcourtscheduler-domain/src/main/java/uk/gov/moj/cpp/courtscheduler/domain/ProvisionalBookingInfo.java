package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class ProvisionalBookingInfo extends CourtSchedule {

    private String bookingId;

    private Date hearingStartTime;

    private ProvisionalBookingInfo(final ProvisionalBookingInfoBuilder builder) {
        super(builder.courtScheduleBuilder);
        this.bookingId = builder.bookingId;
        this.hearingStartTime = builder.hearingStartTime;
    }

    private ProvisionalBookingInfo() { }

    public String getBookingId() { return bookingId; }

    public Date getHearingStartTime() { return hearingStartTime; }

    @Override
    public String toString() {
        return "ProvisionalBookingInfo{" +
                "bookingId='" + bookingId + '\'' +
                "hearingStartTime='" + hearingStartTime + '\'' +
                '}';
    }

    public static final class ProvisionalBookingInfoBuilder {
        private String bookingId;
        private Date hearingStartTime;
        private CourtScheduleBuilder courtScheduleBuilder = new CourtScheduleBuilder();

        public ProvisionalBookingInfoBuilder withBookingId(final String bookingId) {
            this.bookingId = bookingId;
            return this;
        }

        public ProvisionalBookingInfoBuilder withHearingStartTime(final Date hearingStartTime) {
            this.hearingStartTime = hearingStartTime;
            return this;
        }

        public ProvisionalBookingInfoBuilder withCourtScheduleId(final String courtScheduleId) {
            courtScheduleBuilder.withCourtScheduleId(courtScheduleId);
            return this;
        }

        public ProvisionalBookingInfoBuilder withPanel(final String panel) {
            courtScheduleBuilder.withPanel(panel);
            return this;
        }

        public ProvisionalBookingInfoBuilder withListingProfileId(final String listingProfileId) {
            courtScheduleBuilder.withListingProfileId(listingProfileId);
            return this;
        }

        public ProvisionalBookingInfoBuilder withOuCode(final String ouCode) {
            this.courtScheduleBuilder.withOuCode(ouCode);
            return this;
        }

        public ProvisionalBookingInfoBuilder withCourtHouseId(final String courtHouseId) {
            this.courtScheduleBuilder.withCourtHouseId(courtHouseId);
            return this;
        }

        public ProvisionalBookingInfoBuilder withCourtHouseName(final String courtHouseName) {
            this.courtScheduleBuilder.withCourtHouseName(courtHouseName);
            return this;
        }

        public ProvisionalBookingInfoBuilder withCourtRoomId(final String courtRoomId) {
            this.courtScheduleBuilder.withCourtRoomId(courtRoomId);
            return this;
        }

        public ProvisionalBookingInfoBuilder withCourtRoomNumber(final Integer courtRoomNumber) {
            this.courtScheduleBuilder.withCourtRoomNumber(courtRoomNumber);
            return this;
        }

        public ProvisionalBookingInfoBuilder withCourtRoomName(final String courtRoomName) {
            this.courtScheduleBuilder.withCourtRoomName(courtRoomName);
            return this;
        }

        public ProvisionalBookingInfoBuilder withOperationalUnit(final String operationalUnit) {
            this.courtScheduleBuilder.withOperationalUnit(operationalUnit);
            return this;
        }

        public ProvisionalBookingInfoBuilder withBusinessType(final String businessType) {
            this.courtScheduleBuilder.withBusinessType(businessType);
            return this;
        }

        public ProvisionalBookingInfoBuilder withCourtSession(final String courtSession) {
            this.courtScheduleBuilder.withCourtSession(courtSession);
            return this;
        }

        public ProvisionalBookingInfoBuilder withSessionDate(final LocalDate sessionDate) {
            this.courtScheduleBuilder.withSessionDate(sessionDate);
            return this;
        }

        public ProvisionalBookingInfoBuilder withAvailableSlots(final Integer availableSlot) {
            this.courtScheduleBuilder.withAvailableSlots(availableSlot);
            return this;
        }

        public ProvisionalBookingInfoBuilder withAvailableDuration(final Integer availableDuration) {
            this.courtScheduleBuilder.withAvailableDuration(availableDuration);
            return this;
        }

        public ProvisionalBookingInfoBuilder withMaxSlots(final Integer maxSlot) {
            this.courtScheduleBuilder.withMaxSlots(maxSlot);
            return this;
        }

        public ProvisionalBookingInfoBuilder withMaxDuration(final Integer maxDuration) {
            this.courtScheduleBuilder.withMaxDuration(maxDuration);
            return this;
        }

        public ProvisionalBookingInfoBuilder withJudiciaries(final List<CourtScheduleJudiciary> judiciaries) {
            this.courtScheduleBuilder.withJudiciaries(judiciaries);
            return this;
        }

        public ProvisionalBookingInfo build() {
            return new ProvisionalBookingInfo(this);
        }
    }
}
