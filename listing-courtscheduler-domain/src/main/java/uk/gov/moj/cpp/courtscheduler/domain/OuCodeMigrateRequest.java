package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class OuCodeMigrateRequest {

    private List<String> ouCodes;
    private String migrated;

    public List<String> getOuCodes() {
        return ouCodes;
    }

    public void setOuCodes(final List<String> ouCodes) {
        this.ouCodes = ouCodes;
    }

    public String getMigrated() {
        return migrated;
    }

    public void setMigrated(final String migrated) {
        this.migrated = migrated;
    }
}
