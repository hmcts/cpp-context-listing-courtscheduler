package uk.gov.moj.cpp.courtscheduler.domain;

public enum RequestParameterConstant {

    PANEL("panel"),
    COURT_SESSION("courtSession"),
    COURT_SCHEDULE_ID("courtScheduleId"),
    COURT_CENTRE_ID("courtCentreId"),
    OU_LEVEL2("oucodeL2Code"),
    OU_CODE("ouCode"),
    COURT_ROOM("courtRoomId"),
    COURT_CENTRE("courtCentreId"),
    COURT_ROOM_NUMBER("courtRoomNumber"),
    SESSION_START_DATE("sessionStartDate"),
    SESSION_END_DATE("sessionEndDate"),
    EXACT_HEARING_START_DATETIME("exactHearingStartDateTime"),
    BUSINESS_TYPE("businessType"),
    PAGE_SIZE("pageSize"),
    SHOW_OVERBOOKING_SLOTS("showOverbookedSlots"),
    CASE_IDENTIFIER("caseIdentifier"),
    RESULTS("results"),
    PAGE_COUNT("pageCount"),
    HEARING_SLOTS("hearingSlots"),
    SESSIONS("sessions"),
    PROVISIONAL_SLOTS("provisionalSlots"),
    PAGE_NUMBER("pageNumber"),
    SLOT_DETAILS("slotDetails"),
    BOOKING_IDS("bookingIds"),
    FROM_DATE("fromDate"),
    TO_DATE("toDate"),
    SESSION_TYPE("sessionType"),
    DURATION("duration"),
    REPEAT_DAYS("repeatDays"),
    REPEAT_PATTERN("repeatPattern"),
    REPEAT_FREQUENCY("frequency"),
    REPEAT_FOR("repeatFor"),
    START_DATE("startDate"),
    END_DATE("endDate"),
    SESSION_TO_BE_ADDED("sessionToBeAdded"),
    HEARING_IDS("hearingIds"),
    ALL_DAY_SPLIT("allDaySplit"),
    MAX_DURATION_FOR_MORNING("maxDurationForMorning"),
    MAX_DURATION_FOR_AFTERNOON("maxDurationForAfternoon"),
    SESSION_START_TIME("sessionStartTime"),
    SESSION_END_TIME("sessionEndTime"),
    IS_OVERBOOKING_ALLOWED("isOverbookingAllowed"),
    HEARING_ID("hearingId"),
    HEARING_SESSION_DATE("hearingSessionDate"),
    HEARING_SESSION_DATE_CUT_OFF("hearingSessionDateSearchCutOff"),
    DURATION_MINUTES("durationInMinutes"),
    COURT_SCHEDULES("courtSchedules"),
    HEARINGS("hearings"),
    COURT_SCHEDULE_ID_LIST("courtScheduleIdList"),
    IS_SLOT_BASED("isSlotBased"),
    IS_POLICE("isPolice"),
    HEARING_DATE("hearingDate"),
    HEARING_START_TIME("hearingStartTime"),
    IS_DRAFT("isDraft"),
    JURISDICTION("jurisdiction"),
    INDEX("index"),
    COURT_SCHEDULE_IDS("courtScheduleIds"),

    ;

    private final String name;

    RequestParameterConstant(final String name) {
        this.name = name;
    }

    public String getLabel() {
        return name;
    }
}