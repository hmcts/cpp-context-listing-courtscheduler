package uk.gov.moj.cpp.courtscheduler.common.exception;

public class ErrorMessages {

    private ErrorMessages() {
    }

    public static final String DUPLICATE_SESSIONS = "Session to be added has a duplicate";
    public static final String SESSION_EDIT_ANOTHER_USER = "This session is being edited by another user. Your changes cannot be saved so please try again later.";
    public static final String SESSION_NOT_FOUND = "Court Session not found";
    public static final String BUSINESS_TYPE_CHANGE_NOT_ALLOWED = "Business Type cannot be changed from Slot to Non-Slot and vice versa";
    public static final String SPLIT_ONLY_APPLIES_AD_SESSIONS = "Session should be all day(AD) session for all day split to be applied";
    public static final String SPLIT_ONLY_APPLIES_DURATION_BASED_SESSION = "Session should be duration based for all day split to be applied";
    public static final String MAX_DURATION_AM_PM_PROVIDED_FOR_ALL_DAY_SPLIT_SESSION = "Maximum duration for morning and afternoon should be provided for all day split session";
    public static final String DURATION_NOT_FOUND_FOR_REGULAR_SESSION = "Duration should be set for this session";
    public static final String ALL_DAY_SPLIT_MANDATORY_FOR_AD_SESSION = "All day split flag should be sent for All Day(AD) session";
    public static final String ALL_DAY_SPLIT_CHANGE_NOT_ALLOWED = "All day split flag cannot be changed for this session";
    public static final String BUSINESS_TYPE_NOT_FOUND = "Business Type not found";
    public static final String COURTROOM_NOT_FOUND = "Court Room not found";
    public static final String AM_SESSION_END_TIME_CANNOT_EXCEED = "AM Session End Time cannot exceed 13:00";
    public static final String SESSION_START_TIME_CANNOT_BE_EARLIER = "%s Session Start Time cannot be earlier than 01:00";
    public static final String PM_SESSION_START_TIME_CANNOT_BE_EARLIER = "PM Session Start Time cannot be earlier than 14:00";
    public static final String SESSION_END_TIME_CANNOT_BE_LATER = "%s Session End Time cannot be later than 23:00";
    public static final String SESSION_START_TIME_CANNOT_BE_LATER_THAN_END_TIME = "Session Start Time cannot be later than Session End Time";
    public static final String MAX_DURATION_LESS_THAN_TOTAL_BOOKED = "Maximum duration cannot be less than total booked duration";
    public static final String MAX_DURATION_FOR_MORNING_LESS_THAN_TOTAL_BOOKED_FOR_MORNING = "Maximum duration for morning cannot be less than total booked duration for morning";
    public static final String MAX_DURATION_FOR_AFTERNOON_LESS_THAN_TOTAL_BOOKED_FOR_AFTERNOON = "Maximum duration for afternoon cannot be less than total booked duration for afternoon";
    public static final String MIN_HEARING_TIME_AFTER_SESSION_START_TIME = "Session Start Time can not be updated to a time that is later than the minimum hearing time";
    public static final String MAX_HEARING_TIME_BEFORE_SESSION_END_TIME = "Session End Time can not be updated to a time that is before than the maximum hearing time";
    public static final String SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME = MIN_HEARING_TIME_AFTER_SESSION_START_TIME;
    public static final String SESSION_END_TIME_CANNOT_BE_CHANGED_TO_BEFORE_HEARING_TIME = "Session End Time can not be updated to a time that is before than the maximum hearing time";
    public static final String SESSION_IN_PAST_CANNOT_BE_EDITED = "Cannot edit a session that is in the past";
}
