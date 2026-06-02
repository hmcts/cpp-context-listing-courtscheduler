package uk.gov.moj.cpp.courtscheduler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end proof that inbound request bodies are validated against the legacy RAML JSON
 * Schemas in the real dockerised application — i.e. {@code RequestSchemaValidationFilter} is
 * wired into the running app's filter chain and rejects schema-invalid payloads with 400
 * before the controller, exactly as the WildFly framework did.
 *
 * <p>Runs through the full stack (Istio-less): HTTP → authz filter → schema-validation filter →
 * controller. Uses {@link #COURT_SCHEDULE_USER_ID}, which is authorised for the
 * {@code /courtschedule*} endpoints (see CourtScheduleApiContractIntegrationTest), so requests
 * pass authentication and the schema filter is what rejects them.
 *
 * <p>Strict schemas exercised:
 * <ul>
 *   <li>{@code courtscheduler.create.json} — 12 required fields.</li>
 *   <li>{@code courtscheduler.update.json} — {@code additionalProperties:false} + required + enums.</li>
 * </ul>
 */
class RequestSchemaValidationEndToEndIT extends AbstractIntegrationTest {

    private static final String CREATE_CT = "application/vnd.courtscheduler.create+json";
    private static final String UPDATE_CT = "application/vnd.courtscheduler.update+json";

    @Test
    void postCreate_emptyBody_rejectedBySchemaWith400() {
        final ResponseEntity<String> response = post("/courtschedule", COURT_SCHEDULE_USER_ID, CREATE_CT, "{}");

        assertThat(response.getStatusCode().value())
                .as("empty body must be rejected by the schema (12 required fields), got %s / %s",
                        response.getStatusCode(), response.getBody())
                .isEqualTo(400);
        assertThat(response.getBody()).contains("error");
    }

    @Test
    void postCreate_wrongType_rejectedBySchemaWith400() {
        final ResponseEntity<String> response = post("/courtschedule", COURT_SCHEDULE_USER_ID, CREATE_CT,
                "{\"sessionList\":\"not-an-array\"}");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
    }

    @Test
    void postEdit_unknownField_rejectedByAdditionalPropertiesFalse() {
        // update.json has additionalProperties:false at root -> an unknown field is a schema violation.
        final ResponseEntity<String> response = post("/courtschedule/edit", COURT_SCHEDULE_USER_ID, UPDATE_CT,
                "{\"definitelyNotAKnownField\":true}");

        assertThat(response.getStatusCode().value())
                .as("unknown field must be rejected by additionalProperties:false, got %s / %s",
                        response.getStatusCode(), response.getBody())
                .isEqualTo(400);
        assertThat(response.getBody()).contains("error");
    }
}
