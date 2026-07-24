package uk.gov.moj.cpp.courtscheduler.api;

public class JudiciaryAvailabilityValidationMessages {
    private JudiciaryAvailabilityValidationMessages() {}

    // Mandatory field validation messages
    public static final String SELECT_JUDICIARY = "Select a judiciary";
    public static final String SELECT_COURTHOUSE = "Select a courthouse";
    public static final String ENTER_START_DATE = "Enter a start date";
    public static final String ENTER_END_DATE = "Enter an end date";
    public static final String SELECT_REPEAT_DAYS = "Select the days you want to repeat";
    public static final String SELECT_DAY_OF_WEEK = "Select a day of the week";

    // Date validation messages
    public static final String START_DATE_MUST_BE_BEFORE_OR_EQUAL_TO_END_DATE = "The start date must be the same as or before the end date";
    public static final String DATE_RANGE_MUST_BE_3_YEARS_OR_LESS = "The date range must be 3 years or less";
    public static final String START_DATE_MUST_BE_IN_FUTURE = "The start date must be in the future";
    public static final String END_DATE_MUST_BE_IN_FUTURE = "The end date must be in the future";
    public static final String NEW_START_DATE_MUST_BE_IN_FUTURE = "The new start date must be in the future";
    public static final String NEW_END_DATE_MUST_BE_IN_FUTURE = "The new end date must be in the future";

    // Unavailability validation messages
    public static final String UNAVAILABILITY_START_DATE_MUST_BE_BETWEEN = "Unavailability %s start date must be between %s and %s";
    public static final String UNAVAILABILITY_END_DATE_MUST_BE_BETWEEN = "Unavailability %s end date must be between %s and %s";
    public static final String UNAVAILABILITY_DATES_CANNOT_OVERLAP = "Unavailability dates cannot overlap";
    public static final String ADDING_UNAVAILABILITY_WOULD_AFFECT_SESSIONS = "Adding unavailability from %s to %s would affect %s already assigned session(s). Review the assigned sessions before you continue";

    // Overlapping rules validation messages
    public static final String JUDICIARY_ALREADY_ASSIGNED_DURING_DATES = "The judiciary is already assigned during these dates";

    // Rule ID validation messages
    public static final String RULE_ID_REQUIRED = "Rule ID is required";
    public static final String RULE_ID_REQUIRED_FOR_UPDATE = "Rule ID is required for update";
    public static final String RULE_NOT_FOUND = "Judicial itinerary does not exist.";

    // Date change validation messages
    public static final String CHANGING_START_DATE_AFFECTS_SESSIONS = "Changing the start date affects %s sessions already assigned between %s and %s. Review these sessions before you continue.";
    public static final String CHANGING_END_DATE_FROM_TO_WOULD_AFFECT = "Changing end date from %s to %s would affect %s already assigned session(s) in the removed date range. Please review the assigned sessions before proceeding.";

    // Delete validation messages
    public static final String CANNOT_DELETE_ITINERARY_IN_USE = "You cannot delete this itinerary because it is being used in a session. You must remove the session before you can delete it.";
}
