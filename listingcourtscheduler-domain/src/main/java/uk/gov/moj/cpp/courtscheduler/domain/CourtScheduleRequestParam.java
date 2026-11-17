package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public record CourtScheduleRequestParam(String courtCentreId,
                                        String courtRoomId,
                                        String businessType,
                                        String sessionStartDate,
                                        String sessionEndDate,
                                        Boolean isDraft,
                                        String pageSize,
                                        String pageNumber,
                                        Boolean isWeekCommencing
) {

    /**
     * Without the week commencing filter: {@link #isWeekCommencing()} is {@code null}, which the
     * query treats as "both kinds of session".
     */
    public CourtScheduleRequestParam(final String courtCentreId, final String courtRoomId, final String businessType,
                                     final String sessionStartDate, final String sessionEndDate, final Boolean isDraft,
                                     final String pageSize, final String pageNumber) {
        this(courtCentreId, courtRoomId, businessType, sessionStartDate, sessionEndDate, isDraft, pageSize, pageNumber, null);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CourtScheduleRequestParam that = (CourtScheduleRequestParam) o;
        return Objects.equals(courtCentreId, that.courtCentreId) && Objects.equals(courtRoomId, that.courtRoomId)
                && Objects.equals(businessType, that.businessType) && Objects.equals(sessionStartDate, that.sessionStartDate)
                && Objects.equals(sessionEndDate, that.sessionEndDate) && Objects.equals(isDraft, that.isDraft)
                && Objects.equals(pageSize, that.pageSize) && Objects.equals(pageNumber, that.pageNumber)
                && Objects.equals(isWeekCommencing, that.isWeekCommencing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtCentreId, courtRoomId, businessType, sessionStartDate, sessionEndDate, isDraft, pageSize, pageNumber, isWeekCommencing);
    }

    @Override
    public String courtCentreId() {
        return courtCentreId;
    }

    @Override
    public String courtRoomId() {
        return courtRoomId;
    }

    @Override
    public String businessType() {
        return businessType;
    }

    @Override
    public String sessionStartDate() {
        return sessionStartDate;
    }

    @Override
    public String sessionEndDate() {
        return sessionEndDate;
    }

    @Override
    public Boolean isDraft() {
        return isDraft;
    }

    @Override
    public String pageSize() {
        return pageSize;
    }

    @Override
    public String pageNumber() {
        return pageNumber;
    }
}
