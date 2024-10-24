package uk.gov.moj.cpp.courtscheduler.common;

import uk.gov.justice.services.common.configuration.GlobalValue;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class StorageApplicationParameters {

    @Inject
    @GlobalValue(key = "azure.local.mi.clientId", defaultValue = "")
    private String azureLocalMiClientId;

    @Inject
    @GlobalValue(key = "azure.local.mi.tenantId", defaultValue = "")
    private String azureLocalMiTenantId;

    public String getAzureLocalMiClientId() {
        return azureLocalMiClientId;
    }

    public String getAzureLocalMiTenantId() {
        return azureLocalMiTenantId;
    }
}
