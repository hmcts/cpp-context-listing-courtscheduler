package uk.gov.moj.cpp.courtscheduler.api;

import uk.gov.justice.services.core.accesscontrol.LocalAccessControlInterceptor;
import uk.gov.justice.services.core.audit.LocalAuditInterceptor;
import uk.gov.justice.services.core.interceptor.InterceptorChainEntry;
import uk.gov.justice.services.core.interceptor.InterceptorChainEntryProvider;
import uk.gov.justice.services.metrics.interceptor.IndividualActionMetricsInterceptor;
import uk.gov.justice.services.metrics.interceptor.TotalActionMetricsInterceptor;

import java.util.LinkedList;
import java.util.List;

public class CourtSchedulerApiInterceptorChainProvider implements InterceptorChainEntryProvider {

    final List<InterceptorChainEntry> interceptorChainEntries = new LinkedList<>();

    public CourtSchedulerApiInterceptorChainProvider() {
        interceptorChainEntries.add(new InterceptorChainEntry(1, TotalActionMetricsInterceptor.class));
        interceptorChainEntries.add(new InterceptorChainEntry(2, IndividualActionMetricsInterceptor.class));
        interceptorChainEntries.add(new InterceptorChainEntry(3000, LocalAuditInterceptor.class));
        interceptorChainEntries.add(new InterceptorChainEntry(4000, LocalAccessControlInterceptor.class));
    }

    @Override
    public String component() {
        return "Courtscheduler.API";
    }

    public List<InterceptorChainEntry> interceptorChainTypes() {
        return interceptorChainEntries;
    }
}