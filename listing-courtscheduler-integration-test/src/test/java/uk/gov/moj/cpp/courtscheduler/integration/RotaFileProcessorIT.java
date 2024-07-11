package uk.gov.moj.cpp.courtscheduler.integration;

import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

class RotaFileProcessorIT extends AbstractIT {

    private static final String ROTASL_FILE_PROCESSOR_URL = "/rotasl/process-rota-files";

    @Test
    void shouldProcessRotaFiles() {
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, null);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

}
