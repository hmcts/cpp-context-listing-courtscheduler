package uk.gov.moj.cpp.courtscheduler.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A request whose {@code Accept} matches no producible media type must return 406 (as WildFly /
 * JAX-RS did), not 500 — otherwise the catch-all handler would map the content-negotiation failure
 * to a server error, a client-visible regression.
 */
class ContentNegotiationErrorTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new VendorEndpoint())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void mismatchedAccept_returns406NotAcceptable_notServerError() throws Exception {
        mvc.perform(get("/vendor").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void matchingVendorAccept_succeeds() throws Exception {
        mvc.perform(get("/vendor").accept(MediaType.parseMediaType("application/vnd.courtscheduler.get+json")))
                .andExpect(status().isOk());
    }

    @RestController
    static class VendorEndpoint {
        @GetMapping(path = "/vendor", produces = "application/vnd.courtscheduler.get+json")
        ResponseEntity<String> vendor() {
            return ResponseEntity.ok("{}");
        }
    }
}
