package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;

public class OrganisationUnitHMIStatusServiceStub {

    private static final String ROTA_SL_ENDPOINT_URL = "/fa-ste-ccm-scsl";
    private static final String QUERY_API_ENDPOINT_URL = "/organisationUnitHMIStatus";
    private static final String HOST = System.getProperty("INTEGRATION_HOST_KEY", "localhost");
    private static final String OUCODE = "A12AH00";

    static {
        configureFor(HOST, 8080);
    }

    public static void stubGetAllOrganisationUnitHMIStatus() {
        stubFor(get(urlPathMatching(format("%s", ROTA_SL_ENDPOINT_URL + QUERY_API_ENDPOINT_URL)))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(getPayload("stub-data/rotaSl/azure.rotasl.getOrganisationUnitsHMIStatus.stub-data.json"))));
    }

    public static void stubGetOrganisationUnitHMIStatusByOucode() {
        stubFor(get(urlPathMatching(format("%s/%s", ROTA_SL_ENDPOINT_URL + QUERY_API_ENDPOINT_URL, OUCODE)))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .withBody(getPayload("stub-data/rotaSl/azure.rotasl.getOrganisationUnitHMIStatus.stub-data.json"))));
    }
}
