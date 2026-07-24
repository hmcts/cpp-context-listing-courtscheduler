package uk.gov.moj.cpp.courtscheduler.exception;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

public class ExtendMultidayHearingException extends RuntimeException {

    public enum ErrorCode {
        NO_EXISTING_ALLOCATION,
        START_DATE_CHANGE_NOT_ALLOWED,
        NO_AVAILABILITY,
        INVALID_DATE_RANGE
    }

    private final ErrorCode errorCode;
    private final List<LocalDate> unavailableDates;

    public ExtendMultidayHearingException(final ErrorCode errorCode, final String message) {
        this(errorCode, message, Collections.emptyList());
    }

    public ExtendMultidayHearingException(final ErrorCode errorCode, final String message,
                                          final List<LocalDate> unavailableDates) {
        super(message);
        this.errorCode = errorCode;
        this.unavailableDates = unavailableDates == null ? Collections.emptyList() : List.copyOf(unavailableDates);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<LocalDate> getUnavailableDates() {
        return unavailableDates;
    }
}
