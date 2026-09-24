package uk.gov.moj.cpp.courtscheduler.persist.entity;

/**
 * Named-parameter keys ({@code :name}) bound by the court schedule repositories' JPQL/native queries.
 * Column names live in {@link CourtScheduleColumnNames}.
 */
public final class CourtScheduleQueryParameterNames {

    public static final String BUSINESS_TYPE = "businessType";
    public static final String COURT_ROOM_ID = "courtRoomId";
    public static final String OU_CODE = "ouCode";
    public static final String COURT_CENTRE_ID = "courtCentreId";
    public static final String COURT_SCHEDULE_IDS = "courtScheduleIds";
    public static final String SESSION_DATE = "sessionDate";
    public static final String START_DATE = "startDate";
    public static final String END_DATE = "endDate";

    private CourtScheduleQueryParameterNames() {
    }
}
