package uk.gov.moj.cpp.courtscheduler.api;

import uk.gov.justice.services.core.requester.Requester;

import javax.inject.Inject;

public class RequestFactory {
    @Inject
    private Requester requester;

    public  Requester getRequester() {
        return requester;
    }
}
