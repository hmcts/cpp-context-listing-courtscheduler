package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.HashMap;
import java.util.Map;

public class Result {
    final String msg;
    final boolean success;
    String courtRoomId;
    String courtRoomName;
    final Map<String, String> hearingDayCourtSchedules = new HashMap<>();

    public Result(String msg, boolean isSuccess, String courtRoomId) {
        this.msg = msg;
        this.success = isSuccess;
        this.courtRoomId = courtRoomId;
    }

    public Result(String msg, boolean isSuccess) {
        this.msg = msg;
        this.success = isSuccess;
    }

    public static Result SUCCESS() {
        return new Result("Success", true);
    }

    public static Result FAILED(String msg) {
        return new Result(msg, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMsg() {return msg;}

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public String getCourtRoomName() {
        return courtRoomName;
    }

    public void setCourtRoomName(final String courtRoomName) {
        this.courtRoomName = courtRoomName;
    }

    public Map<String, String> getHearingDayCourtSchedules() {
        return hearingDayCourtSchedules;
    }

    public void addHearingDaySchedule(String hearingDay, String courtScheduleId) {
        hearingDayCourtSchedules.put(hearingDay, courtScheduleId);
    }
}
