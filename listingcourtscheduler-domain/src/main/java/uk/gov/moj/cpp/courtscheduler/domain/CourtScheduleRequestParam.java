package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public record CourtScheduleRequestParam(String courtCentreId,
                                        String courtRoomId,
                                        String businessType,
                                        String sessionStartDate,
                                        String sessionEndDate,
                                        String pageSize,
                                        String pageNumber
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CourtScheduleRequestParam that = (CourtScheduleRequestParam) o;
        return Objects.equals(courtCentreId, that.courtCentreId) && Objects.equals(courtRoomId, that.courtRoomId)
                && Objects.equals(businessType, that.businessType) && Objects.equals(sessionStartDate, that.sessionStartDate)
                && Objects.equals(sessionEndDate, that.sessionEndDate) && Objects.equals(pageSize, that.pageSize)
                && Objects.equals(pageNumber, that.pageNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtCentreId, courtRoomId, businessType, sessionStartDate, sessionEndDate, pageSize, pageNumber);
    }
}
