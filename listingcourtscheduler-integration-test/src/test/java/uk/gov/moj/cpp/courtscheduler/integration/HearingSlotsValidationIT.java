package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;

import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Integration tests for GET hearing slots and MAGS search-and-book request validations.
 * Verifies that invalid or missing parameters result in 400 Bad Request
 * with the expected validation error messages.
 */
class HearingSlotsValidationIT extends AbstractIT {

    private static final String HEARING_SLOTS_URL = "/hearingslots";
    private static final String GET_HEARING_SLOTS_ACCEPT = "application/vnd.courtscheduler.get.hearing.slots+json";
    private static final String MAGS_ACCEPT = "application/vnd.courtscheduler.mags.search.and.book+json";

    /** Minimal valid query params for GET hearing slots (panel, dates, ouCode, pageSize, pageNumber). */
    private static Map<String, Object> validGetHearingSlotsParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("panel", "ADULT");
        params.put("sessionStartDate", "2025-07-01");
        params.put("sessionEndDate", "2025-07-31");
        params.put("ouCode", "B40IM00");
        params.put("pageSize", "10");
        params.put("pageNumber", "1");
        params.put("jurisdiction", "CROWN");
        return params;
    }

    private ResponseData getHearingSlots(Map<String, Object> queryParams) {
        RequestParams requestParams = getRequestParams(HEARING_SLOTS_URL, GET_HEARING_SLOTS_ACCEPT, SYSTEM_USER_ID, queryParams);
        return poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();
    }

    private static Stream<Arguments> requiredGetHearingSlotsParams() {
        return Stream.of(
                Arguments.of("panel"),
                Arguments.of("sessionStartDate"),
                Arguments.of("sessionEndDate"),
                Arguments.of("pageSize"),
                Arguments.of("pageNumber")
        );
    }

    @ParameterizedTest(name = "shouldReturn400 when {0} is missing")
    @MethodSource("requiredGetHearingSlotsParams")
    void shouldReturn400WhenRequiredParamIsMissing(String paramName) {
        Map<String, Object> params = validGetHearingSlotsParams();
        params.remove(paramName);
        ResponseData response = getHearingSlots(params);
        assertThat(response.getStatus().getStatusCode(), is(BAD_REQUEST.getStatusCode()));
        assertThat(response.getPayload(), containsString(paramName));
        // Spring's missing-required-parameter message ("Required request parameter 'X' ... is not
        // present") replaces the legacy framework's "has no value" text; same 400 semantics.
        assertThat(response.getPayload(), containsString("is not present"));
    }

    @Test
    void shouldReturnErrorWhenJurisdictionIsInvalid() {
        Map<String, Object> params = validGetHearingSlotsParams();
        params.put("jurisdiction", "INVALID");
        ResponseData response = getHearingSlots(params);
        assertThat(response.getPayload(), containsString("Invalid jurisdiction value"));
    }

    @Test
    void shouldReturn400WhenSearchAndBookCourtCentreIdIsMissing() {
        // courtCentreId is required in the request body; hearingId travels in the URL path only
        final String body = "{\"hearingDate\":\"2025-05-13\",\"durationInMinutes\":30}";
        final Response response = postCommand("/hearings/5771a96b-1c5a-45d1-b647-1bec5212cafc", MAGS_ACCEPT, SYSTEM_USER_ID, body);
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(response.readEntity(String.class), containsString("courtCentreId"));
    }

    @Test
    void shouldReturn400WhenSearchAndBookHearingDateIsMissing() {
        // hearingDate is required in the request body; hearingId travels in the URL path only
        final String body = "{\"courtCentreId\":\"785339c1-af71-3322-a55b-ba255e0db1c2\",\"durationInMinutes\":30}";
        final Response response = postCommand("/hearings/5771a96b-1c5a-45d1-b647-1bec5212cafc", MAGS_ACCEPT, SYSTEM_USER_ID, body);
        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat(response.readEntity(String.class), containsString("hearingDate"));
    }
}
