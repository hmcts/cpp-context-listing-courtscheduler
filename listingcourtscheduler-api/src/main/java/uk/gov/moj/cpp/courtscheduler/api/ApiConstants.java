package uk.gov.moj.cpp.courtscheduler.api;

public class ApiConstants {
    private ApiConstants() {}
    public static final String START_DATE_IS_IN_BAD_FORMAT = "Start Date: %s is in bad format";
    public static final String START_DATE_IS_INVALID = "Start Date: %s is invalid";
    public static final String END_DATE_IS_IN_BAD_FORMAT = "End Date: %s is in bad format";
    public static final String START_DATE_AFTER_END_DATE = "Start date must be on or before end date";
    public static final String EXACT_HEARING_START_DATETIME_IS_IN_BAD_FORMAT = "Exact Hearing Start DateTime: %s is in bad format";
    public static final String MANDATORY_SEARCH_CRITERIA = "Mandatory Search Criteria ";
    public static final String CANNOT_BE_NULL = " cannot be null";
    public static final String ERROR_MESSAGE = "errorMessage";
    public static final String MANDATORY_DATA_MISSING = "Provisional slot missing one of the following mandatory information (courtScheduleId)";
    public static final String PAYLOAD_NOT_CORRECT = "Request body payload is incorrect";
    public static final String PAYLOAD_CANNOT_EMPTY = "Request body cannot be empty";
    public static final String BOOKING_IDS = "bookingIds";
    public static final String ERROR = "error";
}
