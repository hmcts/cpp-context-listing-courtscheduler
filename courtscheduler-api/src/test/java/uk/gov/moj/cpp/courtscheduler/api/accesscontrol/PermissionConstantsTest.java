package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.api.accesscontrol.PermissionConstants.*;
import static uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil.getPayload;

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


    @Test
    void shouldUpdateSchedulePermission() throws JsonProcessingException {
        assertThat(updateCourtSchedulePermission(),
                is(getPayload("update-court-schedule-permission.json").replaceAll("\n", "")));
    }

    @Test
    void shouldUpdateHearingSlotsPermission() throws JsonProcessingException {
        assertThat(updateHearingSlotsPermission(),
                is(getPayload("update-hearing-slots-permission.json").replaceAll("\n", "")));
    }

    @Test
    void shouldViewHearingSlotsPermission() throws JsonProcessingException {
        assertThat(getHearingSlotsPermission(),
                is(getPayload("get-hearing-slots-permission.json").replaceAll("\n", "")));
    }

    @Test
    void shouldCreateProvisionalBookingPermission() throws JsonProcessingException {
        assertThat(createProvisionalBookingPermission(),
                is(getPayload("create-provisional-booking-permission.json").replaceAll("\n",  "")));
    }

    @Test
    void shouldExportCourtSchedulesPermission() throws JsonProcessingException {
        assertThat(exportCourtSchedulesPermission(),
                is(getPayload("export-court-schedules-permission.json").replaceAll("\n", "")));
    }

    @Test
    void shouldExportCourtScheduleJudiciariesPermission() throws JsonProcessingException {
        assertThat(exportCourtScheduleJudiciariesPermission(),
                is(getPayload("export-court-schedule-judiciaries-permission.json").replaceAll("\n", "")));
    }

    @Test
    void shouldExportAllocatedListingsPermission() throws JsonProcessingException {
        assertThat(exportAllocatedListingsPermission(),
                is(getPayload("export-allocated-listings-permission.json").replaceAll("\n", "")));
    }
}
