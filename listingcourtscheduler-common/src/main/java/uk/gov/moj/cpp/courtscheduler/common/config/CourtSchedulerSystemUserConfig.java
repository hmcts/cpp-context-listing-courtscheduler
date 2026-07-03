package uk.gov.moj.cpp.courtscheduler.common.config;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds the system-user UUID used as {@code CJSCPPUID} on outbound HTTP calls
 * to other CPP context-services. Configured via {@code courtscheduler.system-user-id}
 * (env: {@code COURTSCHEDULER_SYSTEM_USER_ID}).
 *
 * <p>Replaces the Justice Services framework's {@code Requester#requestAsAdmin(...)}
 * which embedded an "admin" user id internally. Mirrors
 * {@code CourtListPublishingSystemUserConfig} from cp-court-list-publishing-service.</p>
 */
@Component
public class CourtSchedulerSystemUserConfig {

    private final String systemUserId;

    public CourtSchedulerSystemUserConfig(
            @Value("${courtscheduler.system-user-id:}") final String systemUserId) {
        if (systemUserId != null && !systemUserId.isBlank()) {
            this.systemUserId = validateAndReturn(systemUserId);
        } else {
            this.systemUserId = null;
        }
    }

    private static String validateAndReturn(final String value) {
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "COURTSCHEDULER_SYSTEM_USER_ID must be a valid UUID: " + value, e);
        }
    }

    /** UUID string for {@code CJSCPPUID} header, or {@code null} if not configured. */
    public String getSystemUserId() {
        return systemUserId;
    }

    public String getRequiredSystemUserId() {
        if (systemUserId == null || systemUserId.isBlank()) {
            throw new IllegalStateException("COURTSCHEDULER_SYSTEM_USER_ID is not configured");
        }
        return systemUserId;
    }
}
