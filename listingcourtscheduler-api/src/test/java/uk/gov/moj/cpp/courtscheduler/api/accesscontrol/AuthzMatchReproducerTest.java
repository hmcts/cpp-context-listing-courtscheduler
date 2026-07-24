package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import uk.gov.moj.cpp.authz.drools.Action;
import uk.gov.moj.cpp.authz.http.AuthzPrincipal;
import uk.gov.moj.cpp.authz.http.dto.UserPermission;
import uk.gov.moj.cpp.authz.http.providers.RequestUserAndGroupProvider;

/**
 * End-to-end reproducer of the auth-filter rule eval against the WireMock stub.
 * If this returns false, that's why every protected IT is 403.
 */
class AuthzMatchReproducerTest {

    @Test
    void hasPermissionShouldMatchStubbedUserAgainstPermissionConstants() {
        // Stubbed user permissions exactly as identity-court-schedule-user.json returns them after the casing fix
        final List<UserPermission> userPermissions = List.of(
                new UserPermission("p-1", "CourtSchedule", "Create", ""),
                new UserPermission("p-2", "CourtSchedule", "View", ""));
        final AuthzPrincipal principal = new AuthzPrincipal(
                "11111111-1111-1111-1111-111111111111", null, null, null, java.util.Set.of(), userPermissions);

        final RequestUserAndGroupProvider provider =
                new RequestUserAndGroupProvider(principal, new ObjectMapper());

        final Action action = new Action("courtscheduler.validate.create", java.util.Map.of());

        final boolean canCreate = provider.hasPermission(action, PermissionConstants.createCourtSchedulePermission());
        final boolean canView = provider.hasPermission(action, PermissionConstants.getCourtSchedulePermission());

        assertThat(canCreate).as("user has CourtSchedule_Create").isTrue();
        assertThat(canView).as("user has CourtSchedule_View").isTrue();
    }
}
