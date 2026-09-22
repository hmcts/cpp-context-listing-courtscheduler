package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.HashMap;
import java.util.Map;

public class Result {
    /* package */
    final String msg;
    /* package */
    final boolean successful;
    /* package */
    String courtRoomId;
    /* package */
    String courtRoomName;
    /* package */
    final Map<String, String> hearingDayCourtSchedules = new HashMap<>();

    public Result(final String msg, final boolean isSuccess, final String courtRoomId) {
        this.msg = msg;
        this.successful = isSuccess;
        this.courtRoomId = courtRoomId;
    }

    public Result(final String msg, final boolean isSuccess) {
        this.msg = msg;
        this.successful = isSuccess;
    }

    public static Result success() {
        return new Result("Success", true);
    }

    public static Result failed(final String msg) {
        return new Result(msg, false);
    }

    public boolean isSuccess() {
        return successful;
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

    public void addHearingDaySchedule(final String hearingDay, final String courtScheduleId) {
        hearingDayCourtSchedules.put(hearingDay, courtScheduleId);
    }
}
