package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.api.accesscontrol.PermissionConstants.*;
import static uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil.getPayload;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionConstantsTest {

    @Test
    void shouldCreateSchedulePermission() throws JsonProcessingException {
        final String expectedCreateCourtSchedulePermissionStr = getPayload("create-court-schedule-permission.json");
        final String createCourtSchedulePermissionStr = createCourtSchedulePermission();

        assertThat(createCourtSchedulePermissionStr, is(expectedCreateCourtSchedulePermissionStr.replaceAll("\n",  "")));
    }

    @Test
    void shouldGetSchedulePermission() throws JsonProcessingException {
        final String expectedCreateCourtSchedulePermissionStr = getPayload("get-court-schedule-permission.json");
        final String createCourtSchedulePermissionStr = getCourtSchedulePermission();

        assertThat(createCourtSchedulePermissionStr, is(expectedCreateCourtSchedulePermissionStr.replaceAll("\n",  "")));
    }

    @Test
    void shouldUpdateSchedulePermission() throws JsonProcessingException {
        final String expectedCreateCourtSchedulePermissionStr = getPayload("update-court-schedule-permission.json");
        final String createCourtSchedulePermissionStr = updateCourtSchedulePermission();

        assertThat(createCourtSchedulePermissionStr, is(expectedCreateCourtSchedulePermissionStr.replaceAll("\n",  "")));
    }

    @Test
    void shouldUpdateHearingSlotsPermission() throws JsonProcessingException {
        final String expectedUpdateHearingSlotsPermissionStr = getPayload("update-hearing-slots-permission.json");
        final String updateHearingSlotsPermissionStr = updateHearingSlotsPermission();

        assertThat(updateHearingSlotsPermissionStr, is(expectedUpdateHearingSlotsPermissionStr.replaceAll("\n",  "")));
    }

    @Test
    void shouldViewHearingSlotsPermission() throws JsonProcessingException {
        final String expectedUpdateHearingSlotsPermissionStr = getPayload("get-hearing-slots-permission.json");
        final String hearingSlotsPermission = getHearingSlotsPermission();

        assertThat(hearingSlotsPermission, is(expectedUpdateHearingSlotsPermissionStr.replaceAll("\n",  "")));
    }
}
