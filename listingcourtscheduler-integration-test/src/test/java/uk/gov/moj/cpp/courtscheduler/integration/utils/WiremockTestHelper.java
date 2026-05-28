package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static java.util.concurrent.TimeUnit.SECONDS;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;

import jakarta.ws.rs.core.Response.Status;

/**
 * Re-platformed in place: was a Justice Services {@code RestPoller}/{@code ResponseStatusMatcher}
 * helper waiting for stubs on {@code localhost:8080}. Now uses the shim {@link RestPoller}
 * targeting the dockerised app via {@code app.baseUrl}.
 */
public class WiremockTestHelper {

    public static final int TIMEOUT = 90;

    private static final String APP_BASE_URL = System.getProperty(
            "app.baseUrl",
            "http://localhost:8083/listingcourtscheduler-api/rest/courtscheduler");

    public static void waitForStubToBeReady(final String resource, final String mediaType) {
        waitForStubToBeReady(resource, mediaType, Status.OK);
    }

    public static void waitForStubToBeReady(final String resource, final String mediaType, final Status expectedStatus) {
        final RequestParams params = new RequestParams(APP_BASE_URL + resource, mediaType, null);
        final ResponseData responseData = poll(params).timeout(TIMEOUT, SECONDS).until();
        if (responseData.getStatus().getStatusCode() != expectedStatus.getStatusCode()) {
            throw new IllegalStateException("Stub at " + resource + " not ready, got status "
                    + responseData.getStatus().getStatusCode());
        }
    }
}
