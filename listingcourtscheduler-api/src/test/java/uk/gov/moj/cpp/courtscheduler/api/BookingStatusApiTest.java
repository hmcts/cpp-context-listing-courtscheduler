package uk.gov.moj.cpp.courtscheduler.api;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.controllers.GlobalExceptionHandler;
import uk.gov.moj.cpp.courtscheduler.validation.RequestSchemaValidationFilter;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code POST /unconfirmedBooking/status} (LPT-2611) - the pre-share check, which with
 * {@code reserveIfExpired} also re-books an expired booking whose session still has room. A POST
 * because it can book capacity; the GET stays read-only. Driven through MockMvc with the real
 * {@link RequestSchemaValidationFilter} and {@link GlobalExceptionHandler}.
 */
// Deliberate: MockMvc andExpect(...) performs the assertions, which PMD does not recognise
@SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
class BookingStatusApiTest {

    private static final String PATH = "/unconfirmedBooking/status";
    private static final String STATUS_MT = "application/vnd.courtscheduler.query.booking.status+json";

    private final ProvisionalBookingService provisionalBookingService = mock(ProvisionalBookingService.class);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(api())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestSchemaValidationFilter(new ObjectMapper()))
            .build();

    @Test
    void theFlagIsPassedThroughAndTheStatusesComeBack() throws Exception {
        when(provisionalBookingService.getBookingStatus(List.of("bk-1"), true)).thenReturn(Json.createObjectBuilder()
                .add("bookings", Json.createArrayBuilder().add(Json.createObjectBuilder()
                        .add("bookingId", "bk-1").add("safeToShare", true).add("status", "REBOOKED")))
                .build());

        ask("{\"bookingIds\":[\"bk-1\"],\"reserveIfExpired\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[0].status").value("REBOOKED"))
                .andExpect(jsonPath("$.bookings[0].safeToShare").value(true));
    }

    @Test
    void withoutTheFlagItOnlyReads() throws Exception {
        when(provisionalBookingService.getBookingStatus(List.of("bk-1", "bk-2"), false)).thenReturn(Json.createObjectBuilder()
                .add("bookings", Json.createArrayBuilder()).build());

        ask("{\"bookingIds\":[\"bk-1\",\"bk-2\"]}")
                .andExpect(status().isOk());

        verify(provisionalBookingService).getBookingStatus(List.of("bk-1", "bk-2"), false);
    }

    // The POST is the only booking-status endpoint: the GET it replaced was never released, and its
    // only caller (listing) now posts.
    @Test
    void theGetFormIsGone() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(PATH)
                        .param("bookingIds", "bk-1")
                        .header("CJSCPPUID", "11111111-1111-1111-1111-111111111111"))
                // GlobalExceptionHandler answers an unsupported method with its generic error
                // status, so what is pinned is that the GET is not served.
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(200, result.getResponse().getStatus()));

        verify(provisionalBookingService, never()).getBookingStatus(any(List.class), anyBoolean());
    }

    @Test
    void anEmptyListIsRejected() throws Exception {
        assertRejectedBySchema("{\"bookingIds\":[]}");
    }

    @Test
    void aBlankIdIsRejected() throws Exception {
        assertRejectedBySchema("{\"bookingIds\":[\" \"]}");
    }

    @Test
    void aNonBooleanFlagIsRejected() throws Exception {
        assertRejectedBySchema("{\"bookingIds\":[\"bk-1\"],\"reserveIfExpired\":\"yes\"}");
    }

    private void assertRejectedBySchema(final String body) throws Exception {
        ask(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(startsWith("#")));

        verify(provisionalBookingService, never()).getBookingStatus(any(List.class), anyBoolean());
    }

    private ResultActions ask(final String body) throws Exception {
        return mvc.perform(post(PATH)
                .header("CJSCPPUID", "11111111-1111-1111-1111-111111111111")
                .contentType(STATUS_MT)
                .content(body));
    }

    private CourtSchedulerApi api() {
        // 20 args, positional: provisionalBookingService is 18th. Nothing else is reached.
        return new CourtSchedulerApi(new ObjectMapper(), mock(HttpServletRequest.class), null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                provisionalBookingService, null, null);
    }
}
