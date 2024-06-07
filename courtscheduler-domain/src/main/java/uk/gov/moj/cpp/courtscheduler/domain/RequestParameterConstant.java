package uk.gov.moj.cpp.courtscheduler.domain;

public enum RequestParameterConstant {


    PANEL("panel"),
    COURT_SESSION("courtSession"),
    OU_LEVEL2("oucodeL2Code"),
    OU_CODE("ouCode"),
    COURT_ROOM("courtRoomId"),
    COURT_ROOM_NUMBER("courtRoomNumber"),
    START_DATE("sessionStartDate"),
    END_DATE("sessionEndDate"),
    BUSINESS_TYPE("businessType"),
    PAGE_SIZE("pageSize"),
    RESULTS("results"),
    PAGE_COUNT("pageCount"),
    HEARING_SLOTS("hearingSlots"),
    SESSIONS("sessions"),
    PROVISIONAL_SLOTS("provisionalSlots"),
    PAGE_NUMBER("pageNumber"),
    SLOT_DETAILS("slotDetails"),
    BOOKING_IDS("bookingIds"),

    FROM_DATE("fromDate"),
    TO_DATE("toDate");

    private final String name;

    RequestParameterConstant(final String name) {
        this.name = name;
    }

    public String getLabel() {
        return name;
    }
}