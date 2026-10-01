package uk.gov.moj.cpp.courtscheduler.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import uk.gov.moj.cpp.courtscheduler.exception.ConfirmedBookingExistsException;
import uk.gov.moj.cpp.courtscheduler.exception.NoCapacityException;
import uk.gov.moj.cpp.courtscheduler.exception.NoSessionAvailableException;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reservation exceptions are deliberate refusals, not server faults. Unmapped they fell
 * through to the catch-all as {@code 500 {"error":"Internal Server Error"}}, and that discarded
 * the only thing that explained the failure: hearing logged a bare 500, the public event carried
 * no reason, and a clerk amending an already-shared result was told the session was "fully
 * booked" when it was free.
 *
 * <p>Each must carry its own message and a status that says what kind of refusal it is.
 */
class ReservationErrorMappingTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingEndpoint())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void confirmedBookingExists_returns409_withItsOwnMessage() throws Exception {
        mvc.perform(get("/throw/confirmed"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("already has a confirmed allocation"));
    }

    @Test
    void noSessionAvailable_returns422_withItsOwnMessage() throws Exception {
        mvc.perform(get("/throw/no-session"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("No session found for sessionId abc"));
    }

    @Test
    void noCapacity_returns409_withItsOwnMessage() throws Exception {
        mvc.perform(get("/throw/no-capacity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("no capacity"));
    }

    @RestController
    static class ThrowingEndpoint {
        @GetMapping("/throw/confirmed")
        String confirmed() {
            throw new ConfirmedBookingExistsException("already has a confirmed allocation");
        }

        @GetMapping("/throw/no-session")
        String noSession() {
            throw new NoSessionAvailableException("No session found for sessionId abc");
        }

        @GetMapping("/throw/no-capacity")
        String noCapacity() {
            throw new NoCapacityException("no capacity");
        }
    }
}
