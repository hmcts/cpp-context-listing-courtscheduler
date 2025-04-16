package uk.gov.moj.cpp.courtscheduler.domain;

public class SessionValidationParams {
    private String courtScheduleId;
    private Integer maxDurationForMorning;
    private Integer maxDurationForAfternoon;
    private Boolean isAllDaySplit;
    private String sessionType;
    private String businessType;
    private Integer slotsOrDuration;

    public SessionValidationParams(Integer maxDurationForMorning,
                                   Integer maxDurationForAfternoon,
                                   Boolean isAllDaySplit,
                                   String sessionType,
                                   String businessType,
                                   final Integer slotsOrDuration,
                                   final String courtScheduleId) {
        this.maxDurationForMorning = maxDurationForMorning;
        this.maxDurationForAfternoon = maxDurationForAfternoon;
        this.isAllDaySplit = isAllDaySplit;
        this.sessionType = sessionType;
        this.businessType = businessType;
        this.slotsOrDuration = slotsOrDuration;
        this.courtScheduleId = courtScheduleId;
    }

    // Getters and setters
    public Integer getMaxDurationForMorning() {
        return maxDurationForMorning;
    }

    public void setMaxDurationForMorning(Integer maxDurationForMorning) {
        this.maxDurationForMorning = maxDurationForMorning;
    }

    public Integer getSlotsOrDuration() {
        return slotsOrDuration;
    }

    public void setSlotsOrDuration(Integer slotsOrDuration) {
        this.slotsOrDuration = slotsOrDuration;
    }

    public Integer getMaxDurationForAfternoon() {
        return maxDurationForAfternoon;
    }

    public void setMaxDurationForAfternoon(Integer maxDurationForAfternoon) {
        this.maxDurationForAfternoon = maxDurationForAfternoon;
    }

    public Boolean isAllDaySplit() {
        return isAllDaySplit;
    }

    public void setAllDaySplit(Boolean allDaySplit) {
        isAllDaySplit = allDaySplit;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }
}