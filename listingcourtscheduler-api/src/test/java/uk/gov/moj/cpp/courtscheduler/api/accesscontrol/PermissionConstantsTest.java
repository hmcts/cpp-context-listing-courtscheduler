package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.api.accesscontrol.PermissionConstants.createCourtSchedulePermission;
import static uk.gov.moj.cpp.courtscheduler.api.accesscontrol.PermissionConstants.getCourtSchedulePermission;
import static uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil.getPayload;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionConstantsTest {

    @Test
    void shouldCreateSchedulePermission() throws JsonProcessingException {
        assertThat(createCourtSchedulePermission(),
                is(getPayload("create-court-schedule-permission.json").replaceAll("\n", "")));
    }

    @Test
    void shouldGetSchedulePermission() throws JsonProcessingException {
        assertThat(getCourtSchedulePermission(),
                is(getPayload("get-court-schedule-permission.json").replaceAll("\n", "")));
    }
}
