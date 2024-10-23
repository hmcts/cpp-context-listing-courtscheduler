package uk.gov.moj.cpp.courtscheduler.common;

import uk.gov.justice.services.common.configuration.GlobalValue;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class StorageApplicationParameters {

    @Inject
    @GlobalValue(key = "azure.local.mi.clientId", defaultValue = "a1a4f56c-a99b-4cc0-aaae-edd355daf67b")
    private String azureLocalMiClientId;

    @Inject
    @GlobalValue(key = "azure.local.mi.tenantId", defaultValue = "e2995d11-9947-4e78-9de6-d44e0603518e")
    private String azureLocalMiTenantId;

    public String getAzureLocalMiClientId() {
        return azureLocalMiClientId;
    }

    public String getAzureLocalMiTenantId() {
        return azureLocalMiTenantId;
    }
}
