package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

/**
 * Security group names used by the Drools rules in {@code uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl}.
 *
 * <p>cp-auth-rules-filter 2.0.0's {@code UserAndGroupProvider} no longer exposes
 * {@code isSystemUser(...)}. We model "system user" as membership of a dedicated
 * group ({@link #SYSTEM_USERS}) just like cp-court-list-publishing-service does
 * via {@code SecurityGroupConstants.getSystemUserOnlyRoles()}.</p>
 */
public final class SecurityGroupConstants {

    public static final String SYSTEM_USERS = "SYSTEM_USERS";

    private SecurityGroupConstants() { }

    public static String[] systemUserRoles() {
        return new String[] { SYSTEM_USERS };
    }
}
