package uk.gov.moj.cpp.courtscheduler.integration;

import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupUserAsSystemUser;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceCourtRooms;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataCourtRoomSessionAllocations;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciaries;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataRotaBusinessTypes;

import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RotaFileProcessorIT extends AbstractIT {

    private static final String ROTASL_FILE_PROCESSOR_URL = "/rotasl/process-rota-files";

    @BeforeAll
    static void setupSystemUser() {
        setupUserAsSystemUser(USER_ID.toString());
    }

    @Test
    void shouldProcessRotaFiles() {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-duration-based.json");
        stubGetReferenceCourtRooms("referencedata.rota-courtrooms.json");
        stubGetReferenceDataCourtRoomSessionAllocations("referencedata.rota-courtroom-sessionallocations.json");
        stubGetReferenceDataJudiciaries("referencedata.judiciaries.json");
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, null);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

}
