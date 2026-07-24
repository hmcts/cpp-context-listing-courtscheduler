package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.api.accesscontrol.PermissionConstants.createCourtSchedulePermission;
import static uk.gov.moj.cpp.courtscheduler.api.accesscontrol.PermissionConstants.getCourtSchedulePermission;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionConstantsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldCreateSchedulePermission() throws JsonProcessingException {
        JsonNode actual = mapper.readTree(createCourtSchedulePermission());
        JsonNode expected = mapper.readTree(getPayload("create-court-schedule-permission.json"));
        assertThat(actual, is(expected));
    }

    @Test
    void shouldGetSchedulePermission() throws JsonProcessingException {
        JsonNode actual = mapper.readTree(getCourtSchedulePermission());
        JsonNode expected = mapper.readTree(getPayload("get-court-schedule-permission.json"));
        assertThat(actual, is(expected));
    }
}
