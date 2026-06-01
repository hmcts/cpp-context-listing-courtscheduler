package uk.gov.moj.cpp.courtscheduler.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Migrated from Justice Services {@code @GlobalValue(key=..., defaultValue=...)}
 * to Spring's {@code @Value("${...:default}")}.
 */
@Service
public class StorageApplicationParameters {

    @Value("${azure.local.mi.clientId:}") // gitleaks:allow
    private String azureLocalMiClientId;

    @Value("${azure.local.mi.tenantId:}") // gitleaks:allow
    private String azureLocalMiTenantId;

    public String getAzureLocalMiClientId() {
        return azureLocalMiClientId;
    }

    public String getAzureLocalMiTenantId() {
        return azureLocalMiTenantId;
    }
}
