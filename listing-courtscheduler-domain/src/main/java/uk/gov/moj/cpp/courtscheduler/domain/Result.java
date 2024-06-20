package uk.gov.moj.cpp.courtscheduler.domain;

public class Result {
    final String msg;
    final boolean success;

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
}
