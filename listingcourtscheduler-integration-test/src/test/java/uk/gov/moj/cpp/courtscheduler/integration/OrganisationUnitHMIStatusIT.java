package uk.gov.moj.cpp.courtscheduler.integration;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.equalTo;
import static uk.gov.justice.services.test.utils.core.http.BaseUriProvider.getBaseUri;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.justice.services.test.utils.core.matchers.ResponsePayloadMatcher.payload;
import static uk.gov.justice.services.test.utils.core.matchers.ResponseStatusMatcher.status;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.OrganisationUnitHMIStatusServiceStub.stubGetAllOrganisationUnitHMIStatus;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.OrganisationUnitHMIStatusServiceStub.stubGetOrganisationUnitHMIStatusByOucode;

import uk.gov.justice.services.test.utils.core.http.RequestParams;

import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class OrganisationUnitHMIStatusIT extends AbstractIT {

    private static final String OUCODE1 = "A12AH00";
    private static final String OUCODE2 = "A46AF00";
    private static final String GET_ALL_SERVICE_QUERY_API_PATH = "/listingcourtscheduler-api/rest/courtscheduler/organisation-units/hmi-status";
    private static final String GET_ALL_SERVICE_MEDIA_TYPE = "application/vnd.listingcourtscheduler.query.organisation-units-hmi-status+json";
    private static final String GET_BY_OUCODE_SERVICE_QUERY_API_PATH = "/listingcourtscheduler-api/rest/courtscheduler/organisation-units/hmi-status/";
    private static final String GET_BY_OUCODE_SERVICE_MEDIA_TYPE = "application/vnd.listingcourtscheduler.query.organisation-units-hmi-status+json";
    public static final String USER_ID = "CJSCPPUID";

    @Test
    public void shouldGetAllOrganisationUnitHMIStatus() {
        stubGetAllOrganisationUnitHMIStatus();
        final String url = format("%s%s", getBaseUri(), GET_ALL_SERVICE_QUERY_API_PATH);
        final RequestParams requestParams = requestParams(url, GET_ALL_SERVICE_MEDIA_TYPE)
                .withHeader(USER_ID, randomUUID())
                .build();

        poll(requestParams).until(status().is(OK),
                payload().isJson(
                        allOf(
                                withJsonPath("$.organisationUnitHMIStatus.size()", equalTo(5)),
                                withJsonPath("$.organisationUnitHMIStatus[0].oucode", is(OUCODE1)),
                                withJsonPath("$.organisationUnitHMIStatus[0].isHMIListingEnabled", is(true)),
                                withJsonPath("$.organisationUnitHMIStatus[0].isHMISchedulingEnabled", is(true)),
                                withJsonPath("$.organisationUnitHMIStatus[0].isHMIPubHubEnabled", is(true)),
                                withJsonPath("$.organisationUnitHMIStatus[0].updatedOn", is("2021-08-09T10:48:48.500Z")),
                                withJsonPath("$.organisationUnitHMIStatus[0].courtCentreId", is("9689207b-a9d2-4c2e-bd38-269b78a132a8")),
                                withJsonPath("$.organisationUnitHMIStatus[0].courtId", is("392")),
                                withJsonPath("$.organisationUnitHMIStatus[1].oucode", is(OUCODE2)),
                                withJsonPath("$.organisationUnitHMIStatus[1].isHMIListingEnabled", is(false)),
                                withJsonPath("$.organisationUnitHMIStatus[1].isHMISchedulingEnabled", is(false)),
                                withJsonPath("$.organisationUnitHMIStatus[1].isHMIPubHubEnabled", is(false)),
                                withJsonPath("$.organisationUnitHMIStatus[1].updatedOn", nullValue()),
                                withJsonPath("$.organisationUnitHMIStatus[1].courtCentreId", is("d74876ff-2bd9-435a-87e3-b8047cb1351a"))
                        ))
        );
    }

    @Test
    public void shouldGetOrganisationUnitHMIStatusForGivenOucode() {
        stubGetOrganisationUnitHMIStatusByOucode();
        final String url = format("%s%s%s", getBaseUri(), GET_BY_OUCODE_SERVICE_QUERY_API_PATH, OUCODE1);
        final RequestParams requestParams = requestParams(url, GET_BY_OUCODE_SERVICE_MEDIA_TYPE)
                .withHeader(USER_ID, randomUUID())
                .build();

        poll(requestParams).until(status().is(OK),
                payload().isJson(
                        allOf(
                                withJsonPath("$.oucode", is(OUCODE1)),
                                withJsonPath("$.isHMIListingEnabled", is(true)),
                                withJsonPath("$.isHMISchedulingEnabled", is(true)),
                                withJsonPath("$.isHMIPubHubEnabled", is(true)),
                                withJsonPath("$.updatedOn", is("2021-08-09T10:48:48.500Z")),
                                withJsonPath("$.courtCentreId", is("9689207b-a9d2-4c2e-bd38-269b78a132a8"))
                        ))
        );
    }
}
