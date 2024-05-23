package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public record HearingSlotRequestParam(String panel, String sessionStartDate, String sessionEndDate,
                                      String oucodeL2Code, String ouCode, String pageSize,
                                      String pageNumber, String courtRoomId, String courtRoomNumber,
                                      String businessType, String courtSession) {
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final HearingSlotRequestParam that = (HearingSlotRequestParam) o;
        return Objects.equals(panel(), that.panel()) && Objects.equals(sessionStartDate(),
                that.sessionStartDate()) && Objects.equals(sessionEndDate(),
                that.sessionEndDate()) && Objects.equals(oucodeL2Code(),
                that.oucodeL2Code()) && Objects.equals(ouCode(),
                that.ouCode()) && Objects.equals(pageSize(),
                that.pageSize()) && Objects.equals(pageNumber(),
                that.pageNumber()) && Objects.equals(courtRoomId(),
                that.courtRoomId()) && Objects.equals(courtRoomNumber(),
                that.courtRoomNumber()) && Objects.equals(businessType(),
                that.businessType()) && Objects.equals(courtSession(),
                that.courtSession());
    }

    @Override
    public int hashCode() {
        return Objects.hash(panel(), sessionStartDate(), sessionEndDate(), oucodeL2Code(),
                ouCode(), pageSize(), pageNumber(), courtRoomId(), courtRoomNumber(),
                businessType(), courtSession());
    }
}
