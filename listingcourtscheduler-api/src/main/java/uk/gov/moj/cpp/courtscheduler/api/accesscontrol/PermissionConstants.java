package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

/**
 * Permission JSON literals consumed by the Drools rules in
 * {@code uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl}.
 *
 * <p>cp-auth-rules-filter 2.0.0's {@code RequestUserAndGroupProvider#hasPermission}
 * deserialises each expected-permission string with Jackson into a {@code UserPermission}
 * record (fields {@code permissionId}, {@code object}, {@code action}, {@code description})
 * and matches by {@code UserPermission.getKey()} = {@code "<object>_<action>"}.</p>
 *
 * <p>The constants below therefore return JSON strings rather than the legacy
 * {@code ExpectedPermission} builder shape.</p>
 */
public final class PermissionConstants {

    public static final String CREATE_COURT_SCHEDULE_JSON =
            "{\"object\":\"CourtSchedule\",\"action\":\"Create\"}";

    public static final String GET_COURT_SCHEDULE_JSON =
            "{\"object\":\"CourtSchedule\",\"action\":\"View\"}";

    private PermissionConstants() { }

    public static String createCourtSchedulePermission() {
        return CREATE_COURT_SCHEDULE_JSON;
    }

    public static String getCourtSchedulePermission() {
        return GET_COURT_SCHEDULE_JSON;
    }
}
