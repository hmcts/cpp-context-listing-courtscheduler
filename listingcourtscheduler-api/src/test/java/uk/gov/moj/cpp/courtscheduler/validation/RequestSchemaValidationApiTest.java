package uk.gov.moj.cpp.courtscheduler.validation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Full-pipeline API test: drives requests through {@link RequestSchemaValidationFilter} in a
 * real MockMvc servlet filter chain to prove inbound bodies are validated against the legacy
 * RAML JSON Schemas at the API boundary — invalid bodies are rejected with 400 BEFORE the
 * controller is reached, valid bodies pass through to the controller, and GET / non-vendor
 * media types are not validated (matching WildFly).
 *
 * <p>A lightweight stub controller stands in for the real controllers so the test isolates the
 * validation layer (the filter behaves identically regardless of the downstream controller).
 */
class RequestSchemaValidationApiTest {

    private static final String MIGRATE_CT = "application/vnd.courtscheduler.oucode.migrate+json";
    private static final String ASSIGN_CT = "application/vnd.courtscheduler.assign-judiciary+json";

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        final ObjectMapper mapper = new ObjectMapper();
        mvc = MockMvcBuilders.standaloneSetup(new StubEndpoints())
                .addFilters(new RequestSchemaValidationFilter(mapper))
                .build();
    }

    // ---------- invalid bodies rejected at the API boundary (400, controller not reached) ----------

    @Test
    void postMigrate_missingRequiredField_isRejectedWith400() throws Exception {
        mvc.perform(post("/oucode/migrate").contentType(MIGRATE_CT)
                        .content("{\"ouCodes\":[\"B01OU00\"]}"))            // missing "migrated"
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void postMigrate_wrongType_isRejectedWith400() throws Exception {
        mvc.perform(post("/oucode/migrate").contentType(MIGRATE_CT)
                        .content("{\"ouCodes\":\"not-an-array\",\"migrated\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postSession_unknownField_rejectedByAdditionalPropertiesFalse() throws Exception {
        final String body = "{\"judiciaries\":[{\"judiciaryId\":\"j-1\","
                + "\"sessionIds\":[\"f2ea88af-5cd9-339c-8e2c-405df1f55ea6\"]}],\"bogusField\":true}";
        mvc.perform(post("/session").contentType(ASSIGN_CT).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postSession_badUuidPattern_isRejectedWith400() throws Exception {
        final String body = "{\"judiciaries\":[{\"judiciaryId\":\"j-1\",\"sessionIds\":[\"not-a-uuid\"]}]}";
        mvc.perform(post("/session").contentType(ASSIGN_CT).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postMigrate_invalidJson_isRejectedWith400() throws Exception {
        mvc.perform(post("/oucode/migrate").contentType(MIGRATE_CT).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    // ---------- valid bodies reach the controller ----------

    @Test
    void postMigrate_validBody_reachesController() throws Exception {
        mvc.perform(post("/oucode/migrate").contentType(MIGRATE_CT)
                        .content("{\"ouCodes\":[\"B01OU00\"],\"migrated\":true}"))
                .andExpect(status().isOk())
                .andExpect(content().string("reached:migrate"));
    }

    @Test
    void postSession_validBody_reachesController() throws Exception {
        final String body = "{\"judiciaries\":[{\"judiciaryId\":\"j-1\","
                + "\"sessionIds\":[\"f2ea88af-5cd9-339c-8e2c-405df1f55ea6\"]}]}";
        mvc.perform(post("/session").contentType(ASSIGN_CT).content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("reached:session"));
    }

    // ---------- not validated (matches WildFly) ----------

    @Test
    void nonVendorMediaType_isNotValidated() throws Exception {
        // application/json has no matching schema -> passes through unvalidated
        mvc.perform(post("/passthrough").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anything\":123}"))
                .andExpect(status().isOk())
                .andExpect(content().string("reached:passthrough"));
    }

    @Test
    void getRequest_isNotValidated() throws Exception {
        mvc.perform(get("/oucode/migrate").contentType(MIGRATE_CT))
                .andExpect(status().isOk())
                .andExpect(content().string("reached:get"));
    }

    /** Minimal controller standing in for the real endpoints; only reached when validation passes. */
    @RestController
    static class StubEndpoints {

        @PostMapping(path = "/oucode/migrate", consumes = MIGRATE_CT)
        ResponseEntity<String> migrate(@RequestBody final String body) {
            return ResponseEntity.ok("reached:migrate");
        }

        @PostMapping(path = "/session", consumes = ASSIGN_CT)
        ResponseEntity<String> session(@RequestBody final String body) {
            return ResponseEntity.ok("reached:session");
        }

        @PostMapping(path = "/passthrough", consumes = MediaType.APPLICATION_JSON_VALUE)
        ResponseEntity<String> passthrough(@RequestBody final String body) {
            return ResponseEntity.ok("reached:passthrough");
        }

        @org.springframework.web.bind.annotation.GetMapping("/oucode/migrate")
        ResponseEntity<String> get() {
            return ResponseEntity.ok("reached:get");
        }
    }
}
