package uk.gov.moj.cpp.courtscheduler.common.exception;

public class ErrorMessages {

    public static final String DUPLICATE_SESSIONS = "Session to be added has a duplicate";
    public static final String SESSION_EDIT_ANOTHER_USER = "This session is being edited by another user. Your changes cannot be saved so please try again later.";
    public static final String SESSION_NOT_FOUND = "Court Session not found";
    public static final String BUSINESS_TYPE_CHANGE_NOT_ALLOWED = "Business Type cannot be changed from Slot to Non-Slot and vice versa";
}
