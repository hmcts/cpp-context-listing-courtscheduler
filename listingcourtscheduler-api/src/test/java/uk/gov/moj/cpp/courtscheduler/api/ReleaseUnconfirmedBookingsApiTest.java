package uk.gov.moj.cpp.courtscheduler.api;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.controllers.GlobalExceptionHandler;
import uk.gov.moj.cpp.courtscheduler.validation.RequestSchemaValidationFilter;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code POST /unconfirmedBooking/release} - releases the unconfirmed holds of the given booking
 * ids. The contract is fixed (hearing calls it on draft save): 202 with no body on success, 400 in
 * the project's {@code {"error": "..."}} shape for a missing or empty {@code bookingIds} or a blank
 * entry, and the service is never reached on a 400.
 *
 * <p>Driven through MockMvc with the real {@link RequestSchemaValidationFilter} and
 * {@link GlobalExceptionHandler}, as in production. The nested class drops the filter to prove
 * the controller does not depend on it to refuse a bad body.
 */
// Deliberate: MockMvc andExpect(...) performs the assertions, which PMD does not recognise
@SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
class ReleaseUnconfirmedBookingsApiTest {

    private static final String PATH = "/unconfirmedBooking/release";
    private static final String RELEASE_MT = "application/vnd.courtscheduler.release.unconfirmed.bookings+json";
    private static final String USER_ID_HEADER = "CJSCPPUID";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    private final ProvisionalBookingService provisionalBookingService = mock(ProvisionalBookingService.class);

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(api())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestSchemaValidationFilter(new ObjectMapper()))
            .build();

    @Test
    void validBody_isAccepted_withNoBody_andReleasesTheBookings() throws Exception {
        release(mvc, "{\"bookingIds\":[\"bk-1\",\"bk-2\"]}")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        verify(provisionalBookingService).releaseUnconfirmedBookings(List.of("bk-1", "bk-2"), null);
    }

    // LPT-2611: callers say why, so the booking log can tell a dropped line from a surplus hold.
    @Test
    void reason_isPassedThrough() throws Exception {
        release(mvc, "{\"bookingIds\":[\"bk-1\"],\"reason\":\"SURPLUS\"}")
                .andExpect(status().isAccepted());

        verify(provisionalBookingService).releaseUnconfirmedBookings(List.of("bk-1"), "SURPLUS");
    }

    @Test
    void unknownReason_isRejectedWith400() throws Exception {
        assertRejectedBySchema("{\"bookingIds\":[\"bk-1\"],\"reason\":\"BOGUS\"}");
    }

    @Test
    void duplicateIds_areAccepted() throws Exception {
        release(mvc, "{\"bookingIds\":[\"bk-1\",\"bk-1\"]}")
                .andExpect(status().isAccepted());
    }

    @Test
    void emptyList_isRejectedWith400() throws Exception {
        assertRejectedBySchema("{\"bookingIds\":[]}");
    }

    @Test
    void blankId_isRejectedWith400() throws Exception {
        assertRejectedBySchema("{\"bookingIds\":[\"bk-1\",\"   \"]}");
    }

    @Test
    void emptyStringId_isRejectedWith400() throws Exception {
        assertRejectedBySchema("{\"bookingIds\":[\"\"]}");
    }

    @Test
    void missingBookingIds_isRejectedWith400() throws Exception {
        assertRejectedBySchema("{}");
    }

    @Test
    void nullBookingIds_isRejectedWith400() throws Exception {
        assertRejectedBySchema("{\"bookingIds\":null}");
    }

    @Test
    void emptyBody_isRejectedWith400() throws Exception {
        release(mvc, "")
                .andExpect(status().isBadRequest());

        verify(provisionalBookingService, never()).releaseUnconfirmedBookings(any(), any());
    }

    /**
     * The schema filter skips a media type whose schema it cannot load, and passes an empty body
     * straight through. The controller must refuse a bad body on its own in that case rather
     * than hand a blank id to the release.
     */
    @Nested
    class WithoutTheSchemaFilter {

        private final MockMvc bareMvc = MockMvcBuilders.standaloneSetup(api())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        @Test
        void validBody_isAccepted() throws Exception {
            release(bareMvc, "{\"bookingIds\":[\"bk-1\"]}")
                    .andExpect(status().isAccepted());

            verify(provisionalBookingService).releaseUnconfirmedBookings(List.of("bk-1"), null);
        }

        @Test
        void emptyList_isRejectedWith400() throws Exception {
            assertRejected(bareMvc, "{\"bookingIds\":[]}");
        }

        @Test
        void blankId_isRejectedWith400() throws Exception {
            assertRejected(bareMvc, "{\"bookingIds\":[\"   \"]}");
        }

        @Test
        void nullId_isRejectedWith400() throws Exception {
            assertRejected(bareMvc, "{\"bookingIds\":[null]}");
        }

        @Test
        void missingBookingIds_isRejectedWith400() throws Exception {
            assertRejected(bareMvc, "{}");
        }
    }

    /**
     * Rejected by the JSON schema in the filter, before the controller runs. everit's messages
     * are JSON-pointer prefixed ("#: required key [bookingIds] not found", "#/bookingIds: ..."),
     * which the controller's own message is not - so this also proves the schema is registered
     * for the media type, not merely that something answered 400.
     */
    private void assertRejectedBySchema(final String body) throws Exception {
        release(mvc, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(startsWith("#")));

        verify(provisionalBookingService, never()).releaseUnconfirmedBookings(any(), any());
    }

    private void assertRejected(final MockMvc target, final String body) throws Exception {
        release(target, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        verify(provisionalBookingService, never()).releaseUnconfirmedBookings(any(), any());
    }

    private static ResultActions release(final MockMvc target, final String body) throws Exception {
        return target.perform(post(PATH)
                .header(USER_ID_HEADER, USER_ID)
                .contentType(RELEASE_MT)
                .content(body));
    }

    private CourtSchedulerApi api() {
        // 20 args, positional: provisionalBookingService is 18th. Nothing else is reached.
        return new CourtSchedulerApi(new ObjectMapper(), mock(HttpServletRequest.class), null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                provisionalBookingService, null, null);
    }
}
