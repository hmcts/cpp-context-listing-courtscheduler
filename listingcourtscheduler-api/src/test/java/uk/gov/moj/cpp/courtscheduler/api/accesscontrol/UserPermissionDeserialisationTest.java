package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import uk.gov.moj.cpp.authz.http.dto.UserPermission;

/**
 * Diagnostic: cp-auth-rules-filter constructs a fresh {@code new ObjectMapper()} inside
 * {@code RequestUserAndGroupProvider} and uses it to deserialise the partial
 * {@code {"object":"…","action":"…"}} JSON returned by {@link PermissionConstants}.
 *
 * <p>If this test fails, the rule eval will silently see a UserPermission with null
 * object/action, getKey() will return "", and no user permission will ever match
 * — i.e. 403 on every protected endpoint regardless of stub casing.</p>
 */
class UserPermissionDeserialisationTest {

    @Test
    void defaultObjectMapperCanDeserialisePartialUserPermissionRecord() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final UserPermission perm = mapper.readValue(
                PermissionConstants.createCourtSchedulePermission(), UserPermission.class);
        assertThat(perm.object()).isEqualTo("CourtSchedule");
        assertThat(perm.action()).isEqualTo("Create");
        assertThat(perm.getKey()).isEqualTo("CourtSchedule_Create");
    }
}
