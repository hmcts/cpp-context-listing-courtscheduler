package uk.gov.moj.cpp.courtscheduler.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Proves the legacy RAML JSON Schemas are now enforced on inbound request bodies
 * (required fields, additionalProperties:false, type, uuid pattern), that valid bodies pass,
 * that GETs / unmapped media types are skipped, and that a passed body stays re-readable.
 *
 * Schemas are loaded from the classpath (packaged by processResources into request-schemas/).
 */
class RequestSchemaValidationFilterTest {

    private static final String MIGRATE_CT = "application/vnd.courtscheduler.oucode.migrate+json";
    private static final String ASSIGN_CT = "application/vnd.courtscheduler.assign-judiciary+json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final RequestSchemaValidationFilter filter = new RequestSchemaValidationFilter(mapper);

    private RecordingChain chain;

    private MockHttpServletResponse run(final String method, final String contentType, final String body)
            throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, "/listingcourtscheduler-api/x");
        if (contentType != null) {
            request.setContentType(contentType);
        }
        if (body != null) {
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
        }
        final MockHttpServletResponse response = new MockHttpServletResponse();
        chain = new RecordingChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    // ---- valid bodies pass ----

    @Test
    void validBody_passesThroughAndStaysReadable() throws Exception {
        final String body = "{\"ouCodes\":[\"B01OU00\"],\"migrated\":true}";
        final MockHttpServletResponse response = run("POST", MIGRATE_CT, body);

        assertEquals(200, response.getStatus());
        assertTrue(chain.proceeded, "valid body must reach the controller");
        final String downstream = new String(
                ((HttpServletRequest) chain.captured).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(body, downstream, "body must remain re-readable downstream");
    }

    @Test
    void validAssignJudiciary_passes() throws Exception {
        final String body = "{\"judiciaries\":[{\"judiciaryId\":\"j-1\","
                + "\"sessionIds\":[\"f2ea88af-5cd9-339c-8e2c-405df1f55ea6\"]}]}";
        final MockHttpServletResponse response = run("POST", ASSIGN_CT, body);
        assertEquals(200, response.getStatus());
        assertTrue(chain.proceeded);
    }

    // ---- strictness is enforced ----

    @Test
    void missingRequiredField_returns400() throws Exception {
        final MockHttpServletResponse response = run("POST", MIGRATE_CT, "{\"ouCodes\":[\"B01OU00\"]}");
        assertEquals(400, response.getStatus());
        assertFalse(chain.proceeded);
        assertTrue(response.getContentAsString().contains("\"error\""));
    }

    @Test
    void wrongType_returns400() throws Exception {
        final MockHttpServletResponse response = run("POST", MIGRATE_CT,
                "{\"ouCodes\":\"not-an-array\",\"migrated\":true}");
        assertEquals(400, response.getStatus());
        assertFalse(chain.proceeded);
    }

    @Test
    void additionalPropertiesFalse_rejectsUnknownField() throws Exception {
        final String body = "{\"judiciaries\":[{\"judiciaryId\":\"j-1\","
                + "\"sessionIds\":[\"f2ea88af-5cd9-339c-8e2c-405df1f55ea6\"]}],\"bogusField\":true}";
        final MockHttpServletResponse response = run("POST", ASSIGN_CT, body);
        assertEquals(400, response.getStatus());
        assertFalse(chain.proceeded);
    }

    @Test
    void badUuidPattern_returns400() throws Exception {
        final String body = "{\"judiciaries\":[{\"judiciaryId\":\"j-1\",\"sessionIds\":[\"not-a-uuid\"]}]}";
        final MockHttpServletResponse response = run("POST", ASSIGN_CT, body);
        assertEquals(400, response.getStatus());
        assertFalse(chain.proceeded);
    }

    @Test
    void invalidJson_returns400() throws Exception {
        final MockHttpServletResponse response = run("POST", MIGRATE_CT, "{not json");
        assertEquals(400, response.getStatus());
        assertFalse(chain.proceeded);
    }

    // ---- pass-through cases (unchanged behaviour) ----

    @Test
    void getRequest_isNotValidated() {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setContentType(MIGRATE_CT);
        assertTrue(filter.shouldNotFilter(request), "GET must be skipped (never body-validated, like WildFly)");
    }

    @Test
    void unmappedMediaType_passesThrough() throws Exception {
        final MockHttpServletResponse response = run("POST", "application/json", "{\"anything\":1}");
        assertEquals(200, response.getStatus());
        assertTrue(chain.proceeded, "no matching schema -> pass through unvalidated");
    }

    /** Records whether doFilter ran and the (wrapped) request passed downstream. */
    private static final class RecordingChain extends MockFilterChain {
        private boolean proceeded;
        private ServletRequest captured;

        @Override
        public void doFilter(final ServletRequest request, final ServletResponse response) {
            this.proceeded = true;
            this.captured = request;
        }
    }
}
